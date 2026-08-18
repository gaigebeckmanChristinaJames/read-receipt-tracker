package com.rrt.tracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * read-receipt-tracker Android 服务
 * 内嵌 HTTP 服务器 + SQLite + cloudflared 隧道（含 DNS 修复）
 */
public class TrackerService extends Service {

    private static final String CHANNEL_ID = "rrt_service";
    private static final int NOTIFICATION_ID = 1;
    private static final int PORT = 5000;

    public static final String ACTION_START = "com.rrt.tracker.START";
    public static final String ACTION_STOP = "com.rrt.tracker.STOP";
    public static final String ACTION_STATUS = "com.rrt.tracker.STATUS";
    public static final String EXTRA_RUNNING = "running";
    public static final String EXTRA_TUNNEL_URL = "tunnel_url";

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean tunnelRunning = new AtomicBoolean(false);
    private static volatile String tunnelUrl = null;
    private static volatile Database db = null;

    private ExecutorService httpPool;
    private ServerSocket serverSocket;
    private Process tunnelProcess;
    private Thread serverThread;

    private static final byte[] TRANSPARENT_GIF = new byte[] {
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
        (byte)0x80, 0x00, 0x00, 0x00, 0x00, 0x00,
        (byte)0xFF, (byte)0xFF, (byte)0xFF, 0x21,
        (byte)0xF9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00,
        0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44,
        0x01, 0x00, 0x3B
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
            Notification n = buildNotification("服务启动中...");
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            // 前台服务启动失败不影响 HTTP 服务器
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && ACTION_STOP.equals(intent.getAction())) {
                stopAll();
                stopSelf();
                return START_NOT_STICKY;
            }
            if (intent != null && ACTION_STATUS.equals(intent.getAction())) {
                // 状态查询：广播当前状态（不重启服务）
                broadcastStatus(running.get(), tunnelUrl);
                return START_STICKY;
            }
            startAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopAll();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startAll() {
        if (running.get()) {
            broadcastStatus(true, tunnelUrl);
            return;
        }
        try {
            running.set(true);
            tunnelUrl = null;
            db = new Database(this);
            httpPool = Executors.newFixedThreadPool(16);

            serverThread = new Thread(this::startHttpServer, "rrt-http");
            serverThread.setDaemon(true);
            serverThread.start();

            broadcastStatus(true, null);

            new Thread(() -> {
                try { Thread.sleep(2500); } catch (InterruptedException ignored) {}
                startTunnel();
            }, "rrt-tunnel-start").start();
        } catch (Exception e) {
            running.set(false);
            e.printStackTrace();
        }
    }

    private void stopAll() {
        running.set(false);
        tunnelRunning.set(false);
        tunnelUrl = null;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        try { if (tunnelProcess != null) tunnelProcess.destroy(); } catch (Exception ignored) {}
        try { if (httpPool != null) httpPool.shutdownNow(); } catch (Exception ignored) {}
        try { if (db != null) db.close(); } catch (Exception ignored) {}
        db = null;
        broadcastStatus(false, null);
    }

    // ─────────────────────────────────────────────────────────────
    // 隧道启动（DNS 修复）
    // ─────────────────────────────────────────────────────────────
    private void startTunnel() {
        if (!running.get()) return;
        tunnelRunning.set(true);

        // 关键：写入自定义 resolv.conf（公共 DNS），绕过被污染的 [::1]:53
        writeResolvConf();

        // 内置 Termux 动态编译版 cloudflared（二进制已 patch 读取本路径）
        startBuiltInTunnel();
    }

    /** 写自定义 resolv.conf 到 cloudflared 读取的路径（二进制已 patch） */
    private void writeResolvConf() {
        try {
            File dir = new File(getFilesDir(), "xx");
            if (!dir.exists()) dir.mkdirs();
            File conf = new File(dir, "resolv.conf");
            java.io.FileWriter fw = new java.io.FileWriter(conf);
            fw.write("nameserver 223.5.5.5\n");
            fw.write("nameserver 1.1.1.1\n");
            fw.write("nameserver 8.8.8.8\n");
            fw.close();
            writeLog("✅ resolv.conf 已写入: " + conf.getAbsolutePath());
        } catch (Exception e) {
            writeLog("⚠️ resolv.conf 写入失败: " + e.getMessage());
        }
    }

    /** App 内置 cloudflared（新版 serve 命令 + DNS 参数） */
    private void startBuiltInTunnel() {
        if (!running.get()) return;
        File cloudflaredFile = null;
        String locateError = null;
        // 策略1：从 nativeLibraryDir 读取（如果打包到 jniLibs）
        try {
            File dir = new File(getApplicationInfo().nativeLibraryDir);
            File so = new File(dir, "libcloudflared.so");
            if (so.exists() && so.canRead()) {
                cloudflaredFile = so;
                writeLog("cloudflared 定位(nativeLibraryDir): " + so.getAbsolutePath());
            }
        } catch (Exception e) {
            locateError = "nativeLibraryDir失败: " + e.getMessage();
        }
        // 策略2：反射 VMRuntime 获取原生库目录
        if (cloudflaredFile == null) {
            try {
                java.lang.reflect.Field field = Class.forName("dalvik.system.VMRuntime")
                        .getDeclaredField("nativeLibraryDirectories");
                field.setAccessible(true);
                Object[] dirs = (Object[]) field.get(null);
                if (dirs != null) {
                    for (Object d : dirs) {
                        File dir = new File(d.toString());
                        File so = new File(dir, "libcloudflared.so");
                        if (so.exists() && so.canRead()) {
                            cloudflaredFile = so;
                            writeLog("cloudflared 定位(VMRuntime): " + so.getAbsolutePath());
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        // 策略3：从 assets 解压到 filesDir（可写目录，避免 nativeLibraryDir 只读问题）
        if (cloudflaredFile == null) {
            try {
                File binDir = new File(getFilesDir(), "bin");
                if (!binDir.exists()) binDir.mkdirs();
                File extracted = new File(binDir, "libcloudflared.so");
                if (!extracted.exists() || extracted.length() < 1000000) {
                    try (java.io.InputStream in = getAssets().open("cloudflared");
                         FileOutputStream out = new FileOutputStream(extracted)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    writeLog("cloudflared 已从 assets 解压到: " + extracted.getAbsolutePath()
                            + " (" + extracted.length() + " 字节)");
                }
                if (extracted.exists() && extracted.canRead()) {
                    cloudflaredFile = extracted;
                } else {
                    locateError = (locateError != null ? locateError + "; " : "") + "assets解压后文件不可读";
                }
            } catch (Exception e) {
                locateError = (locateError != null ? locateError + "; " : "") + "assets解压失败: " + e.getMessage();
            }
        }
        // 全部策略失败：只打日志，不抛出异常，不影响 HTTP 服务
        if (cloudflaredFile == null) {
            writeLog("cloudflared 定位失败（隧道功能不可用，HTTP服务正常）: "
                    + (locateError != null ? locateError : "未找到二进制"));
            tunnelRunning.set(false);
            return;
        }
        try {
            cloudflaredFile.setExecutable(true);
        } catch (Exception ignored) {}
        writeLog("cloudflared 就绪: " + cloudflaredFile.getAbsolutePath()
                + " (" + cloudflaredFile.length() + " 字节)");
        // 启动隧道进程
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(cloudflaredFile.getAbsolutePath());
        cmd.add("tunnel");
        cmd.add("--url");
        cmd.add("http://127.0.0.1:" + PORT);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(getFilesDir());
        pb.redirectErrorStream(true);
        final File logFile = new File(getFilesDir(), "cloudflared.log");
        writeLog("启动隧道: " + String.join(" ", cmd));
        try {
            pb.redirectOutput(logFile);
            tunnelProcess = pb.start();
        } catch (Exception e) {
            writeLog("隧道进程启动失败: " + e.getMessage());
            tunnelRunning.set(false);
            return;
        }
        new Thread(() -> {
            Pattern pattern = Pattern.compile("https://[a-z0-9][a-z0-9-]*\\.trycloudflare\\.com");
            while (tunnelRunning.get()) {
                try {
                    if (logFile.exists() && logFile.length() > 0) {
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(
                                new java.io.FileReader(logFile))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line).append("\n");
                        }
                        Matcher m = pattern.matcher(sb.toString());
                        if (m.find()) {
                            String url = m.group();
                            if (!url.equals(tunnelUrl)) {
                                tunnelUrl = url;
                                broadcastStatus(true, url);
                            }
                        }
                    }
                } catch (Exception ignored) {}
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }, "rrt-tunnel-monitor").start();
    }

    // ─────────────────────────────────────────────────────────────
    // HTTP 服务器
    // ─────────────────────────────────────────────────────────────
    private void startHttpServer() {
        try {
            serverSocket = new ServerSocket(PORT);
        } catch (Exception e) {
            broadcastStatus(true, null);
            return;
        }
        broadcastStatus(true, tunnelUrl);
        while (running.get()) {
            try {
                final Socket socket = serverSocket.accept();
                httpPool.submit(() -> handleRequest(socket));
            } catch (Exception e) {
                if (!running.get()) break;
            }
        }
        try { serverSocket.close(); } catch (Exception ignored) {}
    }

    private void handleRequest(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(30000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String requestLine = reader.readLine();
            if (requestLine == null) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String fullPath = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int idx = line.indexOf(":");
                if (idx > 0) headers.put(line.substring(0, idx).trim().toLowerCase(),
                        line.substring(idx + 1).trim());
            }

            StringBuilder bodySb = new StringBuilder();
            int cl = 0;
            try { cl = Integer.parseInt(headers.getOrDefault("content-length", "0")); } catch (Exception ignored) {}
            if (cl > 0) {
                char[] buf = new char[cl];
                int read = 0;
                while (read < cl) {
                    int n = reader.read(buf, read, cl - read);
                    if (n < 0) break;
                    read += n;
                }
                bodySb.append(buf, 0, read);
            }
            String body = bodySb.toString();

            String path = fullPath;
            String query = "";
            int qIdx = fullPath.indexOf("?");
            if (qIdx >= 0) {
                path = fullPath.substring(0, qIdx);
                query = fullPath.substring(qIdx + 1);
            }

            String clientIp = headers.getOrDefault("x-forwarded-for", "");
            if (clientIp.contains(",")) clientIp = clientIp.split(",")[0].trim();
            if (clientIp.isEmpty()) clientIp = headers.getOrDefault("x-real-ip", "");
            if (clientIp.isEmpty()) {
                try {
                    clientIp = s.getRemoteSocketAddress().toString();
                    int colon = clientIp.lastIndexOf(":");
                    if (colon > 0) clientIp = clientIp.substring(1, colon);
                } catch (Exception ignored) { clientIp = "0.0.0.0"; }
            }
            final String ip = clientIp;

            byte[] response = route(method, path, query, body, ip, headers.getOrDefault("user-agent", ""));
            s.getOutputStream().write(response);
            s.getOutputStream().flush();
        } catch (Exception ignored) {}
    }

    private static String qParam(String query, String name) {
        if (query == null) return "";
        String[] pairs = query.split("&");
        for (String p : pairs) {
            int eq = p.indexOf("=");
            if (eq < 0) continue;
            if (p.substring(0, eq).equals(name)) {
                try { return URLDecoder.decode(p.substring(eq + 1), "UTF-8"); }
                catch (Exception ignored) { return ""; }
            }
        }
        return "";
    }

    private static byte[] json(String content, int code) {
        String status = code == 200 ? "OK" : code == 400 ? "Bad Request" :
                code == 404 ? "Not Found" : code == 429 ? "Too Many Requests" : "Internal Server Error";
        byte[] body = content.getBytes();
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(" ").append(status).append("\r\n")
         .append("Content-Type: application/json; charset=utf-8\r\n")
         .append("Content-Length: ").append(body.length).append("\r\n")
         .append("Connection: close\r\n\r\n");
        byte[] header = h.toString().getBytes();
        byte[] result = new byte[header.length + body.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(body, 0, result, header.length, body.length);
        return result;
    }

    private static byte[] html(String content, int code) {
        byte[] body = content.getBytes();
        String status = code == 200 ? "OK" : "Not Found";
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(code).append(" ").append(status).append("\r\n")
         .append("Content-Type: text/html; charset=utf-8\r\n")
         .append("Content-Length: ").append(body.length).append("\r\n")
         .append("Connection: close\r\n\r\n");
        byte[] header = h.toString().getBytes();
        byte[] result = new byte[header.length + body.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(body, 0, result, header.length, body.length);
        return result;
    }

    private byte[] route(String method, String path, String query, String body,
                         String ip, String ua) {
        Database d = db;
        if (d == null) return json("{\"error\":\"db not ready\"}", 500);

        if ("/health".equals(path))
            return json("{\"status\":\"ok\",\"service\":\"read-receipt-tracker\"}", 200);

        if ("/pixel".equals(path) || "/pixel.gif".equals(path)) {
            String wxId = qParam(query, "wxId");
            String msgId = qParam(query, "id");
            String reader = qParam(query, "readerWxId");
            if (reader.isEmpty()) reader = qParam(query, "reader");
            if (!wxId.isEmpty() && !msgId.isEmpty()) {
                String readerFinal = "/pixel.gif".equals(path) ? "未知访客" : reader;
                GeoLookup.Geo geo = GeoLookup.lookup(ip);
                d.recordRead(msgId, wxId, ip, ua,
                        geo != null ? geo.country : "",
                        geo != null ? geo.region : "",
                        geo != null ? geo.city : "",
                        geo != null ? geo.isp : "",
                        geo != null ? geo.loc : "",
                        readerFinal.isEmpty() ? wxId : readerFinal);
            }
            StringBuilder h = new StringBuilder();
            h.append("HTTP/1.1 200 OK\r\nContent-Type: image/gif\r\n")
             .append("Content-Length: ").append(TRANSPARENT_GIF.length).append("\r\n")
             .append("Connection: close\r\nCache-Control: no-cache\r\n\r\n");
            byte[] header = h.toString().getBytes();
            byte[] result = new byte[header.length + TRANSPARENT_GIF.length];
            System.arraycopy(header, 0, result, 0, header.length);
            System.arraycopy(TRANSPARENT_GIF, 0, result, header.length, TRANSPARENT_GIF.length);
            return result;
        }

        if ("/register".equals(path) && "POST".equals(method)) {
            Map<String, String> data = parseJson(body);
            String wxId = data.getOrDefault("wxId", "");
            String content = data.getOrDefault("content", "");
            long createTime = System.currentTimeMillis();
            if (data.containsKey("createTime")) {
                try { createTime = Long.parseLong(data.get("createTime")); } catch (Exception ignored) {}
            }
            if (wxId.isEmpty()) return json("{\"error\":\"wxId required\"}", 400);
            String msgId = sha256(wxId + "\u0000" + content + "\u0000" + createTime);
            d.registerMessage(msgId, wxId, content, createTime);
            return json("{\"success\":true,\"id\":\"" + msgId + "\",\"wxId\":\"" + escJson(wxId) +
                    "\",\"pixel_url\":\"http://127.0.0.1:" + PORT + "/pixel?wxId=" +
                    Uri.encode(wxId) + "&id=" + msgId + "\"}", 200);
        }

        if ("/count".equals(path)) {
            String wxId = qParam(query, "wxId");
            String msgId = qParam(query, "id");
            if (wxId.isEmpty() || msgId.isEmpty())
                return json("{\"count\":0,\"error\":\"wxId and id required\"}", 200);
            int count = d.readCount(msgId, wxId);
            return json("{\"count\":" + count + ",\"msg_id\":\"" + msgId + "\"}", 200);
        }

        if ("/api/messages".equals(path)) {
            StringBuilder sb = new StringBuilder();
            android.database.Cursor c = d.messageList(100);
            while (c.moveToNext()) {
                if (sb.length() > 0) sb.append(",");
                sb.append("{\"id\":\"").append(c.getString(c.getColumnIndexOrThrow("id")))
                  .append("\",\"wxId\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("wx_id"))))
                  .append("\",\"content\":\"").append(escJson(c.getString(c.getColumnIndexOrThrow("content"))))
                  .append("\",\"read_count\":").append(c.getInt(c.getColumnIndexOrThrow("cnt")))
                  .append(",\"registered_at\":").append(c.getLong(c.getColumnIndexOrThrow("registered_at")))
                  .append("}");
            }
            c.close();
            return json("{\"messages\":[" + sb + "]}", 200);
        }

        if (path.startsWith("/api/reads/")) {
            String msgId = path.substring("/api/reads/".length());
            android.database.Cursor msg = d.getMessage(msgId);
            if (msg == null) return json("{\"error\":\"not found\"}", 404);
            StringBuilder sb = new StringBuilder();
            android.database.Cursor c = d.readList(msgId);
            while (c.moveToNext()) {
                if (sb.length() > 0) sb.append(",");
                String reader = c.getString(c.getColumnIndexOrThrow("reader_wx_id"));
                if (reader == null || reader.isEmpty())
                    reader = c.getString(c.getColumnIndexOrThrow("wx_id"));
                String rip = c.getString(c.getColumnIndexOrThrow("ip_address"));
                String country = c.getString(c.getColumnIndexOrThrow("country"));
                String region = c.getString(c.getColumnIndexOrThrow("region"));
                String city = c.getString(c.getColumnIndexOrThrow("city"));
                String isp = c.getString(c.getColumnIndexOrThrow("isp"));
                String loc = c.getString(c.getColumnIndexOrThrow("loc"));
                long rt = c.getLong(c.getColumnIndexOrThrow("read_at"));
                sb.append("{\"ip_address\":\"").append(escJson(rip))
                  .append("\",\"location\":\"").append(escJson(joinAddr(country, region, city)))
                  .append("\",\"country\":\"").append(escJson(country))
                  .append("\",\"region\":\"").append(escJson(region))
                  .append("\",\"city\":\"").append(escJson(city))
                  .append("\",\"isp\":\"").append(escJson(isp))
                  .append("\",\"loc\":\"").append(escJson(loc))
                  .append("\",\"reader_wx_id\":\"").append(escJson(reader))
                  .append("\",\"read_at\":").append(rt).append("}");
            }
            c.close();
            String wx = msg.getString(msg.getColumnIndexOrThrow("wx_id"));
            String content = msg.getString(msg.getColumnIndexOrThrow("content"));
            msg.close();
            return json("{\"msg_id\":\"" + msgId + "\",\"wxId\":\"" + escJson(wx) +
                    "\",\"content\":\"" + escJson(content) + "\",\"reads\":[" + sb + "]}", 200);
        }

        if ("/api/delete-all".equals(path)) {
            d.deleteAll();
            return json("{\"success\":true}", 200);
        }

        if (path.startsWith("/api/delete/")) {
            d.deleteMessage(path.substring("/api/delete/".length()));
            return json("{\"success\":true}", 200);
        }

        if ("/batch-status".equals(path)) {
            String idsStr = qParam(query, "ids");
            if (idsStr.isEmpty()) return json("{\"error\":\"ids required\"}", 400);
            String[] ids = idsStr.split(",");
            StringBuilder sb = new StringBuilder();
            for (String id : ids) {
                if (id.trim().isEmpty()) continue;
                if (sb.length() > 0) sb.append(",");
                sb.append("\"").append(escJson(id.trim())).append("\":")
                  .append(d.readCount(id.trim(), ""));
            }
            return json("{\"statuses\":{" + sb + "}}", 200);
        }

        if ("/".equals(path)) return html(ConsoleHtml.index(d), 200);

        if (path.startsWith("/message/")) {
            String msgId = path.substring("/message/".length());
            android.database.Cursor msg = d.getMessage(msgId);
            if (msg == null) return html("not found", 404);
            String result = ConsoleHtml.detail(d, msg);
            msg.close();
            return html(result, 200);
        }

        return json("{\"error\":\"not found\"}", 404);
    }

    private static String joinAddr(String country, String region, String city) {
        StringBuilder sb = new StringBuilder();
        if (country != null && !country.isEmpty()) sb.append(country).append(" ");
        if (region != null && !region.isEmpty()) sb.append(region).append(" ");
        if (city != null && !city.isEmpty()) sb.append(city);
        return sb.length() == 0 ? "-" : sb.toString().trim();
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static Map<String, String> parseJson(String text) {
        Map<String, String> map = new HashMap<>();
        if (text == null) return map;
        Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\"[^\"]*\"|\\d+)");
        Matcher m = p.matcher(text);
        while (m.find()) {
            String v = m.group(2);
            if (v.startsWith("\"")) v = v.substring(1, v.length() - 1);
            map.put(m.group(1), v);
        }
        return map;
    }

    private static String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "已读追踪服务", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle("已读追踪服务")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void broadcastStatus(boolean runningVal, String url) {
        Intent intent = new Intent(ACTION_STATUS);
        intent.putExtra(EXTRA_RUNNING, runningVal);
        intent.putExtra(EXTRA_TUNNEL_URL, url);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    /** 写入 App 自己的日志文件（同时显示在 cloudflared.log 里） */
    private void writeLog(String message) {
        try {
            File logFile = new File(getFilesDir(), "cloudflared.log");
            java.io.FileWriter fw = new java.io.FileWriter(logFile, true);
            fw.write(java.text.DateFormat.getTimeInstance().format(new Date()) + " [APP] " + message + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }
}
