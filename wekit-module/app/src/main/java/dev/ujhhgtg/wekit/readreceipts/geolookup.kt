package dev.ujhhgtg.wekit.readreceipts

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.HashMap

/**
 * IP 定位（三级接口备份 + 中文运营商映射 + 逆地理编码）
 */
data class GeoResult(
    val country: String,
    val region: String,
    val city: String,
    val isp: String,
    val loc: String
)

object GeoLookup {

    private val ISP_CN = HashMap<String, String>().apply {
        put("china mobile", "中国移动")
        put("china mobile communications", "中国移动")
        put("china unicom", "中国联通")
        put("china unicom communications", "中国联通")
        put("china telecom", "中国电信")
        put("china telecom backbone", "中国电信")
        put("chinatelecom", "中国电信")
        put("china broadband", "中国广电")
        put("china education", "教育网")
        put("shanghai mobile", "上海移动")
        put("shanghai telecom", "上海电信")
        put("beijing telecom", "北京电信")
        put("dr peng telecom", "鹏博士")
        put("great wall broadband", "长城宽带")
    }

    private fun cnIsp(isp: String?): String {
        if (isp == null || isp.isEmpty()) return ""
        val key = isp.trim().lowercase()
        return ISP_CN[key] ?: isp
    }

    fun lookup(ip: String?): GeoResult? {
        if (ip == null || ip.isEmpty() || ip == "0.0.0.0" || ip == "127.0.0.1" || ip == "::1")
            return null

        // 接口 1: ip-api.com (中文)
        try {
            val d = httpGet("http://ip-api.com/json/$ip?lang=zh-CN&fields=status,message,country,regionName,city,isp,lat,lon")
            if (d != null && "success" == d.optString("status")) {
                val loc = if (d.has("lat") && d.has("lon")) "${d.optString("lat")},${d.optString("lon")}" else ""
                return GeoResult(
                    country = d.optString("country"),
                    region = d.optString("regionName"),
                    city = d.optString("city"),
                    isp = cnIsp(d.optString("isp")),
                    loc = loc
                )
            }
        } catch (e: Exception) {
            // Ignore
        }

        // 接口 2: ipwho.is (中文)
        try {
            val d = httpGet("https://ipwho.is/$ip?lang=zh-CN")
            if (d != null && d.optBoolean("success", false)) {
                val conn = d.optJSONObject("connection")
                val loc = if (d.has("latitude") && d.has("longitude")) "${d.optString("latitude")},${d.optString("longitude")}" else ""
                return GeoResult(
                    country = d.optString("country"),
                    region = d.optString("region"),
                    city = d.optString("city"),
                    isp = cnIsp(conn?.optString("isp")),
                    loc = loc
                )
            }
        } catch (e: Exception) {
            // Ignore
        }

        // 接口 3: ipinfo.io (兜底)
        try {
            val d = httpGet("https://ipinfo.io/$ip/json")
            if (d != null && d.has("country")) {
                val org = d.optString("org", "")
                val parts = org.split(" ")
                val isp = if (parts.size > 1) org.substring(org.indexOf(' ') + 1) else org
                return GeoResult(
                    country = d.optString("country"),
                    region = d.optString("region"),
                    city = d.optString("city"),
                    isp = cnIsp(isp),
                    loc = d.optString("loc", "")
                )
            }
        } catch (e: Exception) {
            // Ignore
        }

        return null
    }

    fun reverseGeocode(loc: String?): String {
        if (loc == null || loc.isEmpty() || !loc.contains(",")) return ""
        val parts = loc.split(",", limit = 2)
        try {
            val d = httpGet("https://nominatim.openstreetmap.org/reverse?lat=${parts[0]}&lon=${parts[1]}&zoom=14&format=json",
                "read-receipt-tracker/2.3")
            return d?.optString("display_name", "") ?: ""
        } catch (e: Exception) {
            return ""
        }
    }

    private fun httpGet(url: String): JSONObject? {
        return httpGet(url, "read-receipt-tracker/2.3")
    }

    private fun httpGet(url: String, userAgent: String): JSONObject? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", userAgent)
            if (conn.responseCode != 200) return null
            
            val br = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (br.readLine().also { line = it } != null) {
                sb.append(line)
            }
            br.close()
            return JSONObject(sb.toString())
        } catch (e: Exception) {
            return null
        } finally {
            conn?.disconnect()
        }
    }
}