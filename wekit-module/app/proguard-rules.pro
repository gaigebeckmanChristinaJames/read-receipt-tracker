# ═══════════════════════════════════════════════════════════════════════════
# WeKit 混淆规则 — 脚本引擎(Java/BeanShell插件)依赖保留
# ═══════════════════════════════════════════════════════════════════════════
# 说明:
#   WeKit 的脚本引擎允许运行 Java/BeanShell 插件(如 read-tracker-java)。
#   插件通过反射或类名直接访问 fastjson2 和 okhttp 等库。
#   若这些类被 R8 混淆/裁剪，插件运行时会抛 ClassNotFoundException。
#   因此必须保留以下类的完整名称和成员。
# ═══════════════════════════════════════════════════════════════════════════

# ── 第1层: OkHttp (脚本引擎 HTTP 客户端) ──
# 插件可能直接 new OkHttpClient() / Request.Builder() 等
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# ── 第2层: Okio (OkHttp 底层 IO 库) ──
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okio.**

# ── 第3层: FastJSON2 (脚本引擎 JSON 解析/序列化) ──
# 插件可能直接使用 JSON.parseObject() / JSON.toJSONString() 等
-keep class com.alibaba.fastjson2.** { *; }
-keep interface com.alibaba.fastjson2.** { *; }
-dontwarn com.alibaba.fastjson2.**

# ── 第4层: Kotlin 标准库 (脚本引擎运行时) ──
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── 第5层: 脚本引擎相关类 (防止反射调用被裁剪) ──
-keep class dev.ujhhgtg.wekit.scripting.** { *; }
-keep class dev.ujhhgtg.wekit.features.api.** { *; }

# ── 第6层: Java 插件可能访问的 Android API ──
-keep class android.database.sqlite.** { *; }
-keep class java.net.HttpURLConnection { *; }
