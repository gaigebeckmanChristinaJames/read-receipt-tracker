package dev.ujhhgtg.wekit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dev.ujhhgtg.wekit.R
import dev.ujhhgtg.wekit.activity.ReadReceiptActivity
import dev.ujhhgtg.wekit.readreceipts.Database
import dev.ujhhgtg.wekit.readreceipts.GeoLookup
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/**
 * 已读追踪服务：内嵌 HTTP 服务器 + SQLite + cloudflared 隧道（含 DNS 修复）。
 *
 * 关键修复点（相对旧版）：
 * 1. cloudflared 以 jniLibs 形式打包为 libcloudflared.so，Android 会将其解压到
 *    nativeLibraryDir（可执行目录），解决 /data/data/... noexec 导致 Permission denied。
 * 2. 写入自定义 resolv.conf（公共 DNS），绕过被污染的 [::1]:53。
 * 3. HTTP 服务器使用线程池；从 X-Forwarded-For 提取真实 IP 并做 GeoLookup。
 * 4. 隧道 URL 用 trycloudflare.com 正则从日志文件轮询提取。
 * 5. 1x1 GIF 使用完整合法字节。
 */
class ReadReceiptService : Service() {

    companion object {
        const val CHANNEL_ID = "rrt_service"
        const val NOTIFICATION_ID = 1
        const val PORT = 5000

        const val ACTION_START = "dev.ujhhgtg.wekit.READ_RECEIPT_START"
        const val ACTION_STOP = "dev.ujhhgtg.wekit.READ_RECEIPT_STOP"
        const val ACTION_STATUS = "dev.ujhhgtg.wekit.READ_RECEIPT_STATUS"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_TUNNEL_URL = "tunnel_url"
        const val EXTRA_MODE = "mode"
        const val PREFS_NAME = "rrt_prefs"
        const val PREF_MODE = "tunnel_mode"
        const val MODE_LOCAL = "local"
        const val MODE_TUNNEL = "tunnel"

        @Volatile var tunnelUrl: String? = null
            private set

        private val TRANSPARENT_GIF = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
            0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x21,
            0xF9.toByte(), 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00,
            0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44,
            0x01, 0x00, 0x3B
        )

        private val TUNNEL_PATTERN =
            Pattern.compile("https://[a-z0-9][a-z0-9-]*\\.trycloudflare\\.com")
    }

    private val running = AtomicBoolean(false)
    private val tunnelRunning = AtomicBoolean(false)
    private var database: Database? = null
    private var serverSocket: ServerSocket? = null
    private var httpPool: java.util.concurrent.ExecutorService? = null
    private var tunnelProcess: Process? = null
    private lateinit var logFile: File

    override fun onCreate() {
        super.onCreate()
        logFile = File(filesDir, "cloudflared.log")
        createNotificationChannel()
        runCatching {
            val n = buildNotification("服务启动中...")
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 允许通过 Intent 切换模式
        intent?.getStringExtra(EXTRA_MODE)?.let { mode ->
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREF_MODE, mode).apply()
            if (running.get() && mode == currentMode()) {
                // 模式未变，仅刷新状态
                broadcastStatus(running.get(), tunnelUrl)
                return START_STICKY
            }
            if (running.get()) {
                // 模式变了，重启隧道部分
                stopAll()
                Thread { startAll() }.start()
                return START_STICKY
            }
        }
        when (intent?.action) {
            ACTION_STOP -> { stopAll(); stopSelf() }
            ACTION_STATUS -> broadcastStatus(running.get(), tunnelUrl)
            else -> startAll()
        }
        return START_STICKY
    }

    private fun currentMode(): String =
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_MODE, MODE_TUNNEL) ?: MODE_TUNNEL

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    // ── 启停 ──────────────────────────────────────────────────────────────
    private fun startAll() {
        if (running.get()) {
            broadcastStatus(true, tunnelUrl)
            return
        }
        try {
            running.set(true)
            tunnelUrl = null
            database = Database(this)
            httpPool = Executors.newFixedThreadPool(16)

            Thread(this::runHttpServer, "rrt-http").apply { isDaemon = true }.start()

            val mode = currentMode()
            if (mode == MODE_LOCAL) {
                // 本地模式：不启动 cloudflared，直接使用 127.0.0.1
                val localUrl = "http://127.0.0.1:$PORT"
                tunnelUrl = localUrl
                writeLog("本地模式就绪: $localUrl")
                broadcastStatus(true, localUrl)
                updateNotification("本地服务运行中 · $localUrl")
            } else {
                broadcastStatus(true, null)
                updateNotification("服务启动中，正在建立隧道...")
                Thread({
                    runCatching { Thread.sleep(2500) }
                    startTunnel()
                }, "rrt-tunnel-start").start()
            }
        } catch (e: Exception) {
            running.set(false)
            writeLog("启动失败: ${e.message}")
        }
    }

    private fun stopAll() {
        running.set(false)
        tunnelRunning.set(false)
        tunnelUrl = null
        runCatching { serverSocket?.close() }
        runCatching { tunnelProcess?.destroy() }
        runCatching { httpPool?.shutdownNow() }
        runCatching { database?.close() }
        database = null
        broadcastStatus(false, null)
    }

    // ── 隧道 ──────────────────────────────────────────────────────────────
    private fun startTunnel() {
        if (!running.get()) return
        tunnelRunning.set(true)
        writeResolvConf()
        startBuiltInTunnel()
    }

    /** 写入自定义 resolv.conf 到 cloudflared 读取的路径（二进制已 patch）。 */
    private fun writeResolvConf() {
        try {
            val dir = File(filesDir, "xx")
            if (!dir.exists()) dir.mkdirs()
            File(dir, "resolv.conf").writeText(
                "nameserver 223.5.5.5\nnameserver 1.1.1.1\nnameserver 8.8.8.8\n"
            )
            writeLog("resolv.conf 已写入: ${dir.absolutePath}/resolv.conf")
        } catch (e: Exception) {
            writeLog("resolv.conf 写入失败: ${e.message}")
        }
    }

    /** 定位 jniLibs 中的 libcloudflared.so（Android 解压到可执行目录）。 */
    private fun locateCloudflared(): File? {
        // 1) 优先使用 applicationInfo.nativeLibraryDir（最可靠，该目录下的 .so 有执行权限）
        runCatching {
            val dir = applicationInfo.nativeLibraryDir
            if (dir != null) {
                val so = File(dir, "libcloudflared.so")
                if (so.exists() && so.length() > 0L) {
                    so.setExecutable(true)
                    return so
                }
            }
        }

        // 2) 回退：VMRuntime 反射拿到 nativeLibraryDir
        try {
            val field = Class.forName("dalvik.system.VMRuntime")
                .getDeclaredField("nativeLibraryDirectories")
            field.isAccessible = true
            val dirs = field.get(null) as? Array<*>
            if (dirs != null && dirs.isNotEmpty()) {
                val so = File(dirs[0].toString(), "libcloudflared.so")
                if (so.exists()) return so
            }
        } catch (_: Exception) {}

        // 2) 回退：从 assets 释放到 filesDir（nativeLibraryDir 是只读的，不能写入）
        try {
            val dir = File(filesDir, "bin")
            if (!dir.exists()) dir.mkdirs()
            val exe = File(dir, "cloudflared")
            if (!exe.exists() || exe.length() == 0L) {
                assets.open("cloudflared").use { input ->
                    FileOutputStream(exe).use { output -> input.copyTo(output) }
                }
            }
            exe.setExecutable(true)
            return exe
        } catch (e: Exception) {
            writeLog("cloudflared 定位失败: ${e.message}")
        }
        return null
    }

    private fun startBuiltInTunnel() {
        if (!running.get()) return
        val cloudflared = locateCloudflared()
        if (cloudflared == null) {
            writeLog("未找到 libcloudflared.so（仅支持 arm64-v8a）")
            tunnelRunning.set(false)
            return
        }
        cloudflared.setExecutable(true)
        writeLog("cloudflared 就绪: ${cloudflared.absolutePath} (${cloudflared.length()} 字节)")

        val cmd = listOf(
            cloudflared.absolutePath,
            "tunnel", "--url", "http://127.0.0.1:$PORT"
        )
        writeLog("启动隧道: ${cmd.joinToString(" ")}")

        try {
            val pb = ProcessBuilder(cmd)
            pb.directory(filesDir)
            pb.redirectErrorStream(true)
            pb.redirectOutput(logFile)
            tunnelProcess = pb.start()
        } catch (e: Exception) {
            writeLog("隧道进程启动失败: ${e.message}")
            tunnelRunning.set(false)
            return
        }

        // 轮询日志提取隧道 URL
        Thread({
            while (tunnelRunning.get() && running.get()) {
                try {
                    if (logFile.exists() && logFile.length() > 0) {
                        val text = logFile.readText()
                        val m = TUNNEL_PATTERN.matcher(text)
                        if (m.find()) {
                            val url = m.group()
                            if (url != tunnelUrl) {
                                tunnelUrl = url
                                writeLog("隧道地址: $url")
                                broadcastStatus(true, url)
                                updateNotification("隧道已就绪 · $url")
                            }
                        }
                    }
                } catch (_: Exception) {}
                runCatching { Thread.sleep(2000) }
            }
        }, "rrt-tunnel-monitor").start()
    }

    // ── HTTP 服务器 ───────────────────────────────────────────────────────
    private fun runHttpServer() {
        try {
            serverSocket = ServerSocket(PORT)
            writeLog("HTTP 服务器监听 127.0.0.1:$PORT")
        } catch (e: Exception) {
            writeLog("HTTP 服务器启动失败: ${e.message}")
            broadcastStatus(true, tunnelUrl)
            return
        }
        broadcastStatus(true, tunnelUrl)
        while (running.get()) {
            try {
                val socket = serverSocket!!.accept()
                httpPool?.submit { handleRequest(socket) }
            } catch (e: Exception) {
                if (!running.get()) break
            }
        }
        runCatching { serverSocket?.close() }
    }

    private fun handleRequest(socket: Socket) {
        try {
            socket.use { s ->
                s.soTimeout = 30000
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val fullPath = parts[1]

                val headers = HashMap<String, String>()
                var line = reader.readLine()
                while (line != null && line.isNotEmpty()) {
                    val idx = line.indexOf(":")
                    if (idx > 0) {
                        headers[line.substring(0, idx).trim().lowercase()] =
                            line.substring(idx + 1).trim()
                    }
                    line = reader.readLine()
                }

                val cl = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (cl > 0) {
                    val buf = CharArray(cl)
                    var read = 0
                    while (read < cl) {
                        val n = reader.read(buf, read, cl - read)
                        if (n < 0) break
                        read += n
                    }
                    String(buf, 0, read)
                } else ""

                var path = fullPath
                var query = ""
                val qIdx = fullPath.indexOf("?")
                if (qIdx >= 0) {
                    path = fullPath.substring(0, qIdx)
                    query = fullPath.substring(qIdx + 1)
                }

                // 真实 IP：cloudflared 会在 X-Forwarded-For 中传递
                var clientIp = headers["x-forwarded-for"] ?: ""
                if (clientIp.contains(",")) clientIp = clientIp.split(",")[0].trim()
                if (clientIp.isEmpty()) clientIp = headers["x-real-ip"] ?: ""
                if (clientIp.isEmpty()) {
                    clientIp = runCatching {
                        val addr = s.remoteSocketAddress.toString()
                        val colon = addr.lastIndexOf(":")
                        if (colon > 0) addr.substring(1, colon) else addr
                    }.getOrDefault("0.0.0.0")
                }

                val response = route(method, path, query, body, clientIp,
                    headers["user-agent"] ?: "")
                s.getOutputStream().write(response)
                s.getOutputStream().flush()
            }
        } catch (_: Exception) {}
    }

    private fun route(method: String, path: String, query: String, body: String,
                      ip: String, ua: String): ByteArray {
        val db = database ?: return json("{\"error\":\"db not ready\"}", 500)

        when {
            path == "/health" ->
                return json("{\"status\":\"ok\",\"service\":\"read-receipt-tracker\"}", 200)

            path == "/pixel" || path == "/pixel.gif" -> {
                val wxId = qParam(query, "wxId")
                val msgId = qParam(query, "id")
                var reader = qParam(query, "readerWxId")
                if (reader.isEmpty()) reader = qParam(query, "reader")
                if (wxId.isNotEmpty() && msgId.isNotEmpty()) {
                    val readerFinal = if (path == "/pixel.gif") "未知访客" else reader
                    val geo = runCatching { GeoLookup.lookup(ip) }.getOrNull()
                    db.recordRead(
                        msgId, wxId, ip, ua,
                        geo?.country ?: "", geo?.region ?: "", geo?.city ?: "",
                        geo?.isp ?: "", geo?.loc ?: "",
                        if (readerFinal.isEmpty()) wxId else readerFinal
                    )
                }
                return gif()
            }

            path == "/register" && method == "POST" -> {
                val data = parseJson(body)
                val wxId = data["wxId"] ?: ""
                val content = data["content"] ?: ""
                var createTime = System.currentTimeMillis()
                data["createTime"]?.let { ct ->
                    runCatching { createTime = ct.toLong() }
                }
                if (wxId.isEmpty()) return json("{\"error\":\"wxId required\"}", 400)
                val msgId = sha256("$wxId\u0000$content\u0000$createTime")
                db.registerMessage(msgId, wxId, content, createTime)
                return json(
                    "{\"success\":true,\"id\":\"$msgId\",\"wxId\":\"${escJson(wxId)}\"," +
                    "\"pixel_url\":\"http://127.0.0.1:$PORT/pixel?wxId=${urlEnc(wxId)}&id=$msgId\"}",
                    200
                )
            }

            path == "/count" -> {
                val wxId = qParam(query, "wxId")
                val msgId = qParam(query, "id")
                if (wxId.isEmpty() || msgId.isEmpty())
                    return json("{\"count\":0,\"error\":\"wxId and id required\"}", 200)
                return json("{\"count\":${db.readCount(msgId, wxId)},\"msg_id\":\"$msgId\"}", 200)
            }

            path == "/api/messages" -> {
                val sb = StringBuilder()
                db.messageList(100).use { c ->
                    while (c.moveToNext()) {
                        if (sb.isNotEmpty()) sb.append(",")
                        sb.append("{\"id\":\"").append(c.getString(c.getColumnIndexOrThrow("id")))
                          .append("\",\"wxId\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("wx_id"))))
                          .append("\",\"content\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("content"))))
                          .append("\",\"read_count\":").append(c.getInt(c.getColumnIndexOrThrow("cnt")))
                          .append(",\"registered_at\":").append(c.getLong(c.getColumnIndexOrThrow("registered_at")))
                          .append("}")
                    }
                }
                return json("{\"messages\":[$sb]}", 200)
            }

            path.startsWith("/api/reads/") -> {
                val msgId = path.removePrefix("/api/reads/")
                val msg = db.getMessage(msgId)
                    ?: return json("{\"error\":\"not found\"}", 404)
                val sb = StringBuilder()
                db.readList(msgId).use { c ->
                    while (c.moveToNext()) {
                        if (sb.isNotEmpty()) sb.append(",")
                        var reader = c.getString(c.getColumnIndexOrThrow("reader_wx_id"))
                        if (reader.isNullOrEmpty())
                            reader = c.getString(c.getColumnIndexOrThrow("wx_id"))
                        sb.append("{\"ip_address\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("ip_address"))))
                          .append("\",\"location\":\"").append(escJson(joinAddr(
                              c.getString(c.getColumnIndexOrThrow("country")),
                              c.getString(c.getColumnIndexOrThrow("region")),
                              c.getString(c.getColumnIndexOrThrow("city")))))
                          .append("\",\"country\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("country"))))
                          .append("\",\"region\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("region"))))
                          .append("\",\"city\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("city"))))
                          .append("\",\"isp\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("isp"))))
                          .append("\",\"loc\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("loc"))))
                          .append("\",\"reader_wx_id\":\"").append(escJson(reader))
                          .append("\",\"read_at\":").append(c.getLong(c.getColumnIndexOrThrow("read_at")))
                          .append("}")
                    }
                }
                val wx = msg.getString(msg.getColumnIndexOrThrow("wx_id"))
                val content = msg.getString(msg.getColumnIndexOrThrow("content"))
                msg.close()
                return json(
                    "{\"msg_id\":\"$msgId\",\"wxId\":\"${escJson(wx)}\"," +
                    "\"content\":\"${escJson(content)}\",\"reads\":[$sb]}", 200
                )
            }

            path == "/api/delete-all" -> {
                db.deleteAll()
                return json("{\"success\":true}", 200)
            }

            path.startsWith("/api/delete/") -> {
                db.deleteMessage(path.removePrefix("/api/delete/"))
                return json("{\"success\":true}", 200)
            }

            path == "/batch-status" -> {
                val idsStr = qParam(query, "ids")
                if (idsStr.isEmpty()) return json("{\"error\":\"ids required\"}", 400)
                val sb = StringBuilder()
                for (id in idsStr.split(",")) {
                    val t = id.trim()
                    if (t.isEmpty()) continue
                    if (sb.isNotEmpty()) sb.append(",")
                    sb.append("\"").append(escJson(t)).append("\":").append(db.readCount(t, ""))
                }
                return json("{\"statuses\":{$sb}}", 200)
            }

            path == "/" -> return html(dev.ujhhgtg.wekit.readreceipts.ConsoleHtml.index(db), 200)

            path.startsWith("/message/") -> {
                val msgId = path.removePrefix("/message/")
                val msg = db.getMessage(msgId)
                    ?: return html("not found", 404)
                val result = dev.ujhhgtg.wekit.readreceipts.ConsoleHtml.detail(db, msg)
                msg.close()
                return html(result, 200)
            }

            else -> return json("{\"error\":\"not found\"}", 404)
        }
    }

    // ── 响应工具 ──────────────────────────────────────────────────────────
    private fun gif(): ByteArray {
        val h = ("HTTP/1.1 200 OK\r\nContent-Type: image/gif\r\n" +
                "Content-Length: ${TRANSPARENT_GIF.size}\r\n" +
                "Connection: close\r\nCache-Control: no-cache\r\n\r\n").toByteArray()
        return h + TRANSPARENT_GIF
    }

    private fun json(content: String, code: Int): ByteArray {
        val status = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"
            429 -> "Too Many Requests"; else -> "Internal Server Error"
        }
        val body = content.toByteArray()
        val h = ("HTTP/1.1 $code $status\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()
        return h + body
    }

    private fun html(content: String, code: Int): ByteArray {
        val status = if (code == 200) "OK" else "Not Found"
        val body = content.toByteArray()
        val h = ("HTTP/1.1 $code $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()
        return h + body
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────
    private fun qParam(query: String, name: String): String {
        if (query.isEmpty()) return ""
        for (p in query.split("&")) {
            val eq = p.indexOf("=")
            if (eq < 0) continue
            if (p.substring(0, eq) == name) {
                return runCatching {
                    URLDecoder.decode(p.substring(eq + 1), "UTF-8")
                }.getOrDefault("")
            }
        }
        return ""
    }

    private fun joinAddr(country: String?, region: String?, city: String?): String {
        val sb = StringBuilder()
        if (!country.isNullOrEmpty()) sb.append(country).append(" ")
        if (!region.isNullOrEmpty()) sb.append(region).append(" ")
        if (!city.isNullOrEmpty()) sb.append(city)
        return if (sb.isEmpty()) "-" else sb.toString().trim()
    }

    private fun sha256(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { "" }
    }

    private fun parseJson(text: String): Map<String, String> {
        val map = HashMap<String, String>()
        if (text.isEmpty()) return map
        val p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\d+)")
        val m = p.matcher(text)
        while (m.find()) {
            var v = m.group(2)
            if (v.startsWith("\"")) v = v.substring(1, v.length - 1)
            map[m.group(1)] = v
        }
        return map
    }

    private fun escJson(s: String?): String {
        if (s == null) return ""
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun urlEnc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    // ── 通知 / 广播 / 日志 ────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "已读追踪服务", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "已读追踪服务正在运行" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, ReadReceiptActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("已读追踪服务")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        runCatching {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    private fun broadcastStatus(runningVal: Boolean, url: String?) {
        sendBroadcast(Intent(ACTION_STATUS).apply {
            putExtra(EXTRA_RUNNING, runningVal)
            putExtra(EXTRA_TUNNEL_URL, url)
            // 不限制包名，允许微信进程接收状态广播
        })
    }

    private fun writeLog(message: String) {
        try {
            FileOutputStream(logFile, true).bufferedWriter().use { fw ->
                fw.write("${DateFormat.getTimeInstance().format(Date())} [APP] $message\n")
            }
        } catch (_: Exception) {}
    }
}
