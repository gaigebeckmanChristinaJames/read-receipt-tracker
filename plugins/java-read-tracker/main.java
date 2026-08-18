import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

String FRAMEWORK = "unknown";
String PLUGIN_DIR = "";
int PORT = 5000;
String DB_FILE = "";
String LOG_FILE = "";
String GEO_API = "http://ip-api.com/json";
Context appContext = null;
ServerSocket serverSocket = null;
String publicDomain = "";
boolean hasSent = false;
AlertDialog currentDialog = null;
TextView logView = null;
Handler uiHandler = null;
Runnable logUpdater = null;
SQLiteDatabase db = null;

byte[] PIXEL_GIF = new byte[]{
    0x47,0x49,0x46,0x38,0x39,0x61,0x01,0x00,0x01,0x00,
    (byte)0x80,0x00,0x00,0x00,0x00,0x00,(byte)0xFF,(byte)0xFF,(byte)0xFF,
    0x21,(byte)0xF9,0x04,0x01,0x00,0x00,0x00,0x00,0x2C,0x00,
    0x00,0x00,0x00,0x01,0x00,0x01,0x00,0x00,0x02,0x01,
    0x44,0x00,0x3B
};

void detectFramework() {
    try {
        try {
            Object pd = this.interpreter.get("pluginDir");
            if (pd != null) {
                FRAMEWORK = "hchat";
                PLUGIN_DIR = pd.toString();
                return;
            }
        } catch (Exception e) {}
        try {
            Object jh = this.interpreter.get("JavaHookApi");
            if (jh != null) {
                FRAMEWORK = "wekit";
                try {
                    Object app = this.interpreter.get("hostinfo");
                    if (app != null) {
                        Object ctx = app.getClass().getMethod("getApplication").invoke(app);
                        PLUGIN_DIR = ((Context)ctx).getFilesDir().getAbsolutePath() + "/tracker";
                    }
                } catch (Exception e2) {}
                if (PLUGIN_DIR.isEmpty()) PLUGIN_DIR = "/sdcard/WeKit/tracker";
                return;
            }
        } catch (Exception e) {}
        FRAMEWORK = "hchat";
        try { PLUGIN_DIR = pluginDir; } catch (Exception e) { PLUGIN_DIR = "."; }
    } catch (Exception e) {
        FRAMEWORK = "hchat";
        PLUGIN_DIR = ".";
    }
}

String nowStr() {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
}

void writeLog(String msg) {
    try {
        FileWriter fw = new FileWriter(LOG_FILE, true);
        PrintWriter pw = new PrintWriter(fw);
        pw.println("[" + nowStr() + "] " + msg);
        pw.flush();
        pw.close();
    } catch (Exception e) {}
    try { log("[tracker] " + msg); } catch (Exception e) {}
}

void toastMsg(String msg) {
    try { toast(msg); return; } catch (Exception e) {}
    try {
        Context ctx = getTopActivitySafe();
        if (ctx != null) Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
    } catch (Exception e2) {}
}

String getSelfWxid() {
    try { return getLoginWxid(); } catch (Exception e) {}
    try {
        Object app = this.interpreter.get("hostinfo");
        if (app != null) return app.getClass().getMethod("getSelfWxId").invoke(app).toString();
    } catch (Exception e) {}
    return "";
}

void sendMsg(String talker, String text) {
    try { sendText(talker, text); return; } catch (Exception e) {}
    try {
        Object app = this.interpreter.get("hostinfo");
        if (app != null) {
            app.getClass().getMethod("sendText", String.class, String.class).invoke(app, talker, text);
            return;
        }
    } catch (Exception e) {}
    writeLog("sendMsg失败");
}

Context getTopActivitySafe() {
    try { return getTopActivity(); } catch (Exception e) {}
    try {
        Object app = this.interpreter.get("hostinfo");
        if (app != null) return (Context) app.getClass().getMethod("getApplication").invoke(app);
    } catch (Exception e) {}
    return null;
}

void loadConfig() {
    DB_FILE = PLUGIN_DIR + "/track_data/receipts.db";
    LOG_FILE = PLUGIN_DIR + "/log.txt";
    if (appContext == null) appContext = getTopActivitySafe();
    File f = new File(PLUGIN_DIR + "/config.prop");
    if (!f.exists()) { writeLog("config.prop不存在，使用默认配置"); return; }
    try {
        BufferedReader br = new BufferedReader(new FileReader(f));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] kv = line.split("=", 2);
            if (kv.length < 2) continue;
            String k = kv[0].trim();
            String v = kv[1].trim();
            if (k.equals("port")) PORT = Integer.parseInt(v);
        }
        br.close();
        writeLog("配置加载 port=" + PORT + " framework=" + FRAMEWORK);
    } catch (Exception e) {
        writeLog("读取配置异常:" + e.getMessage());
    }
}

void initDB() {
    try {
        File dbDir = new File(PLUGIN_DIR + "/track_data");
        if (!dbDir.exists()) dbDir.mkdirs();
        Context ctx = getTopActivitySafe();
        if (ctx != null) {
            db = ctx.openOrCreateDatabase(DB_FILE, Context.MODE_PRIVATE, null);
        } else {
            db = SQLiteDatabase.openOrCreateDatabase(DB_FILE, null);
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (" +
            "id TEXT PRIMARY KEY," +
            "wx_id TEXT NOT NULL," +
            "content TEXT DEFAULT ''," +
            "create_time INTEGER NOT NULL," +
            "registered_at INTEGER DEFAULT (strftime('%s','now'))" +
            ")");
        db.execSQL("CREATE TABLE IF NOT EXISTS reads (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "msg_id TEXT NOT NULL," +
            "wx_id TEXT NOT NULL," +
            "ip_address TEXT," +
            "user_agent TEXT," +
            "country TEXT DEFAULT ''," +
            "region TEXT DEFAULT ''," +
            "city TEXT DEFAULT ''," +
            "isp TEXT DEFAULT ''," +
            "loc TEXT DEFAULT ''," +
            "reader_wx_id TEXT DEFAULT ''," +
            "read_at INTEGER DEFAULT (strftime('%s','now'))," +
            "UNIQUE(msg_id, ip_address)" +
            ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reads_msg ON reads(msg_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msgs_wx ON messages(wx_id)");
        String[] readCols = {"user_agent","country","region","city","isp","loc","reader_wx_id"};
        for (int ci=0; ci<readCols.length; ci++) {
            try { db.execSQL("ALTER TABLE reads ADD COLUMN " + readCols[ci] + " TEXT DEFAULT ''"); } catch (Exception e) {}
        }
        String[] msgCols = {"create_time","registered_at"};
        for (int ci=0; ci<msgCols.length; ci++) {
            try { db.execSQL("ALTER TABLE messages ADD COLUMN " + msgCols[ci] + " INTEGER DEFAULT 0"); } catch (Exception e) {}
        }
        writeLog("数据库初始化完成");
    } catch (Exception e) {
        writeLog("数据库异常:" + e.getMessage());
    }
}

String computeMsgId(String wxId, String content, long createTime) {
    try {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        md.update(wxId.getBytes("UTF-8"));
        md.update((byte)0);
        md.update(content.getBytes("UTF-8"));
        md.update((byte)0);
        md.update(String.valueOf(createTime).getBytes("UTF-8"));
        byte[] digest = md.digest();
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            String h = Integer.toHexString(digest[i] & 0xFF);
            if (h.length() == 1) hex.append("0");
            hex.append(h);
        }
        return hex.toString();
    } catch (Exception e) {
        return String.valueOf((wxId + content + createTime).hashCode());
    }
}

void registerMessage(String id, String wxId, String content, long createTime) {
    try {
        if (db == null) initDB();
        db.execSQL("INSERT OR IGNORE INTO messages(id,wx_id,content,create_time) VALUES(?,?,?,?)",
            new Object[]{id, wxId, content, createTime});
    } catch (Exception e) {
        writeLog("注册消息异常: " + e.getMessage());
    }
}

void recordRead(String msgId, String wxId, String ip, String ua,
                String country, String region, String city, String isp, String loc, String readerWxId) {
    try {
        if (db == null) initDB();
        if (ua != null && ua.length() > 500) ua = ua.substring(0, 500);
        db.execSQL("INSERT OR IGNORE INTO reads(msg_id,wx_id,ip_address,user_agent,country,region,city,isp,loc,reader_wx_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
            new Object[]{msgId, wxId, ip, ua, country, region, city, isp, loc, readerWxId});
    } catch (Exception e) {
        writeLog("记录已读异常: " + e.getMessage());
    }
}

int readCount(String msgId, String wxId) {
    try {
        if (db == null) return 0;
        Cursor c = db.rawQuery("SELECT COUNT(DISTINCT ip_address) FROM reads WHERE msg_id=? AND wx_id=?",
            new String[]{msgId, wxId});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    } catch (Exception e) { return 0; }
}

long[] getStats() {
    long tm = 0, tr = 0;
    try {
        if (db == null) return new long[]{0,0};
        Cursor c1 = db.rawQuery("SELECT COUNT(*) FROM messages", null);
        if (c1.moveToFirst()) tm = c1.getLong(0);
        c1.close();
        Cursor c2 = db.rawQuery("SELECT COUNT(DISTINCT ip_address) FROM reads", null);
        if (c2.moveToFirst()) tr = c2.getLong(0);
        c2.close();
    } catch (Exception e) {}
    return new long[]{tm, tr};
}

void deleteMessage(String id) {
    try {
        if (db == null) return;
        db.execSQL("DELETE FROM reads WHERE msg_id=?", new Object[]{id});
        db.execSQL("DELETE FROM messages WHERE id=?", new Object[]{id});
    } catch (Exception e) {
        writeLog("删除消息异常: " + e.getMessage());
    }
}

void deleteAll() {
    try {
        if (db == null) return;
        db.execSQL("DELETE FROM reads");
        db.execSQL("DELETE FROM messages");
    } catch (Exception e) {
        writeLog("清空异常: " + e.getMessage());
    }
}

int findAvailablePort(int startPort) {
    for (int p = startPort; p < startPort + 20; p++) {
        try {
            ServerSocket test = new ServerSocket(p);
            test.close();
            return p;
        } catch (Exception e) {}
    }
    return startPort;
}

boolean tunnelRunning = false;
Process cloudflaredProcess = null;

void startTunnel() {
    tunnelRunning = true;
    new Thread() {
        void run() {
            try {
                startCloudflaredTunnel();
            } catch (Throwable t) {
                writeLog("隧道线程崩溃: " + t.toString());
            }
        }
    }.start();
}

boolean startCloudflaredTunnel() {
    try {
        Context ctx = getTopActivitySafe();
        File cfDir = new File(ctx.getFilesDir(), "rrt_bin");
        if (!cfDir.exists()) cfDir.mkdirs();
        File cfFile = new File(cfDir, "cf");
        if (!cfFile.exists() || cfFile.length() < 1000000) {
            File src = new File(PLUGIN_DIR + "/lib/libcloudflared.so");
            if (!src.exists()) {
                writeLog("未找到libcloudflared.so: " + src.getAbsolutePath());
                return false;
            }
            writeLog("复制cloudflared: " + (src.length()/1024/1024) + "MB -> " + cfFile.getAbsolutePath());
            FileInputStream fis = new FileInputStream(src);
            FileOutputStream fos = new FileOutputStream(cfFile);
            byte[] buf = new byte[65536];
            int n;
            while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
            fis.close();
            fos.close();
            cfFile.setReadable(true);
            writeLog("复制完成");
        }
        try {
            File dnsDir = new File(ctx.getFilesDir(), "xx");
            if (!dnsDir.exists()) dnsDir.mkdirs();
            dnsDir.setExecutable(true, false);
            dnsDir.setReadable(true, false);
            File resolv = new File(dnsDir, "resolv.conf");
            java.io.FileWriter rw = new java.io.FileWriter(resolv);
            rw.write("nameserver 223.5.5.5\n");
            rw.write("nameserver 1.1.1.1\n");
            rw.write("nameserver 8.8.8.8\n");
            rw.close();
            resolv.setReadable(true, false);
        } catch (Throwable dnsE) {
            writeLog("resolv.conf写入失败: " + dnsE.toString());
        }
        String linker = "/system/bin/linker64";
        if (!new File(linker).exists()) linker = "/system/bin/linker";
        writeLog("使用linker: " + linker);
        ProcessBuilder pb = new ProcessBuilder(
            linker, cfFile.getAbsolutePath(),
            "tunnel", "--protocol", "http2", "--edge-ip-version", "4", "--url", "http://127.0.0.1:" + PORT
        );
        pb.directory(ctx.getFilesDir());
        pb.redirectErrorStream(true);
        java.util.Map<String,String> env = pb.environment();
        env.put("GODEBUG", "netdns=go,ipv6=0");
        env.put("TUNNEL_DNS_RESOLVER_ADDR", "223.5.5.5:53");
        env.put("TUNNEL_EDGE_IP_VERSION", "4");
        writeLog("启动: " + linker + " cf tunnel --protocol http2 --edge-ip-version 4 --url http://127.0.0.1:" + PORT);
        cloudflaredProcess = pb.start();
        final java.io.InputStream is = cloudflaredProcess.getInputStream();
        final StringBuilder allOutput = new StringBuilder();
        Thread reader = new Thread() {
            void run() {
                try {
                    byte[] b = new byte[4096];
                    while (tunnelRunning) {
                        int avail = is.available();
                        if (avail > 0) {
                            int n = is.read(b, 0, Math.min(avail, b.length));
                            if (n < 0) break;
                            String chunk = new String(b, 0, n, "UTF-8");
                            allOutput.append(chunk);
                            String[] lines = chunk.split("\n");
                            for (String line : lines) {
                                if (line.trim().length() > 0) writeLog("[cf] " + line.trim());
                            }
                        } else {
                            try { Thread.sleep(500); } catch (Throwable e) {}
                        }
                    }
                } catch (Throwable t) {
                    if (tunnelRunning) writeLog("cf读线程结束: " + t.getMessage());
                }
            }
        };
        reader.setDaemon(true);
        reader.start();
        long startWait = System.currentTimeMillis();
        java.util.regex.Pattern urlPattern = java.util.regex.Pattern.compile("https://[a-z0-9][a-z0-9-]*-[a-z0-9-]*\\.trycloudflare\\.com");
        while (System.currentTimeMillis() - startWait < 90000 && tunnelRunning) {
            String out = allOutput.toString();
            java.util.regex.Matcher m = urlPattern.matcher(out);
            if (m.find()) {
                String candidate = m.group();
                if (candidate.contains("api.trycloudflare.com")) {
                    allOutput.setLength(0);
                    try { Thread.sleep(2000); } catch (Throwable e) {}
                    continue;
                }
                publicDomain = candidate;
                writeLog("====隧道地址: " + publicDomain + "====");
                if (!hasSent) {
                    hasSent = true;
                    try {
                        String selfId = getSelfWxid();
                        if (!selfId.isEmpty()) {
                            sendMsg(selfId, "控制台:http://127.0.0.1:" + PORT + "/\n隧道:" + publicDomain);
                            writeLog("地址已发送到微信");
                        }
                    } catch (Throwable se) {
                        writeLog("自动发送失败: " + se.getMessage());
                    }
                }
                return true;
            }
            try {
                int exit = cloudflaredProcess.exitValue();
                writeLog("cloudflared已退出 code=" + exit);
                return false;
            } catch (IllegalThreadStateException e) {}
            try { Thread.sleep(1000); } catch (Throwable e) {}
        }
        writeLog("等待隧道URL超时");
        return false;
    } catch (Throwable t) {
        writeLog("cloudflared启动失败: " + t.toString());
        return false;
    }
}

void stopTunnel() {
    tunnelRunning = false;
    if (cloudflaredProcess != null) {
        cloudflaredProcess.destroy();
        cloudflaredProcess = null;
    }
    publicDomain = "";
    writeLog("隧道已关闭");
}

String[] getGeoInfo(String ip) {
    String[] empty = {"", "", "", "", ""};
    if (ip == null || ip.isEmpty() || ip.equals("127.0.0.1") || ip.equals("::1") ||
        ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))
        return empty;
    try {
        URL url = new URL("http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=status,country,regionName,city,isp,lat,lon");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "read-receipt-tracker/9.0");
        if (conn.getResponseCode() == 200) {
            byte[] buf = new byte[4096];
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream is = conn.getInputStream();
            int r;
            while ((r = is.read(buf)) != -1) bos.write(buf, 0, r);
            is.close();
            String json = bos.toString("UTF-8");
            String status = getJsonVal(json, "status");
            if ("success".equals(status)) {
                String country = getJsonVal(json, "country");
                String region = getJsonVal(json, "regionName");
                String city = getJsonVal(json, "city");
                String isp = cnIsp(getJsonVal(json, "isp"));
                String lat = getJsonVal(json, "lat");
                String lon = getJsonVal(json, "lon");
                String loc = "";
                if (!lat.isEmpty() && !lon.isEmpty()) loc = lat + "," + lon;
                return new String[]{country, region, city, isp, loc};
            }
        }
        conn.disconnect();
    } catch (Exception e) {
        writeLog("IP解析失败 " + ip + ": " + e.getMessage());
    }
    return empty;
}

String cnIsp(String isp) {
    if (isp == null || isp.isEmpty()) return "";
    String lower = isp.toLowerCase();
    if (lower.contains("移动") || lower.contains("china mobile")) return "中国移动";
    if (lower.contains("联通") || lower.contains("china unicom")) return "中国联通";
    if (lower.contains("电信") || lower.contains("china telecom")) return "中国电信";
    if (lower.contains("广电")) return "中国广电";
    if (lower.contains("education")) return "教育网";
    return isp;
}

String getJsonVal(String json, String key) {
    try {
        String k = "\"" + key + "\":";
        int idx = json.indexOf(k);
        if (idx == -1) return "";
        int pos = idx + k.length();
        if (pos >= json.length()) return "";
        if (json.charAt(pos) == '"') {
            pos++;
            int end = json.indexOf("\"", pos);
            if (end < 0) return "";
            return json.substring(pos, end);
        } else {
            int end1 = json.indexOf(",", pos);
            int end2 = json.indexOf("}", pos);
            int end = json.length();
            if (end1 > 0) end = Math.min(end, end1);
            if (end2 > 0) end = Math.min(end, end2);
            return json.substring(pos, end).trim();
        }
    } catch (Exception e) { return ""; }
}

String extractJsonString(String json, String field) {
    try {
        String k = "\"" + field + "\"";
        int idx = json.indexOf(k);
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx + k.length());
        if (colon == -1) return null;
        int pos = colon + 1;
        while (pos < json.length() && (json.charAt(pos) == ' ' || json.charAt(pos) == '\t')) pos++;
        if (pos >= json.length() || json.charAt(pos) != '"') return null;
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '\\' && pos + 1 < json.length()) {
                char next = json.charAt(pos + 1);
                if (next == '"') sb.append('"');
                else if (next == '\\') sb.append('\\');
                else if (next == 'n') sb.append('\n');
                else if (next == 't') sb.append('\t');
                else if (next == 'r') sb.append('\r');
                else sb.append(next);
                pos += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                pos++;
            }
        }
        return sb.toString();
    } catch (Exception e) { return null; }
}

Long extractJsonLong(String json, String field) {
    try {
        String k = "\"" + field + "\"";
        int idx = json.indexOf(k);
        if (idx == -1) return null;
        int colon = json.indexOf(":", idx + k.length());
        if (colon == -1) return null;
        int pos = colon + 1;
        while (pos < json.length() && (json.charAt(pos) == ' ' || json.charAt(pos) == '\t')) pos++;
        if (pos >= json.length()) return null;
        if (json.charAt(pos) == '"') pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if (c == '"' || c == ',' || c == '}' || c == ' ' || c == '\t' || c == '\n' || c == '\r') break;
            sb.append(c);
            pos++;
        }
        if (sb.length() == 0) return null;
        return Long.parseLong(sb.toString());
    } catch (Exception e) { return null; }
}

String readLogFile(int maxLines) {
    try {
        File f = new File(LOG_FILE);
        if (!f.exists()) return "(暂无日志)";
        BufferedReader br = new BufferedReader(new FileReader(f));
        LinkedList lines = new LinkedList();
        String line;
        while ((line = br.readLine()) != null) {
            lines.add(line);
            if (lines.size() > maxLines) lines.removeFirst();
        }
        br.close();
        StringBuilder sb = new StringBuilder();
        Iterator it = lines.iterator();
        while (it.hasNext()) sb.append(it.next()).append("\n");
        return sb.toString();
    } catch (Exception e) {
        return "读取日志失败: " + e.getMessage();
    }
}

boolean httpRunning = false;

void startHttp() {
    PORT = findAvailablePort(PORT);
    writeLog("使用端口: " + PORT);
    httpRunning = true;
    new Thread() {
        void run() {
            int restartCount = 0;
            while (httpRunning) {
                try {
                    serverSocket = new ServerSocket(PORT);
                    writeLog("HTTP服务启动 端口" + PORT);
                    while (httpRunning) {
                        Socket sock = serverSocket.accept();
                        sock.setSoTimeout(10000);
                        new ClientHandler(sock).start();
                    }
                } catch (Throwable e) {
                    if (httpRunning) {
                        restartCount++;
                        writeLog("HTTP服务异常(第" + restartCount + "次重启): " + e.toString());
                        try { Thread.sleep(1000); } catch (Exception ie) {}
                        PORT = findAvailablePort(PORT);
                    }
                }
            }
        }
    }.start();
}

class ClientHandler extends Thread {
    Socket sock;
    ClientHandler(Socket s) { this.sock = s; }
    public void run() {
        try {
            handleRequest(sock);
        } catch (Throwable e) {
            writeLog("请求异常: " + e.toString());
        } finally {
            try { sock.close(); } catch (Exception ee) {}
        }
    }
}

String getClientIp(Map headers, Socket sock) {
    try {
        Object cfi = headers.get("cf-connecting-ip");
        if (cfi != null && cfi.toString().length() > 0) return cfi.toString().trim();
        Object xff = headers.get("x-forwarded-for");
        if (xff != null && xff.toString().length() > 0) {
            String xffs = xff.toString();
            if (xffs.contains(",")) return xffs.split(",")[0].trim();
            return xffs.trim();
        }
        Object xri = headers.get("x-real-ip");
        if (xri != null && xri.toString().length() > 0) return xri.toString().trim();
    } catch (Exception e) {}
    return sock.getInetAddress().getHostAddress();
}

String readLine(InputStream is) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    int c;
    while ((c = is.read()) != -1) {
        if (c == '\n') break;
        if (c == '\r') continue;
        bos.write(c);
        if (bos.size() > 8192) break; // 防止超长行
    }
    if (bos.size() == 0 && c == -1) return null;
    return new String(bos.toByteArray(), "UTF-8");
}

void handleRequest(Socket sock) {
    InputStream is = null;
    OutputStream out = null;
    try {
        sock.setSoTimeout(8000); // 8秒超时，比WeKit的10秒短，确保我们先超时
        is = new BufferedInputStream(sock.getInputStream());
        out = sock.getOutputStream();
        String requestLine = readLine(is);
        if (requestLine == null || requestLine.isEmpty()) {
            sendResponse(out, "Bad Request", "text/plain; charset=utf-8", "400 Bad Request");
            return;
        }
        writeLog("HTTP请求行: " + requestLine);
        Map headers = new HashMap();
        String line;
        int contentLength = 0;
        while ((line = readLine(is)) != null && !line.isEmpty()) {
            int idx = line.indexOf(":");
            if (idx > 0) {
                String key = line.substring(0, idx).trim().toLowerCase();
                String val = line.substring(idx + 1).trim();
                headers.put(key, val);
                if ("content-length".equals(key)) {
                    try { contentLength = Integer.parseInt(val); } catch (Exception e) {}
                }
            }
        }
        Object expectHdr = headers.get("expect");
        if (expectHdr != null && expectHdr.toString().toLowerCase().contains("100-continue")) {
            try {
                out.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes("UTF-8"));
                out.flush();
                writeLog("已发送 100 Continue");
            } catch (Exception e) { writeLog("发送100 Continue失败: " + e.getMessage()); }
        }
        byte[] bodyBytes = new byte[0];
        if (contentLength > 0 && contentLength < 1048576) { // 最多1MB
            bodyBytes = new byte[contentLength];
            int read = 0;
            long startRead = System.currentTimeMillis();
            while (read < contentLength) {
                if (System.currentTimeMillis() - startRead > 5000) break; // 5秒读body超时
                int n = is.read(bodyBytes, read, contentLength - read);
                if (n < 0) break;
                read += n;
            }
            if (read < contentLength) {
                writeLog("body读取不完整: 期望" + contentLength + " 实际" + read);
                byte[] trimmed = new byte[read];
                System.arraycopy(bodyBytes, 0, trimmed, 0, read);
                bodyBytes = trimmed;
            }
        }
        String body = new String(bodyBytes, "UTF-8");
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) { sendResponse(out, "Bad Request", "text/plain; charset=utf-8", "400 Bad Request"); return; }
        String method = parts[0];
        String fullPath = parts[1];
        String path = fullPath;
        int qIdx = fullPath.indexOf("?");
        if (qIdx >= 0) path = fullPath.substring(0, qIdx);
        String ip = getClientIp(headers, sock);
        writeLog("HTTP " + method + " " + path + " ip=" + ip + " bodyLen=" + body.length());

        if ("/health".equals(path)) {
            sendResponse(out, "OK", "text/plain; charset=utf-8");

        } else if ("/pixel".equals(path) || "/pixel.png".equals(path) || "/pixel.gif".equals(path)) {
            handlePixel(fullPath, headers, ip, out);

        } else if ("/register".equals(path) && "POST".equals(method)) {
            handleRegister(body, out);

        } else if ("/count".equals(path)) {
            handleCount(fullPath, out);

        } else if ("/api/messages".equals(path)) {
            handleMessages(out);

        } else if (path.startsWith("/api/reads/")) {
            String msgId = path.substring("/api/reads/".length());
            handleReads(msgId, out);

        } else if (path.startsWith("/api/delete/") && "POST".equals(method)) {
            String msgId = path.substring("/api/delete/".length());
            deleteMessage(msgId);
            sendResponse(out, "{\"success\":true,\"message\":\"deleted\"}", "application/json; charset=utf-8");

        } else if ("/api/delete-all".equals(path) && "POST".equals(method)) {
            deleteAll();
            sendResponse(out, "{\"success\":true,\"message\":\"all deleted\"}", "application/json; charset=utf-8");

        } else if (path.startsWith("/message/")) {
            String msgId = path.substring("/message/".length());
            String html = buildMessageDetailHtml(msgId);
            if (html != null) {
                sendResponse(out, html, "text/html; charset=utf-8");
            } else {
                sendResponse(out, "Not Found", "text/plain; charset=utf-8", "404 Not Found");
            }

        } else if ("/".equals(path) || "/detail".equals(path)) {
            String html = buildConsoleHtml();
            sendResponse(out, html, "text/html; charset=utf-8");

        } else {
            sendResponse(out, "Not Found", "text/plain; charset=utf-8", "404 Not Found");
        }
    } catch (Throwable e) {
        writeLog("http请求异常: " + e.toString());
        try { if (out != null) sendResponse(out, "Server Error", "text/plain; charset=utf-8", "500 Internal Server Error"); } catch (Exception ex) {}
    } finally {
        try { if (out != null) out.flush(); } catch (Exception e) {}
        try { sock.close(); } catch (Exception ee) {}
    }
}

void handlePixel(String fullPath, Map headers, String ip, OutputStream out) {
    Map params = parseQueryParams(fullPath);
    String wxId = (String) params.get("wxId");
    String id = (String) params.get("id");
    String reader = (String) params.get("reader");
    if (reader == null) reader = "";
    String ua = "";
    try {
        Object uaObj = headers.get("user-agent");
        if (uaObj != null) ua = uaObj.toString();
    } catch (Exception e) {}
    if (wxId != null && id != null) {
        final String fwxId = wxId, fid = id, fip = ip, fua = ua, freader = reader;
        new Thread() {
            void run() {
                try {
                    String[] geo = getGeoInfo(fip);
                    recordRead(fid, fwxId, fip, fua, geo[0], geo[1], geo[2], geo[3], geo[4], freader);
                    writeLog("已读记录: id=" + fid.substring(0,8) + " ip=" + fip + " " + geo[0] + geo[1] + geo[2]);
                } catch (Throwable t) {
                    writeLog("已读记录异常: " + t.getMessage());
                }
            }
        }.start();
    } else {
        writeLog("/pixel 缺少 wxId 或 id 参数");
    }
    try {
        out.write("HTTP/1.1 200 OK\r\n".getBytes("UTF-8"));
        out.write("Content-Type: image/gif\r\n".getBytes("UTF-8"));
        out.write(("Content-Length: " + PIXEL_GIF.length + "\r\n").getBytes("UTF-8"));
        out.write("Cache-Control: no-store, no-cache, must-revalidate\r\n".getBytes("UTF-8"));
        out.write("Pragma: no-cache\r\n".getBytes("UTF-8"));
        out.write("Connection: close\r\n\r\n".getBytes("UTF-8"));
        out.write(PIXEL_GIF);
        out.flush();
    } catch (Exception e) {
        writeLog("发送pixel失败: " + e.getMessage());
    }
}

void handleRegister(String body, OutputStream out) {
    try {
        writeLog("/register 请求体: " + body);
        String wxId = extractJsonString(body, "wxId");
        if (wxId == null || wxId.isEmpty()) wxId = extractJsonString(body, "wxid");
        String content = extractJsonString(body, "content");
        Long ctLong = extractJsonLong(body, "createTime");
        long createTime;
        if (ctLong != null) {
            createTime = ctLong.longValue();
        } else {
            String ctStr = extractJsonString(body, "createTime");
            if (ctStr != null && ctStr.length() > 0) {
                try { createTime = Long.parseLong(ctStr); } catch (Exception e) { createTime = System.currentTimeMillis(); }
            } else {
                createTime = System.currentTimeMillis();
            }
        }
        if (wxId == null || wxId.isEmpty()) {
            writeLog("/register 失败: wxId 为空");
            sendResponse(out, "{\"success\":false,\"error\":\"wxId required\"}",
                "application/json; charset=utf-8", "400 Bad Request");
            return;
        }
        if (content == null) content = "";
        String id = computeMsgId(wxId, content, createTime);
        registerMessage(id, wxId, content, createTime);
        String baseUrl;
        if (publicDomain != null && publicDomain.length() > 0) {
            baseUrl = publicDomain;
        } else {
            baseUrl = "http://127.0.0.1:" + PORT;
        }
        String pixelUrl = baseUrl + "/pixel?wxId=" + wxId + "&id=" + id;
        String resp = "{\"success\":true,\"id\":\"" + id + "\",\"wxId\":\"" + wxId + "\",\"pixel_url\":\"" + pixelUrl + "\",\"pixelUrl\":\"" + pixelUrl + "\"}";
        writeLog("/register 成功: id=" + id.substring(0, 8) + " wxId=" + wxId + " createTime=" + createTime + " pixelUrl=" + pixelUrl);
        sendResponse(out, resp, "application/json; charset=utf-8");
    } catch (Exception e) {
        writeLog("注册异常: " + e.getMessage());
        sendResponse(out, "{\"success\":false,\"error\":\"Internal server error\"}",
            "application/json; charset=utf-8", "500 Internal Server Error");
    }
}

void handleCount(String fullPath, OutputStream out) {
    Map params = parseQueryParams(fullPath);
    String wxId = (String) params.get("wxId");
    String id = (String) params.get("id");
    if (wxId == null || id == null) {
        sendResponse(out, "{\"success\":false,\"error\":\"Missing parameters\"}",
            "application/json; charset=utf-8", "400 Bad Request");
        return;
    }
    int count = readCount(id, wxId);
    String resp = "{\"success\":true,\"count\":" + count + ",\"msg_id\":\"" + id + "\"}";
    sendResponse(out, resp, "application/json; charset=utf-8");
}

void handleMessages(OutputStream out) {
    try {
        if (db == null) initDB();
        Cursor c = db.rawQuery(
            "SELECT m.*, (SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id = m.id) AS cnt " +
            "FROM messages m ORDER BY m.registered_at DESC LIMIT 100", null);
        StringBuilder msgs = new StringBuilder();
        boolean first = true;
        while (c.moveToNext()) {
            if (!first) msgs.append(",");
            first = false;
            String id = c.getString(c.getColumnIndex("id"));
            String wx = c.getString(c.getColumnIndex("wx_id"));
            String ct = c.getString(c.getColumnIndex("content"));
            int cnt = c.getInt(c.getColumnIndex("cnt"));
            msgs.append("{\"id\":\"").append(jsonEscape(id)).append("\",\"wx_id\":\"").append(jsonEscape(wx))
                .append("\",\"content\":").append(jsonEscapeStr(ct)).append(",\"cnt\":").append(cnt).append("}");
        }
        c.close();
        String resp = "{\"success\":true,\"messages\":[" + msgs.toString() + "]}";
        sendResponse(out, resp, "application/json; charset=utf-8");
    } catch (Exception e) {
        sendResponse(out, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}",
            "application/json; charset=utf-8", "500 Internal Server Error");
    }
}

void handleReads(String msgId, OutputStream out) {
    try {
        if (db == null) initDB();
        Cursor c = db.rawQuery("SELECT * FROM reads WHERE msg_id=? ORDER BY read_at DESC, id DESC",
            new String[]{msgId});
        StringBuilder reads = new StringBuilder();
        boolean first = true;
        while (c.moveToNext()) {
            if (!first) reads.append(",");
            first = false;
            String ip = safeGetString(c, "ip_address");
            String country = safeGetString(c, "country");
            String region = safeGetString(c, "region");
            String city = safeGetString(c, "city");
            String isp = safeGetString(c, "isp");
            String loc = safeGetString(c, "loc");
            long rt = safeGetLong(c, "read_at");
            reads.append("{\"ip_address\":\"").append(jsonEscape(ip))
                .append("\",\"country\":\"").append(jsonEscape(country))
                .append("\",\"region\":\"").append(jsonEscape(region))
                .append("\",\"city\":\"").append(jsonEscape(city))
                .append("\",\"isp\":\"").append(jsonEscape(isp))
                .append("\",\"loc\":\"").append(jsonEscape(loc))
                .append("\",\"read_at\":").append(rt).append("}");
        }
        c.close();
        String resp = "{\"success\":true,\"reads\":[" + reads.toString() + "]}";
        sendResponse(out, resp, "application/json; charset=utf-8");
    } catch (Exception e) {
        sendResponse(out, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}",
            "application/json; charset=utf-8", "500 Internal Server Error");
    }
}

String safeGetString(Cursor c, String col) {
    try {
        int idx = c.getColumnIndex(col);
        if (idx < 0) return "";
        String v = c.getString(idx);
        return v != null ? v : "";
    } catch (Exception e) { return ""; }
}

long safeGetLong(Cursor c, String col) {
    try {
        int idx = c.getColumnIndex(col);
        if (idx < 0) return 0;
        return c.getLong(idx);
    } catch (Exception e) { return 0; }
}

Map parseQueryParams(String fullPath) {
    Map params = new HashMap();
    int qIdx = fullPath.indexOf("?");
    if (qIdx == -1) return params;
    String query = fullPath.substring(qIdx + 1);
    query = query.replace("&amp;", "&");
    String[] pairs = query.split("&");
    for (int i = 0; i < pairs.length; i++) {
        int eq = pairs[i].indexOf("=");
        if (eq >= 0) {
            String k = pairs[i].substring(0, eq);
            String v = pairs[i].substring(eq + 1);
            try { v = URLDecoder.decode(v, "UTF-8"); } catch (Exception e) {}
            params.put(k, v);
            if (k.startsWith("amp;")) {
                params.put(k.substring(4), v);
            }
        }
    }
    return params;
}

String jsonEscape(String s) {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c == '"') sb.append("\\\"");
        else if (c == '\\') sb.append("\\\\");
        else if (c == '\n') sb.append("\\n");
        else if (c == '\r') sb.append("\\r");
        else if (c == '\t') sb.append("\\t");
        else if (c < 0x20) sb.append(String.format("\\u%04x", (int)c));
        else sb.append(c);
    }
    return sb.toString();
}

String jsonEscapeStr(String s) {
    if (s == null) return "null";
    return "\"" + jsonEscape(s) + "\"";
}

void sendResponse(OutputStream out, String content, String contentType) {
    sendResponse(out, content, contentType, "200 OK");
}

void sendResponse(OutputStream out, String content, String contentType, String status) {
    try {
        byte[] bytes = content.getBytes("UTF-8");
        out.write(("HTTP/1.1 " + status + "\r\n").getBytes("UTF-8"));
        out.write(("Content-Type: " + contentType + "\r\n").getBytes("UTF-8"));
        out.write(("Content-Length: " + bytes.length + "\r\n").getBytes("UTF-8"));
        out.write("Access-Control-Allow-Origin: *\r\n".getBytes("UTF-8"));
        out.write("Connection: close\r\n\r\n".getBytes("UTF-8"));
        out.write(bytes);
        out.flush();
    } catch (Exception e) {
        writeLog("发送响应失败: " + e.getMessage());
    }
}

String escHtml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
}

String fmtTs(long epoch) {
    try {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(epoch * 1000));
    } catch (Exception e) { return String.valueOf(epoch); }
}

String fmtLoc(String loc) {
    if (loc == null || loc.isEmpty() || !loc.contains(",")) return loc != null ? loc : "";
    try {
        String[] parts = loc.split(",", 2);
        double lat = Double.parseDouble(parts[0]);
        double lon = Double.parseDouble(parts[1]);
        String latDir = lat >= 0 ? "北纬" : "南纬";
        String lonDir = lon >= 0 ? "东经" : "西经";
        return String.format("%s%.4f°, %s%.4f°", latDir, Math.abs(lat), lonDir, Math.abs(lon));
    } catch (Exception e) { return loc; }
}

String buildConsoleHtml() {
    long[] stats = getStats();
    long totalReads = stats[1];
    StringBuilder sb = new StringBuilder();
    sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
    sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">");
    sb.append("<title>已读追踪</title>");
    sb.append("<style>");
    sb.append("*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}");
    sb.append("body{background:#0d1117;font-family:system-ui;color:#e6edf3;padding-bottom:20px}");
    sb.append(".topbar{position:sticky;top:0;background:#0d1117;padding:12px 14px;border-bottom:1px solid #30363d;z-index:10}");
    sb.append(".topbar-inner{display:flex;align-items:center;gap:10px}");
    sb.append(".readcount{flex:1;font-size:14px;color:#8b949e}");
    sb.append(".readcount b{color:#3fb950;font-size:18px}");
    sb.append(".tunnel{font-size:11px;color:#58a6ff;padding:4px 0;word-break:break-all}");
    sb.append(".btn{background:#f85149;color:#fff;border:none;border-radius:8px;padding:10px 14px;font-size:13px;font-weight:600}");
    sb.append(".msglist{padding:10px 14px}");
    sb.append(".msg-item{position:relative;background:#161b22;border:1px solid #30363d;border-radius:12px;margin-bottom:10px;overflow:hidden}");
    sb.append(".msg-body{padding:14px;transition:transform .2s ease}");
    sb.append(".msg-meta{display:flex;justify-content:space-between;margin-bottom:6px}");
    sb.append(".msg-wx{font-size:13px;color:#58a6ff;font-weight:600}");
    sb.append(".msg-time{font-size:11px;color:#484f58}");
    sb.append(".msg-content{font-size:14px;color:#e6edf3;line-height:1.5;word-break:break-all}");
    sb.append(".msg-read{display:inline-block;margin-top:8px;font-size:12px;color:#3fb950;background:rgba(63,185,80,.12);padding:3px 10px;border-radius:12px}");
    sb.append(".del-layer{position:absolute;top:0;right:0;bottom:0;width:80px;background:#f85149;display:flex;align-items:center;justify-content:center;color:#fff;font-size:14px;font-weight:600;transform:translateX(100%);transition:transform .2s ease}");
    sb.append(".msg-item.deleted .msg-body{transform:translateX(-80px)}");
    sb.append(".msg-item.deleted .del-layer{transform:translateX(0)}");
    sb.append(".empty{padding:60px 0;text-align:center;color:#484f58;font-size:14px}");
    sb.append("</style></head><body>");
    sb.append("<div class=\"topbar\"><div class=\"topbar-inner\">");
    sb.append("<div class=\"readcount\">总已读 <b>").append(totalReads).append("</b> 人</div>");
    sb.append("<button class=\"btn\" onclick=\"clearAll()\">清空消息</button>");
    sb.append("</div>");
    if (publicDomain != null && !publicDomain.isEmpty()) {
        sb.append("<div class=\"tunnel\">隧道: ").append(escHtml(publicDomain)).append("</div>");
    }
    sb.append("</div>");
    sb.append("<div class=\"msglist\" id=\"list\">");
    try {
        if (db != null) {
            Cursor c = db.rawQuery(
                "SELECT m.*, (SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id = m.id) AS cnt " +
                "FROM messages m ORDER BY m.registered_at DESC LIMIT 200", null);
            if (c.getCount() == 0) {
                sb.append("<div class=\"empty\">暂无消息<br>发送带已读追踪的消息后会显示在这里</div>");
            }
            while (c.moveToNext()) {
                String id = safeGetString(c, "id");
                String wxId = safeGetString(c, "wx_id");
                String content = safeGetString(c, "content");
                int cnt = safeGetLong(c, "cnt") > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)safeGetLong(c, "cnt");
                long reg = safeGetLong(c, "registered_at");
                sb.append("<div class=\"msg-item\" id=\"msg-").append(escHtml(id)).append("\">");
                sb.append("<div class=\"del-layer\" onclick=\"del('").append(escHtml(id)).append("')\">删除</div>");
                sb.append("<div class=\"msg-body\" onclick=\"openDetail('").append(escHtml(id)).append("')\">");
                sb.append("<div class=\"msg-meta\"><span class=\"msg-wx\">").append(escHtml(wxId)).append("</span>");
                sb.append("<span class=\"msg-time\">").append(fmtTs(reg)).append("</span></div>");
                sb.append("<div class=\"msg-content\">").append(escHtml(content)).append("</div>");
                sb.append("<span class=\"msg-read\">").append(cnt).append(" 人已读</span>");
                sb.append("</div></div>");
            }
            c.close();
        } else {
            sb.append("<div class=\"empty\">数据库未初始化</div>");
        }
    } catch (Exception e) {
        sb.append("<div class=\"empty\">查询失败: ").append(escHtml(e.getMessage())).append("</div>");
    }
    sb.append("</div>");
    sb.append("<script>");
    sb.append("var startX=0,startY=0,cur=null,isSwiping=false;");
    sb.append("document.addEventListener('touchstart',function(e){startX=e.touches[0].clientX;startY=e.touches[0].clientY;cur=findItem(e.target);isSwiping=false});");
    sb.append("function findItem(el){while(el&&el!=document.body){if(el.className&&el.className.indexOf('msg-item')>=0)return el;el=el.parentNode}return null}");
    sb.append("document.addEventListener('touchmove',function(e){");
    sb.append("if(!cur)return;var dx=e.touches[0].clientX-startX;var dy=e.touches[0].clientY-startY;");
    sb.append("if(Math.abs(dx)>Math.abs(dy)&&Math.abs(dx)>10){isSwiping=true;e.preventDefault();");
    sb.append("var body=cur.querySelector('.msg-body');if(body)body.style.transform='translateX('+dx+'px)'}});");
    sb.append("document.addEventListener('touchend',function(e){");
    sb.append("if(!cur)return;var body=cur.querySelector('.msg-body');var dxv=0;");
    sb.append("if(body&&body.style.transform){var m=body.style.transform.match(/-?\\d+/);if(m)dxv=parseInt(m[0])}");
    sb.append("if(isSwiping&&dxv<-40){cur.className='msg-item deleted'}");
    sb.append("else if(isSwiping){cur.className='msg-item'}");
    sb.append("if(body){body.style.transform=''}");
    sb.append("cur=null;isSwiping=false});");
    sb.append("function openDetail(id){location.href='/message/'+id}");
    sb.append("async function del(id){if(!confirm('删除这条消息?'))return;await fetch('/api/delete/'+id,{method:'POST'});location.reload()}");
    sb.append("async function clearAll(){if(!confirm('清空全部消息?不可恢复!'))return;await fetch('/api/delete-all',{method:'POST'});location.reload()}");
    sb.append("</script></body></html>");
    return sb.toString();
}

String buildMessageDetailHtml(String msgId) {
    try {
        if (db == null) initDB();
        Cursor msg = db.rawQuery("SELECT * FROM messages WHERE id=?", new String[]{msgId});
        if (!msg.moveToFirst()) { msg.close(); return null; }
        String id = safeGetString(msg, "id");
        String wxId = safeGetString(msg, "wx_id");
        String content = safeGetString(msg, "content");
        long reg = safeGetLong(msg, "registered_at");
        msg.close();
        Cursor reads = db.rawQuery("SELECT * FROM reads WHERE msg_id=? ORDER BY read_at DESC, id DESC",
            new String[]{id});
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">");
        sb.append("<title>消息详情</title>");
        sb.append("<style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}");
        sb.append("body{background:#0d1117;font-family:system-ui;color:#e6edf3;line-height:1.6;padding-bottom:20px}");
        sb.append(".topbar{position:sticky;top:0;background:#0d1117;padding:12px 14px;border-bottom:1px solid #30363d;z-index:10;display:flex;align-items:center;gap:10px}");
        sb.append(".backbtn{color:#58a6ff;text-decoration:none;font-size:15px;font-weight:600}");
        sb.append(".topbar-title{flex:1;text-align:center;font-size:16px;font-weight:700}");
        sb.append(".delbtn{background:#f85149;color:#fff;border:none;border-radius:8px;padding:8px 14px;font-size:13px;font-weight:600}");
        sb.append(".msg-card{background:#161b22;border:1px solid #30363d;border-radius:12px;padding:16px;margin:12px 14px}");
        sb.append(".msg-wx{font-size:14px;color:#58a6ff;font-weight:700;margin-bottom:6px}");
        sb.append(".msg-time{font-size:11px;color:#484f58;margin-bottom:10px}");
        sb.append(".msg-content{font-size:15px;line-height:1.6;word-break:break-all;background:#0d1117;padding:12px;border-radius:8px;border-left:3px solid #3fb950}");
        sb.append(".read-card{background:#161b22;border:1px solid #30363d;border-radius:12px;padding:14px;margin:0 14px 10px}");
        sb.append(".read-ip{font-family:monospace;color:#58a6ff;font-size:14px;font-weight:600;margin-bottom:6px}");
        sb.append(".read-row{display:flex;font-size:12px;color:#8b949e;margin-bottom:3px}");
        sb.append(".read-row b{color:#e6edf3;font-weight:600;min-width:52px}");
        sb.append(".read-time{font-size:11px;color:#484f58;margin-top:6px}");
        sb.append(".section-title{font-size:14px;color:#8b949e;padding:16px 14px 8px;font-weight:700}");
        sb.append(".empty{padding:40px;text-align:center;color:#484f58;font-size:14px}");
        sb.append("</style></head><body>");
        sb.append("<div class=\"topbar\">");
        sb.append("<a class=\"backbtn\" href=\"/\">← 返回</a>");
        sb.append("<span class=\"topbar-title\">消息详情</span>");
        sb.append("<button class=\"delbtn\" onclick=\"delMsg()\">删除</button>");
        sb.append("</div>");
        sb.append("<div class=\"msg-card\">");
        sb.append("<div class=\"msg-wx\">").append(escHtml(wxId)).append("</div>");
        sb.append("<div class=\"msg-time\">").append(fmtTs(reg)).append("</div>");
        sb.append("<div class=\"msg-content\">").append(escHtml(content)).append("</div>");
        sb.append("</div>");
        int readCount = reads.getCount();
        sb.append("<div class=\"section-title\">已读记录 (").append(readCount).append(")</div>");
        if (readCount == 0) {
            sb.append("<div class=\"empty\">暂无读取记录</div>");
        } else {
            while (reads.moveToNext()) {
                String ip = safeGetString(reads, "ip_address");
                String country = safeGetString(reads, "country");
                String region = safeGetString(reads, "region");
                String city = safeGetString(reads, "city");
                String isp = safeGetString(reads, "isp");
                String loc = fmtLoc(safeGetString(reads, "loc"));
                long rt = safeGetLong(reads, "read_at");
                StringBuilder addr = new StringBuilder();
                if (country.length() > 0) addr.append(country).append(" ");
                if (region.length() > 0) addr.append(region).append(" ");
                if (city.length() > 0) addr.append(city);
                String addrStr = addr.length() > 0 ? addr.toString().trim() : "-";
                sb.append("<div class=\"read-card\">");
                sb.append("<div class=\"read-ip\">").append(escHtml(ip)).append("</div>");
                sb.append("<div class=\"read-row\"><b>地址</b>").append(escHtml(addrStr)).append("</div>");
                sb.append("<div class=\"read-row\"><b>运营商</b>").append(escHtml(isp.length() > 0 ? isp : "-")).append("</div>");
                sb.append("<div class=\"read-row\"><b>经纬度</b>").append(escHtml(loc.length() > 0 ? loc : "-")).append("</div>");
                sb.append("<div class=\"read-time\">").append(fmtTs(rt)).append("</div>");
                sb.append("</div>");
            }
        }
        reads.close();
        sb.append("<script>");
        sb.append("async function delMsg(){if(!confirm('删除这条消息?'))return;await fetch('/api/delete/").append(escHtml(id)).append("',{method:'POST'});location.href='/'}");
        sb.append("</script></body></html>");
        return sb.toString();
    } catch (Exception e) {
        writeLog("详情页异常: " + e.getMessage());
        return null;
    }
}

LinearLayout createCard(Context ctx) {
    LinearLayout layout = new LinearLayout(ctx);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(22, 18, 22, 18);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.setMargins(0, 6, 0, 10);
    layout.setLayoutParams(params);
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(Color.parseColor("#FFFFFF"));
    shape.setCornerRadius(8);
    shape.setStroke(1, Color.parseColor("#DDE3E8"));
    layout.setBackground(shape);
    try { layout.setElevation(2); } catch (Exception e) {}
    return layout;
}

TextView createTitle(Context ctx, String text) {
    TextView tv = new TextView(ctx);
    tv.setText(text);
    tv.setTextSize(15);
    tv.setTextColor(Color.parseColor("#182026"));
    try { tv.getPaint().setFakeBoldText(true); } catch (Exception e) {}
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.setMargins(0, 0, 0, 10);
    tv.setLayoutParams(params);
    return tv;
}

void addBtn(LinearLayout parent, String text, View.OnClickListener listener) {
    Context ctx = getTopActivitySafe();
    Button button = new Button(ctx);
    button.setText(text);
    button.setOnClickListener(listener);
    button.setAllCaps(false);
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    button.setTextSize(14);
    button.setTextColor(Color.parseColor("#1F2A30"));
    button.setGravity(Gravity.CENTER);
    button.setPadding(18, 12, 18, 12);
    GradientDrawable shape = new GradientDrawable();
    shape.setColor(Color.parseColor("#F2F5F7"));
    shape.setCornerRadius(6);
    shape.setStroke(1, Color.parseColor("#D8DEE3"));
    button.setBackground(shape);
    try { button.setElevation(1); } catch (Exception e) {}
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    params.setMargins(0, 6, 0, 6);
    button.setLayoutParams(params);
    parent.addView(button);
}

void copyToClipboard(String text) {
    try {
        Context ctx = getTopActivitySafe();
        Object cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        Class clipClass = Class.forName("android.content.ClipboardManager");
        Class clipDataClass = Class.forName("android.content.ClipData");
        Object clipData = clipDataClass.getMethod("newPlainText", CharSequence.class, CharSequence.class)
            .invoke(null, "tracker", text);
        clipClass.getMethod("setPrimaryClip", clipDataClass).invoke(cm, clipData);
        toastMsg("已复制");
    } catch (Exception e) {
        toastMsg("复制失败");
    }
}

void showDashboard() {
    final Context ctx = getTopActivitySafe();
    if (ctx == null) { toastMsg("无法获取界面"); return; }
    ScrollView scroll = new ScrollView(ctx);
    LinearLayout root = new LinearLayout(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(18, 12, 18, 12);
    scroll.addView(root);
    LinearLayout status = createCard(ctx);
    String tunnelStatus = (publicDomain != null && !publicDomain.isEmpty()) ? "已连接" : "连接中...";
    String consoleAddr = "http://127.0.0.1:" + PORT + "/";
    String tunnelAddr = (publicDomain != null && !publicDomain.isEmpty()) ? publicDomain : "等待隧道...";
    long[] st = getStats();
    TextView statusText = new TextView(ctx);
    statusText.setText("框架: " + FRAMEWORK + "\n端口: " + PORT + "\n隧道: " + tunnelStatus +
        "\n消息数: " + st[0] + "\n已读人数: " + st[1] +
        "\n\n控制台: " + consoleAddr + "\n隧道: " + tunnelAddr);
    statusText.setTextSize(13);
    statusText.setTextColor(Color.parseColor("#36414A"));
    statusText.setLineSpacing(2, 1.0f);
    statusText.setPadding(16, 10, 16, 10);
    status.addView(statusText);
    root.addView(status);
    LinearLayout actions = createCard(ctx);
    actions.addView(createTitle(ctx, "快捷操作"));
    addBtn(actions, "复制控制台地址", new View.OnClickListener() {
        public void onClick(View v) {
            copyToClipboard("http://127.0.0.1:" + PORT + "/");
            toastMsg("控制台地址已复制");
        }
    });
    addBtn(actions, "复制隧道地址", new View.OnClickListener() {
        public void onClick(View v) {
            if (publicDomain != null && !publicDomain.isEmpty()) {
                copyToClipboard(publicDomain);
                toastMsg("隧道地址已复制");
            } else toastMsg("隧道未就绪");
        }
    });
    addBtn(actions, "发送到微信", new View.OnClickListener() {
        public void onClick(View v) {
            if (publicDomain != null && !publicDomain.isEmpty()) {
                String selfId = getSelfWxid();
                if (!selfId.isEmpty()) {
                    sendMsg(selfId, "控制台:http://127.0.0.1:" + PORT + "/\n隧道:" + publicDomain);
                    toastMsg("已发送");
                } else toastMsg("无法获取微信号");
            } else toastMsg("隧道未就绪");
        }
    });
    addBtn(actions, "重连隧道", new View.OnClickListener() {
        public void onClick(View v) {
            stopTunnel();
            publicDomain = "";
            startTunnel();
            toastMsg("正在重连隧道...");
        }
    });
    addBtn(actions, "刷新状态", new View.OnClickListener() {
        public void onClick(View v) {
            if (currentDialog != null) currentDialog.dismiss();
            showDashboard();
        }
    });
    root.addView(actions);
    LinearLayout logCard = createCard(ctx);
    logCard.addView(createTitle(ctx, "实时日志"));
    logView = new TextView(ctx);
    logView.setText(readLogFile(30));
    logView.setTextSize(11);
    logView.setTextColor(Color.parseColor("#2E7D32"));
    logView.setBackgroundColor(Color.parseColor("#F5F5F5"));
    logView.setPadding(12, 10, 12, 10);
    logCard.addView(logView);
    root.addView(logCard);
    AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
    builder.setView(scroll);
    builder.setPositiveButton("关闭", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            stopLogUpdater();
            currentDialog = null;
        }
    });
    currentDialog = builder.create();
    try {
        Window window = currentDialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.15f;
            window.setAttributes(lp);
        }
    } catch (Exception e) {}
    currentDialog.show();
    startLogUpdater();
}

void startLogUpdater() {
    if (uiHandler == null) uiHandler = new Handler(Looper.getMainLooper());
    logUpdater = new Runnable() {
        public void run() {
            try {
                if (logView != null && currentDialog != null && currentDialog.isShowing()) {
                    logView.setText(readLogFile(30));
                }
            } catch (Exception e) {}
            if (uiHandler != null && logUpdater != null) {
                uiHandler.postDelayed(logUpdater, 1500);
            }
        }
    };
    uiHandler.postDelayed(logUpdater, 1500);
}

void stopLogUpdater() {
    if (uiHandler != null && logUpdater != null) {
        uiHandler.removeCallbacks(logUpdater);
    }
    logUpdater = null;
}

void onHandleMsg(Object msgInfoBean) { handleMessage(msgInfoBean); }
void onMessage(Object... args) { handleMessage(args); }
void handleMessage(Object msgObj) {
    try {
        String talker = "", wxid = "", content = "";
        boolean isSend = false;
        try {
            talker = msgObj.getTalker();
            wxid = msgObj.getSendTalker();
            content = String.valueOf(msgObj.getContent());
            try { isSend = msgObj.isSend(); } catch (Exception e) {}
        } catch (Exception e) {
            try {
                Object[] arr = (Object[]) msgObj;
                if (arr.length >= 1) talker = String.valueOf(arr[0]);
                if (arr.length >= 2) content = String.valueOf(arr[1]);
                if (arr.length >= 4) isSend = ((Number) arr[3]).intValue() == 1;
            } catch (Exception e2) {}
        }
        String selfId = getSelfWxid();
        if (!isSend && !selfId.equals(wxid)) return;
        String text = content.trim();
        if (text.equals("#已读服务器") || text.equals("#已读")) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                public void run() { showDashboard(); }
            });
        }
    } catch (Exception ex) {
        writeLog("消息处理异常:" + ex.getMessage());
    }
}

void onLoad() {
    try {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            void uncaughtException(Thread t, Throwable e) {
                try { writeLog("未捕获异常[" + t.getName() + "]: " + e.toString()); } catch (Throwable ex) {}
            }
        });
    } catch (Throwable t) {}
    detectFramework();
    loadConfig();
    initDB();
    startHttp();
    startTunnel();
    writeLog("====模块启动完成 v9.0 框架:" + FRAMEWORK + "====");
    toastMsg("像素已读追踪已启动\n发送 #已读服务器 打开仪表盘");
}

void onUnload() {
    httpRunning = false;
    stopLogUpdater();
    stopTunnel();
    try { if (serverSocket != null) serverSocket.close(); } catch (Exception e) {}
    try { if (db != null) db.close(); } catch (Exception e) {}
    if (currentDialog != null) { try { currentDialog.dismiss(); } catch (Exception e) {} }
    writeLog("模块已关闭");
}

void onUnLoad() { onUnload(); }
