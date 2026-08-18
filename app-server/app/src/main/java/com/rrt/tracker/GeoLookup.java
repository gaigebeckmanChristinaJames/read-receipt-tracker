package com.rrt.tracker;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * IP 定位（三级接口备份 + 中文运营商映射 + 逆地理编码）
 */
public class GeoLookup {

    public static class Geo {
        public final String country, region, city, isp, loc;
        Geo(String c, String r, String ci, String i, String l) {
            country = c; region = r; city = ci; isp = i; loc = l;
        }
    }

    private static final Map<String, String> ISP_CN = new HashMap<>();
    static {
        ISP_CN.put("china mobile", "中国移动");
        ISP_CN.put("china mobile communications", "中国移动");
        ISP_CN.put("china unicom", "中国联通");
        ISP_CN.put("china unicom communications", "中国联通");
        ISP_CN.put("china telecom", "中国电信");
        ISP_CN.put("china telecom backbone", "中国电信");
        ISP_CN.put("chinatelecom", "中国电信");
        ISP_CN.put("china broadband", "中国广电");
        ISP_CN.put("china education", "教育网");
        ISP_CN.put("shanghai mobile", "上海移动");
        ISP_CN.put("shanghai telecom", "上海电信");
        ISP_CN.put("beijing telecom", "北京电信");
        ISP_CN.put("dr peng telecom", "鹏博士");
        ISP_CN.put("great wall broadband", "长城宽带");
    }

    private static String cnIsp(String isp) {
        if (isp == null || isp.isEmpty()) return "";
        String key = isp.trim().toLowerCase();
        String v = ISP_CN.get(key);
        return v != null ? v : isp;
    }

    public static Geo lookup(String ip) {
        if (ip == null || ip.isEmpty() || ip.equals("0.0.0.0") || ip.equals("127.0.0.1") || ip.equals("::1"))
            return null;

        // 接口 1: ip-api.com (中文)
        try {
            JSONObject d = httpGet("http://ip-api.com/json/" + ip +
                "?lang=zh-CN&fields=status,message,country,regionName,city,isp,lat,lon");
            if (d != null && "success".equals(d.optString("status"))) {
                String loc = d.has("lat") && d.has("lon") ? d.opt("lat") + "," + d.opt("lon") : "";
                return new Geo(d.optString("country"), d.optString("regionName"),
                    d.optString("city"), cnIsp(d.optString("isp")), loc);
            }
        } catch (Exception ignored) {}

        // 接口 2: ipwho.is (中文)
        try {
            JSONObject d = httpGet("https://ipwho.is/" + ip + "?lang=zh-CN");
            if (d != null && d.optBoolean("success", false)) {
                JSONObject conn = d.optJSONObject("connection");
                String loc = d.has("latitude") && d.has("longitude") ? d.opt("latitude") + "," + d.opt("longitude") : "";
                return new Geo(d.optString("country"), d.optString("region"),
                    d.optString("city"), cnIsp(conn != null ? conn.optString("isp") : ""), loc);
            }
        } catch (Exception ignored) {}

        // 接口 3: ipinfo.io (兜底)
        try {
            JSONObject d = httpGet("https://ipinfo.io/" + ip + "/json");
            if (d != null && d.has("country")) {
                String org = d.optString("org", "");
                String[] parts = org.split(" ");
                String isp = parts.length > 1 ? org.substring(org.indexOf(' ') + 1) : org;
                return new Geo(d.optString("country"), d.optString("region"),
                    d.optString("city"), cnIsp(isp), d.optString("loc", ""));
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static String reverseGeocode(String loc) {
        if (loc == null || loc.isEmpty() || !loc.contains(",")) return "";
        String[] parts = loc.split(",", 2);
        try {
            JSONObject d = httpGet("https://nominatim.openstreetmap.org/reverse?lat=" +
                parts[0] + "&lon=" + parts[1] + "&zoom=14&format=json",
                "read-receipt-tracker/2.3");
            return d != null ? d.optString("display_name", "") : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static JSONObject httpGet(String url) {
        return httpGet(url, "read-receipt-tracker/2.3");
    }

    private static JSONObject httpGet(String url, String ua) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", ua);
            if (conn.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return new JSONObject(sb.toString());
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
