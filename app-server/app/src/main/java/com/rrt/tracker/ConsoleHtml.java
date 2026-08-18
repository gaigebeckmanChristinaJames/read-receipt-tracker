package com.rrt.tracker;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.app.PendingIntent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 控制台 HTML 页面（深色主题，与 Python 版一致）
 */
public class ConsoleHtml {

    private static String ts(long epoch) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(epoch * 1000));
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String fmtLoc(String loc) {
        if (loc == null || loc.isEmpty() || !loc.contains(",")) return loc;
        try {
            String[] parts = loc.split(",", 2);
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            String latDir = lat >= 0 ? "北纬" : "南纬";
            String lonDir = lon >= 0 ? "东经" : "西经";
            return String.format(Locale.getDefault(), "%s%.4f°, %s%.4f°",
                    latDir, Math.abs(lat), lonDir, Math.abs(lon));
        } catch (Exception e) {
            return loc;
        }
    }

    public static String index(Database db) {
        long[] stats = db.stats();
        long tm = stats[0], tr = stats[1];
        Cursor rows = db.messageListAsc(200);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">")
          .append("<title>消息列表</title>")
          .append("<style>")
          .append("*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}")
          .append("body{background:#0d1117;font-family:system-ui;color:#e6edf3;padding-bottom:20px}")
          // 顶部统计栏
          .append(".topbar{position:sticky;top:0;background:#0d1117;padding:12px 14px;border-bottom:1px solid #30363d;z-index:10}")
          .append(".topbar-inner{display:flex;align-items:center;gap:10px}")
          .append(".readcount{flex:1;font-size:14px;color:#8b949e}")
          .append(".readcount b{color:#3fb950;font-size:18px}")
          .append(".btn{background:#f85149;color:#fff;border:none;border-radius:8px;padding:10px 14px;font-size:13px;font-weight:600}")
          // 消息列表
          .append(".msglist{padding:10px 14px}")
          // 消息项：默认只显示内容，删除层隐藏在右侧
          .append(".msg-item{position:relative;background:#161b22;border:1px solid #30363d;border-radius:12px;margin-bottom:10px;overflow:hidden}")
          .append(".msg-body{padding:14px;transition:transform .2s ease}")
          .append(".msg-meta{display:flex;justify-content:space-between;margin-bottom:6px}")
          .append(".msg-wx{font-size:13px;color:#58a6ff;font-weight:600}")
          .append(".msg-time{font-size:11px;color:#484f58}")
          .append(".msg-content{font-size:14px;color:#e6edf3;line-height:1.5;word-break:break-all}")
          .append(".msg-read{display:inline-block;margin-top:8px;font-size:12px;color:#3fb950;background:rgba(63,185,80,.12);padding:3px 10px;border-radius:12px}")
          // 删除按钮：隐藏在右侧（translateX(100%)），左滑时露出
          .append(".del-layer{position:absolute;top:0;right:0;bottom:0;width:80px;background:#f85149;display:flex;align-items:center;justify-content:center;color:#fff;font-size:14px;font-weight:600;transform:translateX(100%);transition:transform .2s ease}")
          .append(".msg-item.deleted .msg-body{transform:translateX(-80px)}")
          .append(".msg-item.deleted .del-layer{transform:translateX(0)}")
          .append(".empty{padding:60px 0;text-align:center;color:#484f58;font-size:14px}")
          .append("</style></head><body>")
          // 顶部栏：总已读数 + 清空按钮
          .append("<div class=\"topbar\"><div class=\"topbar-inner\">")
          .append("<div class=\"readcount\">总已读 <b>").append(tr).append("</b> 次</div>")
          .append("<button class=\"btn\" onclick=\"clearAll()\">清空消息</button>")
          .append("</div></div>")
          .append("<div class=\"msglist\" id=\"list\">");

        if (rows.getCount() == 0) {
            sb.append("<div class=\"empty\">📭 暂无消息</div>");
        } else {
            while (rows.moveToNext()) {
                String id = rows.getString(rows.getColumnIndexOrThrow("id"));
                String wxId = rows.getString(rows.getColumnIndexOrThrow("wx_id"));
                String content = rows.getString(rows.getColumnIndexOrThrow("content"));
                int cnt = rows.getInt(rows.getColumnIndexOrThrow("cnt"));
                long reg = rows.getLong(rows.getColumnIndexOrThrow("registered_at"));
                sb.append("<div class=\"msg-item\" id=\"msg-").append(id).append("\">")
                  // 删除按钮（默认隐藏在右侧）
                  .append("<div class=\"del-layer\" onclick=\"del('").append(id).append("')\" >删除</div>")
                  // 消息主体（点击进详情）
                  .append("<div class=\"msg-body\" onclick=\"openDetail('").append(id).append("')\">")
                  .append("<div class=\"msg-meta\"><span class=\"msg-wx\">").append(esc(wxId)).append("</span>")
                  .append("<span class=\"msg-time\">").append(ts(reg)).append("</span></div>")
                  .append("<div class=\"msg-content\">").append(esc(content)).append("</div>")
                  .append("<span class=\"msg-read\">").append(cnt).append(" 人已读</span>")
                  .append("</div>")
                  .append("</div>");
            }
        }
        rows.close();

        sb.append("</div>")
          .append("<script>")
          // 左滑删除
          .append("var startX=0,startY=0,cur=null,isSwiping=false;")
          .append("document.addEventListener('touchstart',function(e){startX=e.touches[0].clientX;startY=e.touches[0].clientY;cur=findItem(e.target);isSwiping=false});")
          .append("function findItem(el){while(el&&el!=document.body){if(el.className&&el.className.indexOf('msg-item')>=0)return el;el=el.parentNode}return null}")
          .append("document.addEventListener('touchmove',function(e){")
          .append("if(!cur)return;var dx=e.touches[0].clientX-startX;var dy=e.touches[0].clientY-startY;")
          .append("if(Math.abs(dx)>Math.abs(dy)&&Math.abs(dx)>10){isSwiping=true;e.preventDefault();")
          .append("var body=cur.querySelector('.msg-body');if(body)body.style.transform='translateX('+dx+'px)'}});")
          .append("document.addEventListener('touchend',function(e){")
          .append("if(!cur){return}var body=cur.querySelector('.msg-body');var dxv=0;")
          .append("if(body&&body.style.transform){var m=body.style.transform.match(/-?\\d+/);if(m)dxv=parseInt(m[0])}")
          .append("if(isSwiping&&dxv<-40){cur.className='msg-item deleted'}")
          .append("else if(isSwiping){cur.className='msg-item'}")
          .append("if(body){body.style.transform=''}")
          .append("cur=null;isSwiping=false});")
          // 点击删除层外的卡片进详情
          .append("function openDetail(id){location.href='/message/'+id}")
          // 删除与清空
          .append("async function del(id){if(!confirm('删除这条消息?'))return;await fetch('/api/delete/'+id,{method:'POST'});location.reload()}")
          .append("async function clearAll(){if(!confirm('清空全部消息?不可恢复!'))return;await fetch('/api/delete-all',{method:'POST'});location.reload()}")
          .append("</script></body></html>");
        return sb.toString();
    }

    public static String detail(Database db, android.database.Cursor msg) {
        String id = msg.getString(msg.getColumnIndexOrThrow("id"));
        String wxId = msg.getString(msg.getColumnIndexOrThrow("wx_id"));
        String content = msg.getString(msg.getColumnIndexOrThrow("content"));
        long reg = msg.getLong(msg.getColumnIndexOrThrow("registered_at"));
        Cursor reads = db.readList(id);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">")
          .append("<title>消息详情</title>")
          .append("<style>")
          .append("*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}")
          .append("body{background:#0d1117;font-family:system-ui;color:#e6edf3;line-height:1.6;padding-bottom:20px}")
          // 顶部栏
          .append(".topbar{position:sticky;top:0;background:#0d1117;padding:12px 14px;border-bottom:1px solid #30363d;z-index:10;display:flex;align-items:center;gap:10px}")
          .append(".backbtn{color:#58a6ff;text-decoration:none;font-size:15px;font-weight:600}")
          .append(".topbar-title{flex:1;text-align:center;font-size:16px;font-weight:700}")
          .append(".delbtn{background:#f85149;color:#fff;border:none;border-radius:8px;padding:8px 14px;font-size:13px;font-weight:600}")
          // 消息卡片
          .append(".msg-card{background:#161b22;border:1px solid #30363d;border-radius:12px;padding:16px;margin:12px 14px}")
          .append(".msg-wx{font-size:14px;color:#58a6ff;font-weight:700;margin-bottom:6px}")
          .append(".msg-time{font-size:11px;color:#484f58;margin-bottom:10px}")
          .append(".msg-content{font-size:15px;line-height:1.6;word-break:break-all;background:#0d1117;padding:12px;border-radius:8px;border-left:3px solid #3fb950}")
          // 已读记录卡片
          .append(".read-card{background:#161b22;border:1px solid #30363d;border-radius:12px;padding:14px;margin:0 14px 10px}")
          .append(".read-ip{font-family:monospace;color:#58a6ff;font-size:14px;font-weight:600;margin-bottom:6px}")
          .append(".read-row{display:flex;font-size:12px;color:#8b949e;margin-bottom:3px}")
          .append(".read-row b{color:#e6edf3;font-weight:600;min-width:52px}")
          .append(".read-time{font-size:11px;color:#484f58;margin-top:6px}")
          .append(".section-title{font-size:14px;color:#8b949e;padding:16px 14px 8px;font-weight:700}")
          .append(".empty{padding:40px;text-align:center;color:#484f58;font-size:14px}")
          .append("</style></head><body>")
          // 顶部
          .append("<div class=\"topbar\">")
          .append("<a class=\"backbtn\" href=\"/\">← 返回</a>")
          .append("<span class=\"topbar-title\">消息详情</span>")
          .append("<button class=\"delbtn\" onclick=\"delMsg()\">删除</button>")
          .append("</div>")
          // 消息卡片
          .append("<div class=\"msg-card\">")
          .append("<div class=\"msg-wx\">").append(esc(wxId)).append("</div>")
          .append("<div class=\"msg-time\">").append(ts(reg)).append("</div>")
          .append("<div class=\"msg-content\">").append(esc(content)).append("</div>")
          .append("</div>")
          // 已读记录
          .append("<div class=\"section-title\">已读记录 (").append(reads.getCount()).append(")</div>");

        if (reads.getCount() == 0) {
            sb.append("<div class=\"empty\">📭 暂无读取记录</div>");
        } else {
            while (reads.moveToNext()) {
                String reader = reads.getString(reads.getColumnIndexOrThrow("reader_wx_id"));
                if (reader == null || reader.isEmpty()) {
                    reader = reads.getString(reads.getColumnIndexOrThrow("wx_id"));
                }
                String ip = reads.getString(reads.getColumnIndexOrThrow("ip_address"));
                String country = reads.getString(reads.getColumnIndexOrThrow("country"));
                String region = reads.getString(reads.getColumnIndexOrThrow("region"));
                String city = reads.getString(reads.getColumnIndexOrThrow("city"));
                String isp = reads.getString(reads.getColumnIndexOrThrow("isp"));
                String loc = fmtLoc(reads.getString(reads.getColumnIndexOrThrow("loc")));
                long rt = reads.getLong(reads.getColumnIndexOrThrow("read_at"));

                StringBuilder addr = new StringBuilder();
                if (country != null && !country.isEmpty()) addr.append(country).append(" ");
                if (region != null && !region.isEmpty()) addr.append(region).append(" ");
                if (city != null && !city.isEmpty()) addr.append(city);
                if (addr.length() == 0) addr.append("-");

                sb.append("<div class=\"read-card\">")
                  .append("<div class=\"read-ip\">").append(esc(ip)).append("</div>")
                  .append("<div class=\"read-row\"><b>地址</b>").append(esc(addr.toString())).append("</div>")
                  .append("<div class=\"read-row\"><b>运营商</b>").append(esc(isp == null || isp.isEmpty() ? "-" : isp)).append("</div>")
                  .append("<div class=\"read-row\"><b>经纬度</b>").append(esc(loc == null || loc.isEmpty() ? "-" : loc)).append("</div>")
                  .append("<div class=\"read-time\">").append(ts(rt)).append("</div>")
                  .append("</div>");
            }
        }
        reads.close();

        sb.append("<script>")
          .append("async function delMsg(){if(!confirm('删除这条消息?'))return;await fetch('/api/delete/")
          .append(id)
          .append("',{method:'POST'});location.href='/'}")
          .append("</script></body></html>");
        return sb.toString();
    }

}