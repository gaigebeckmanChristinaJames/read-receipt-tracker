import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.security.MessageDigest;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

String CFG_SERVER = "server";
String CFG_PREFIX = "prefix";
String CFG_LAST_ID = "last_id";
String CFG_LAST_WXID = "last_wxid";
String CFG_LAST_CONTENT = "last_content";
String CLIENT_UA = "ZuoMeng-ReadReceipts/1.0";

Method appMsgParseMethod = null;
Method appMsgSendMethod = null;
List appMsgParseCandidates = new ArrayList();
boolean appMsgMethodsResolved = false;

int VIEW_TAG_ID = 0x7E000002;
String COUNT_MARKER = " | 已读 ";
Pattern PIXEL_PATTERN = Pattern.compile(
    "/pixel\\?wxId=([^&\"<\\s]+)(?:&amp;amp;|&amp;|&)id=([0-9a-fA-F]+)"
);
Map activeReceiptViews = Collections.synchronizedMap(new WeakHashMap());
List messageViewHookHandles = new ArrayList();
boolean receiptPolling = false;
Map getItemMethodCache = new ConcurrentHashMap();
Field chattingAdapterField = null;
Map receiptLastQueryAt = new ConcurrentHashMap();
Map receiptCountCache = new ConcurrentHashMap();

String normalizeServer(String value) {
    String server = value == null ? "" : value.trim();
    while (server.endsWith("/")) {
        server = server.substring(0, server.length() - 1);
    }
    return server;
}

String sha256(String wxId, String content, long createTime) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(wxId.getBytes("UTF-8"));
    digest.update((byte) 0);
    digest.update(content.getBytes("UTF-8"));
    digest.update((byte) 0);
    digest.update(String.valueOf(createTime).getBytes("UTF-8"));
    byte[] bytes = digest.digest();
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (int i = 0; i < bytes.length; i++) {
        int value = bytes[i] & 0xff;
        if (value < 16) result.append('0');
        result.append(Integer.toHexString(value));
    }
    return result.toString();
}

String xmlEscape(String value) {
    if (value == null) return "";
    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
}

String buildTrackedXml(String text, String pixelUrl) {
    String escapedText = xmlEscape(text);
    return "<msg><appmsg appid=\"\" sdkver=\"0\">"
        + "<title>" + escapedText + "</title>"
        + "<action>view</action><type>57</type>"
        + "<refermsg><type>49</type><svrid>3081795456970157299</svrid>"
        + "<fromusr>wxid_</fromusr><chatusr>wxid_</chatusr><displayname>\u00a0</displayname>"
        + "<msgsource>&lt;msgsource&gt;&lt;alnode&gt;&lt;fr&gt;2&lt;/fr&gt;&lt;/alnode&gt;&lt;sec_msg_node&gt;&lt;/sec_msg_node&gt;&lt;/msgsource&gt;"
        + "<content>&lt;msg&gt;&lt;appmsg&#x20;appid=&quot;&quot;&#x20;sdkver=&quot;0&quot;&gt;"
        + "&lt;title&gt;当前版本不支持展示该内容，请升级至最新版本。&lt;/title&gt;"
        + "&lt;action&gt;view&lt;/action&gt;&lt;type&gt;51&lt;/type&gt;"
        + "&lt;url&gt;https://support.weixin.qq.com/security/readtemplate?t=w_security_center_website/upgrade&lt;/url&gt;"
        + "&lt;finderFeed&gt;"
        + "&lt;objectId&gt;14667626555619936481&lt;/objectId&gt;"
        + "&lt;objectNonceId&gt;8625307247096037618_0_12_2_1_1748600110424042_f7dd7f2e-3d3e-11f0-adb0-43719c7e1fc7&lt;/objectNonceId&gt;"
        + "&lt;feedType&gt;4&lt;/feedType&gt;"
        + "&lt;username&gt;v2_060000231003b20faec8cae38d1ac4d6c800e435b077830e54ceb941efb42210f69f736d359b@finder&lt;/username&gt;"
        + "&lt;avatar&gt;&lt;![CDATA[https://wx.qlogo.cn/finderhead/ver_1/MiawsaiaO8qpgTJBRD70ROuXN6En8LoKZ266tvlLeRGRHbb7CvcqKrxH19a2mxiafeuCoakYZhsf1u3AYEB3BooKZ6lpCfRVnsfjMfMHC4ibR67iaV6rR4qZ5Irmal16AFpQ0/0]]&gt;&lt;/avatar&gt;"
        + "&lt;desc&gt;(⃔&amp;#x20;*`꒳´&amp;#x20;*&amp;#x20; )⃕↝&lt;/desc&gt;&lt;mediaCount&gt;1&lt;/mediaCount&gt;"
        + "&lt;authIconType&gt;1&lt;/authIconType&gt;"
        + "&lt;authIconUrl&gt;&lt;![CDATA[https://dldir1v6.qq.com/weixin/checkresupdate/auth_icon_level3_2e2f94615c1e4651a25a7e0446f63135.png]]&gt;&lt;/authIconUrl&gt;"
        + "&lt;mediaList&gt;&lt;media&gt;&lt;mediaType&gt;4&lt;/mediaType&gt;"
        + "&lt;url&gt;&lt;![CDATA[http://wxapp.tc.qq.com/251/20302/stodownload?encfilekey=rjD5jyTuFrIpZ2ibE8T7YmwgiahniaXswqz0uUhqGrF2B7C1FqN4dW4RUFEqbMlm05rmPXfSmjgCf3G9ia8ia5kibCH5kxIczTrbCbgAqYUvKicB0IA1udGCuzXpw&amp;hy=SH&amp;idx=1&amp;m=&amp;uzid=7a15c&amp;token=cztXnd9GyrE6cgMDsjj0eZ1MdRB3Eib2ic7rNkGkF4Z9FR5nuld6Yiap9VEugIeCegbHKzjOSMHy5EPTzfChDe3YZJjiaR7aiaFbEzmJ7lsaIjCkSIMxuHkzHibDgX42h1Lq3VySAfoEl06sU0vskxMYumKLA4llQm1WU2hX00ItegJ0c&amp;basedata=CAESBnhXVDE1MRoGeFdUMTExGgZ4V1QxMTIaBnhXVDE1MxoGeFdUMTU2GgZ4V1QxNTEaBnhXVDE1NxoGeFdUMTU4IhgKCgoGeFdUMTEyEAEKCgoGeFdUMTU3EAEqBwiYHRAAGAI&amp;sign=60es22k_sbg7L-LeRKkcDVtXNMBrP54gaTyqCSSs7KRwQm_cI792BPZxaghvauP9954aUbkgAXldv-6hcaDvjA&amp;ctsc=12&amp;extg=10eb900&amp;svrbypass=AAuL%2FQsFAAABAAAAAAC%2B28t6CjV1pwlsLoU5aBAAAADnaHZTnGbFfAj9RgZXfw6Vfkx7FpiL%2B22LVp4HLkn05tij40%2FAsJD%2BPQrMho6FgQX6w1ETaBHqHtM%3D&amp;svrnonce=1748600110]]&gt;&lt;/url&gt;"
        + "&lt;thumbUrl&gt;&lt;![CDATA[" + pixelUrl + "]]&gt;&lt;/thumbUrl&gt;"
        + "&lt;coverUrl&gt;&lt;![CDATA[" + pixelUrl + "]]&gt;&lt;/coverUrl&gt;"
        + "&lt;width&gt;1080.0&lt;/width&gt;&lt;height&gt;1920.0&lt;/height&gt;"
        + "&lt;videoPlayDuration&gt;8&lt;/videoPlayDuration&gt;"
        + "&lt;/media&gt;&lt;/mediaList&gt;&lt;sourceCommentScene&gt;1&lt;/sourceCommentScene&gt;"
        + "&lt;finderShareExtInfo&gt;&lt;![CDATA[{&quot;hasInput&quot;:false,&quot;tabContextId&quot;:&quot;4-1748600105044&quot;,&quot;contextId&quot;:&quot;1-1-17-e669331b7d4243ecae426b3a64ec81b5&quot;,&quot;shareSrcScene&quot;:4}]]&gt;&lt;/finderShareExtInfo&gt;"
        + "&lt;/finderFeed&gt;&lt;/appmsg&gt;&lt;/msg&gt;</content>"
        + "<createtime>1748600455</createtime>"
        + "</refermsg></appmsg></msg>";
}

String buildTrackedXmlStage(String text, String pixelUrl, int stage) {
    String title = xmlEscape(text);
    String inner = "&lt;msg&gt;&lt;appmsg&#x20;appid=&quot;&quot;&#x20;sdkver=&quot;0&quot;&gt;"
        + "&lt;title&gt;当前版本不支持展示该内容，请升级至最新版本。&lt;/title&gt;"
        + "&lt;action&gt;view&lt;/action&gt;&lt;type&gt;51&lt;/type&gt;"
        + "&lt;url&gt;https://support.weixin.qq.com/security/readtemplate?t=w_security_center_website/upgrade&lt;/url&gt;";
    if (stage >= 4) {
        inner += "&lt;finderFeed&gt;"
            + "&lt;objectId&gt;14667626555619936481&lt;/objectId&gt;"
            + "&lt;objectNonceId&gt;8625307247096037618_0_12_2_1_1748600110424042_f7dd7f2e-3d3e-11f0-adb0-43719c7e1fc7&lt;/objectNonceId&gt;"
            + "&lt;feedType&gt;4&lt;/feedType&gt;"
            + "&lt;username&gt;v2_060000231003b20faec8cae38d1ac4d6c800e435b077830e54ceb941efb42210f69f736d359b@finder&lt;/username&gt;"
            + "&lt;avatar&gt;&lt;![CDATA[https://wx.qlogo.cn/finderhead/ver_1/MiawsaiaO8qpgTJBRD70ROuXN6En8LoKZ266tvlLeRGRHbb7CvcqKrxH19a2mxiafeuCoakYZhsf1u3AYEB3BooKZ6lpCfRVnsfjMfMHC4ibR67iaV6rR4qZ5Irmal16AFpQ0/0]]&gt;&lt;/avatar&gt;"
            + "&lt;desc&gt;read receipt&lt;/desc&gt;&lt;mediaCount&gt;1&lt;/mediaCount&gt;";
    }
    if (stage >= 5) {
        inner += "&lt;mediaList&gt;&lt;media&gt;&lt;mediaType&gt;4&lt;/mediaType&gt;"
            + "&lt;url&gt;&lt;![CDATA[https://support.weixin.qq.com/security/readtemplate?t=w_security_center_website/upgrade]]&gt;&lt;/url&gt;"
            + "&lt;thumbUrl&gt;&lt;![CDATA[" + pixelUrl + "]]&gt;&lt;/thumbUrl&gt;"
            + "&lt;coverUrl&gt;&lt;![CDATA[" + pixelUrl + "]]&gt;&lt;/coverUrl&gt;"
            + "&lt;width&gt;1080.0&lt;/width&gt;&lt;height&gt;1920.0&lt;/height&gt;"
            + "&lt;videoPlayDuration&gt;8&lt;/videoPlayDuration&gt;"
            + "&lt;/media&gt;&lt;/mediaList&gt;";
    }
    if (stage >= 4) {
        inner += "&lt;sourceCommentScene&gt;1&lt;/sourceCommentScene&gt;&lt;/finderFeed&gt;";
    }
    inner += "&lt;/appmsg&gt;&lt;/msg&gt;";
    String xml = "<msg><appmsg appid=\"\" sdkver=\"0\"><title>" + title + "</title>"
        + "<action>view</action><type>57</type>";
    if (stage >= 2) {
        xml += "<refermsg><type>49</type><svrid>3081795456970157299</svrid>"
            + "<fromusr>wxid_</fromusr><chatusr>wxid_</chatusr><displayname>\u00a0</displayname>";
        if (stage >= 3) xml += "<content>" + inner + "</content>";
        xml += "<createtime>1748600455</createtime></refermsg>";
    }
    return xml + "</appmsg></msg>";
}

Field findFieldDeep(Class clazz, String name) {
    Class current = clazz;
    while (current != null) {
        try {
            Field field = current.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
        }
        current = current.getSuperclass();
    }
    return null;
}

Object readFieldDeep(Object object, String name) {
    if (object == null) return null;
    try {
        Field field = findFieldDeep(object.getClass(), name);
        return field == null ? null : field.get(object);
    } catch (Throwable ignored) {
        return null;
    }
}

Object firstFieldValueByType(Object object, Class wantedType) {
    if (object == null) return null;
    Class current = object.getClass();
    while (current != null) {
        Field[] fields = current.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                Field field = fields[i];
                if (!wantedType.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(object);
                if (value != null) return value;
            } catch (Throwable ignored) {
            }
        }
        current = current.getSuperclass();
    }
    return null;
}

Object callGetItem(Object adapter, int index) {
    if (adapter == null) return null;
    Method cached = (Method) getItemMethodCache.get(adapter.getClass());
    if (cached != null) {
        try {
            return cached.invoke(adapter, new Object[]{Integer.valueOf(index)});
        } catch (Throwable ignored) {
        }
    }
    Class current = adapter.getClass();
    while (current != null) {
        Method[] methods = current.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class[] types = method.getParameterTypes();
            if (!"getItem".equals(method.getName()) || types.length != 1) continue;
            if (types[0] != Integer.TYPE && types[0] != Integer.class) continue;
            try {
                method.setAccessible(true);
                getItemMethodCache.put(adapter.getClass(), method);
                return method.invoke(adapter, new Object[]{Integer.valueOf(index)});
            } catch (Throwable ignored) {
            }
        }
        current = current.getSuperclass();
    }
    return null;
}

boolean looksLikeMessage(Object object) {
    return object != null && readFieldDeep(object, "field_content") != null;
}

Object findBoundMessage(Object thisObject, Object[] args) {
    for (int i = 0; i < args.length; i++) {
        if (looksLikeMessage(args[i])) return args[i];
    }
    if (args.length < 3 || !(args[2] instanceof Number)) return null;
    int index = ((Number) args[2]).intValue();
    Object direct = callGetItem(thisObject, index);
    if (looksLikeMessage(direct)) return direct;
    if (chattingAdapterField != null) {
        try {
            Object adapter = chattingAdapterField.get(thisObject);
            Object message = callGetItem(adapter, index);
            if (looksLikeMessage(message)) return message;
        } catch (Throwable ignored) {
        }
    }
    Class current = thisObject == null ? null : thisObject.getClass();
    while (current != null) {
        Field[] fields = current.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            try {
                Field field = fields[i];
                if (field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                Object child = field.get(thisObject);
                Object message = callGetItem(child, index);
                if (looksLikeMessage(message)) {
                    chattingAdapterField = field;
                    return message;
                }
            } catch (Throwable ignored) {
            }
        }
        current = current.getSuperclass();
    }
    return null;
}

TextView findTimeTextView(View view) {
    if (view == null) return null;
    try {
        Object tag = view.getTag();
        Object value = readFieldDeep(tag, "timeTV");
        if (value instanceof TextView) return (TextView) value;
    } catch (Throwable ignored) {
    }
    return findTimeTextViewInTree(view, 0);
}

TextView findTimeTextViewInTree(View view, int depth) {
    if (view == null || depth > 12) return null;
    if (view instanceof TextView) {
        TextView textView = (TextView) view;
        try {
            int id = textView.getId();
            if (id != View.NO_ID) {
                String name = textView.getResources().getResourceEntryName(id);
                if (name != null && name.toLowerCase().contains("time")) return textView;
            }
        } catch (Throwable ignored) {
        }
        String text = String.valueOf(textView.getText());
        if (text.matches(".*\\d{1,2}:\\d{2}(:\\d{2})?.*")) return textView;
    }
    if (view instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) view;
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            TextView found = findTimeTextViewInTree(group.getChildAt(i), depth + 1);
            if (found != null) return found;
        }
    }
    return null;
}

void applyReceiptCount(final TextView timeView, final String id, final int count) {
    if (timeView == null) return;
    timeView.post(new Runnable() {
        public void run() {
            Object tag = timeView.getTag(VIEW_TAG_ID);
            if (!id.equals(String.valueOf(tag))) return;
            String current = String.valueOf(timeView.getText());
            int marker = current.indexOf(COUNT_MARKER);
            String base = marker >= 0 ? current.substring(0, marker) : current;
            timeView.setText(base + COUNT_MARKER + count + " 人");
            timeView.setVisibility(View.VISIBLE);
        }
    });
}

void applyReceiptCountToVisibleViews(String id, int count) {
    Object[] entries;
    synchronized (activeReceiptViews) {
        entries = activeReceiptViews.entrySet().toArray();
    }
    for (int i = 0; i < entries.length; i++) {
        Map.Entry entry = (Map.Entry) entries[i];
        if (!(entry.getKey() instanceof TextView) || !(entry.getValue() instanceof Map)) continue;
        Map reference = (Map) entry.getValue();
        if (id.equals(String.valueOf(reference.get("id")))) {
            applyReceiptCount((TextView) entry.getKey(), id, count);
        }
    }
}

void fetchReceiptCount(final TextView timeView, final String wxId, final String id) {
    String server = normalizeServer(getString(CFG_SERVER, ""));
    if (server.length() == 0) return;
    long now = System.currentTimeMillis();
    Object previousRaw = receiptLastQueryAt.get(id);
    if (previousRaw instanceof Number && now - ((Number) previousRaw).longValue() < 4500L) return;
    receiptLastQueryAt.put(id, Long.valueOf(now));
    try {
        String url = server + "/count?wxId=" + URLEncoder.encode(wxId, "UTF-8") + "&id=" + id;
        get(url, new HashMap(), 10L, new java.util.function.Consumer() {
            public void accept(Object response) {
                if (response == null) return;
                try {
                    int count = new JSONObject(String.valueOf(response)).optInt("count", 0);
                    receiptCountCache.put(id, Integer.valueOf(count));
                    applyReceiptCountToVisibleViews(id, count);
                } catch (Throwable error) {
                    log("解析气泡已读数失败: " + error);
                }
            }
        });
    } catch (Throwable error) {
        log("查询气泡已读数失败: " + error);
    }
}

void handleMessageViewBound(Object rawParam) {
    try {
        XC_MethodHook.MethodHookParam param = (XC_MethodHook.MethodHookParam) rawParam;
        Object[] args = param.args;
        if (args == null || args.length < 3) return;
        Object message = findBoundMessage(param.thisObject, args);
        if (message == null) return;
        Object isSendValue = readFieldDeep(message, "field_isSend");
        if (!(isSendValue instanceof Number) || ((Number) isSendValue).intValue() == 0) return;
        String content = String.valueOf(readFieldDeep(message, "field_content"));
        Matcher matcher = PIXEL_PATTERN.matcher(content);
        if (!matcher.find()) return;
        String wxId = matcher.group(1);
        String id = matcher.group(2);
        Object holder = args[0];
        Object itemViewValue = readFieldDeep(holder, "itemView");
        View view = itemViewValue instanceof View
            ? (View) itemViewValue
            : (holder instanceof View ? (View) holder : (View) firstFieldValueByType(holder, View.class));
        TextView timeView = findTimeTextView(view);
        if (timeView == null) return;
        timeView.setTag(VIEW_TAG_ID, id);
        HashMap reference = new HashMap();
        reference.put("wxId", wxId);
        reference.put("id", id);
        activeReceiptViews.put(timeView, reference);
        Object cachedCount = receiptCountCache.get(id);
        if (cachedCount instanceof Number) {
            applyReceiptCount(timeView, id, ((Number) cachedCount).intValue());
        }
        fetchReceiptCount(timeView, wxId, id);
    } catch (Throwable error) {
        log("处理消息气泡绑定失败: " + error);
    }
}

void installMessageViewHook() {
    try {
        Object candidates = findMemberList(new String[]{
            "MicroMsg.MvvmChattingItem", "[onBindView]"
        });
        if (!(candidates instanceof List)) {
            log("未找到消息 View 绑定方法");
            return;
        }
        List list = (List) candidates;
        for (int i = 0; i < list.size(); i++) {
            Object member = list.get(i);
            if (!(member instanceof Method)) continue;
            Method method = (Method) member;
            if (method.getParameterTypes().length < 3) continue;
            Object handle = hookAfter(method, new java.util.function.Consumer() {
                public void accept(Object param) {
                    handleMessageViewBound(param);
                }
            });
            messageViewHookHandles.add(handle);
            log("已 Hook 消息 View 绑定方法: " + method);
        }
    } catch (Throwable error) {
        log("安装消息 View Hook 失败: " + error);
    }
}

void pollVisibleReceiptViews() {
    if (!receiptPolling) return;
    try {
        Object[] entries;
        synchronized (activeReceiptViews) {
            entries = activeReceiptViews.entrySet().toArray();
        }
        HashMap unique = new HashMap();
        for (int i = 0; i < entries.length; i++) {
            Map.Entry entry = (Map.Entry) entries[i];
            if (!(entry.getKey() instanceof TextView) || !(entry.getValue() instanceof Map)) continue;
            Map reference = (Map) entry.getValue();
            unique.put(String.valueOf(reference.get("id")), entry);
        }
        Object[] uniqueEntries = unique.values().toArray();
        for (int i = 0; i < uniqueEntries.length; i++) {
            Map.Entry entry = (Map.Entry) uniqueEntries[i];
            Map reference = (Map) entry.getValue();
            fetchReceiptCount(
                (TextView) entry.getKey(),
                String.valueOf(reference.get("wxId")),
                String.valueOf(reference.get("id"))
            );
        }
    } catch (Throwable error) {
        log("轮询可见消息已读数失败: " + error);
    }
    delay(5000L, new Runnable() {
        public void run() {
            pollVisibleReceiptViews();
        }
    });
}

Object parseAppMsgWithCandidates(String xml) {
    for (int i = 0; i < appMsgParseCandidates.size(); i++) {
        Method candidate = (Method) appMsgParseCandidates.get(i);
        try {
            Object parsed = candidate.invoke(null, new Object[]{xml});
            if (parsed != null) {
                appMsgParseMethod = candidate;
                return parsed;
            }
        } catch (Throwable error) {
            log("AppMsg XML 解析异常: " + candidate + " error=" + error);
        }
    }
    return null;
}

Object diagnoseAndParseTrackedXml(String text, String pixelUrl) {
    Object best = null;
    int bestStage = 0;
    for (int stage = 1; stage <= 5; stage++) {
        String probeXml = buildTrackedXmlStage(text, pixelUrl, stage);
        Object parsed = parseAppMsgWithCandidates(probeXml);
        log("追踪 XML 分层探针: stage=" + stage + " parsed=" + (parsed != null));
        if (parsed != null) {
            best = parsed;
            bestStage = stage;
        }
    }
    log("追踪 XML 分层探针最高可用层级: " + bestStage);
    return bestStage == 5 ? best : null;
}

void resolveWeKitAppMsgMethods() {
    if (appMsgMethodsResolved) return;
    try {
        Object sendCandidates = findMemberList(new String[]{"sendAppMsg", "attachFilePath"});
        if (sendCandidates instanceof List) {
            List list = (List) sendCandidates;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (!(item instanceof Method)) continue;
                Method method = (Method) item;
                Class[] types = method.getParameterTypes();
                if (Modifier.isStatic(method.getModifiers())
                        && types.length == 6
                        && types[1] == String.class
                        && types[2] == String.class
                        && types[3] == String.class
                        && types[4] == String.class
                        && types[5] == byte[].class) {
                    method.setAccessible(true);
                    appMsgSendMethod = method;
                    break;
                }
            }
        }
        if (appMsgSendMethod != null) {
            Class contentClass = appMsgSendMethod.getParameterTypes()[0];
            ArrayList contentClasses = new ArrayList();
            try {
                Object exactClasses = findClassList(new String[]{
                    "<appmsg appid=\"", "parse amessage xml failed"
                });
                if (exactClasses instanceof List) {
                    List list = (List) exactClasses;
                    for (int i = 0; i < list.size(); i++) {
                        Object item = list.get(i);
                        if (item instanceof Class && !contentClasses.contains(item)) {
                            contentClasses.add(item);
                        }
                    }
                }
            } catch (Throwable error) {
                log("精确定位 AppMsg 内容类失败: " + error);
            }
            if (!contentClasses.contains(contentClass)) contentClasses.add(contentClass);
            for (int classIndex = 0; classIndex < contentClasses.size(); classIndex++) {
                Class candidateClass = (Class) contentClasses.get(classIndex);
                if (candidateClass != contentClass) continue;
                Method[] methods = candidateClass.getDeclaredMethods();
                for (int i = 0; i < methods.length; i++) {
                    Method method = methods[i];
                    Class[] types = method.getParameterTypes();
                    if (Modifier.isStatic(method.getModifiers())
                            && types.length == 1
                            && types[0] == String.class
                            && method.getReturnType() == contentClass) {
                        method.setAccessible(true);
                        appMsgParseCandidates.add(method);
                    }
                }
            }
        }
        appMsgMethodsResolved = !appMsgParseCandidates.isEmpty() && appMsgSendMethod != null;
        log("AppMsg 链路: parseCandidates=" + appMsgParseCandidates.size() + " send=" + appMsgSendMethod);
    } catch (Throwable error) {
        log("定位 AppMsg 链路失败: " + error);
    }
}

boolean sendTrackedAppMsg(String talker, String xml, String text, String pixelUrl) {
    resolveWeKitAppMsgMethods();
    if (!appMsgParseCandidates.isEmpty() && appMsgSendMethod != null) {
        try {
            Object contentObj = null;
            ArrayList parseInputs = new ArrayList();
            parseInputs.add(xml);
            int appMsgStart = xml.indexOf("<appmsg");
            int appMsgEnd = xml.lastIndexOf("</appmsg>");
            if (appMsgStart >= 0 && appMsgEnd > appMsgStart) {
                parseInputs.add(xml.substring(appMsgStart, appMsgEnd + 9));
            }
            if (appMsgParseMethod != null) {
                for (int inputIndex = 0; inputIndex < parseInputs.size(); inputIndex++) {
                    try {
                        contentObj = appMsgParseMethod.invoke(
                            null, new Object[]{parseInputs.get(inputIndex)}
                        );
                        if (contentObj != null) break;
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (contentObj == null) {
                contentObj = diagnoseAndParseTrackedXml(text, pixelUrl);
            }
            if (contentObj != null) {
                String title = "";
                try {
                    int start = xml.indexOf("<title>");
                    int end = xml.indexOf("</title>", start + 7);
                    if (start >= 0 && end > start) title = xml.substring(start + 7, end);
                } catch (Throwable ignored) {
                }
                appMsgSendMethod.invoke(
                    null,
                    new Object[]{contentObj, "", title, talker, null, null}
                );
                return true;
            }
            log("全部 AppMsg XML 解析候选均返回 null");
        } catch (Throwable error) {
            log("AppMsg 链路发送失败: " + error);
        }
    }
    try {
        sendXmlMsg(talker, xml);
        log("已回退到 Hchat sendXmlMsg");
        return true;
    } catch (Throwable error) {
        log("Hchat sendXmlMsg 发送失败: " + error);
        return false;
    }
}

void registerAndSend(final String talker, final String content) {
    final String server = normalizeServer(getString(CFG_SERVER, ""));
    if (server.length() == 0) {
        toast("请先在插件设置中填写服务器地址");
        return;
    }
    final String wxId = getLoginWxid();
    if (wxId == null || wxId.length() == 0) {
        toast("无法获取当前账号 wxId");
        return;
    }
    final long createTime = System.currentTimeMillis();
    final String id;
    try {
        id = sha256(wxId, content, createTime);
    } catch (Throwable error) {
        log("生成消息 ID 失败: " + error);
        toast("生成消息 ID 失败");
        return;
    }
    try {
        String pixelUrl = server + "/pixel?wxId="
            + URLEncoder.encode(wxId, "UTF-8") + "&amp;id=" + id;
        if (!sendTrackedAppMsg(talker, buildTrackedXml(content, pixelUrl), content, pixelUrl)) {
            toast("追踪消息发送失败");
            return;
        }
        putString(CFG_LAST_ID, id);
        putString(CFG_LAST_WXID, wxId);
        putString(CFG_LAST_CONTENT, content);
        toast("已发送附带已读追踪的消息");
    } catch (Throwable error) {
        log("发送追踪消息失败: " + error);
        toast("追踪消息发送失败");
        return;
    }
    new Thread(new Runnable() {
        public void run() {
            String response = postRegisterJson(server, wxId, content, createTime);
            if (response == null) {
                log("注册消息失败: 服务器无响应");
                return;
            }
            try {
                JSONObject json = new JSONObject(String.valueOf(response));
                String serverId = json.optString("id", "");
                if (serverId.length() > 0 && !id.equals(serverId)) {
                    log("注册消息失败: 客户端与服务器生成的 ID 不一致");
                }
            } catch (Throwable error) {
                log("解析注册响应失败: " + response + " error=" + error);
            }
        }
    }, "ZuoMeng-register").start();
}

String postRegisterJson(String server, String wxId, String content, long createTime) {
    HttpURLConnection connection = null;
    try {
        JSONObject payload = new JSONObject();
        payload.put("wxId", wxId);
        payload.put("content", content);
        payload.put("createTime", Long.valueOf(createTime));
        byte[] body = payload.toString().getBytes("UTF-8");
        connection = (HttpURLConnection) new URL(server + "/register").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", CLIENT_UA);
        connection.setFixedLengthStreamingMode(body.length);
        OutputStream output = connection.getOutputStream();
        try {
            output.write(body);
            output.flush();
        } finally {
            output.close();
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
            ? connection.getInputStream() : connection.getErrorStream();
        String response = readUtf8(stream);
        if (status < 200 || status >= 300) {
            log("注册消息失败: HTTP " + status + " body=" + response);
            return null;
        }
        return response;
    } catch (Throwable error) {
        log("注册消息请求失败: " + error);
        return null;
    } finally {
        try {
            if (connection != null) connection.disconnect();
        } catch (Throwable ignored) {
        }
    }
}

String readUtf8(InputStream stream) throws Exception {
    if (stream == null) return "";
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
    try {
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line);
        return result.toString();
    } finally {
        reader.close();
    }
}

void queryLastCount() {
    String server = normalizeServer(getString(CFG_SERVER, ""));
    String wxId = getString(CFG_LAST_WXID, "");
    String id = getString(CFG_LAST_ID, "");
    final String content = getString(CFG_LAST_CONTENT, "");
    final String talker = getTargetTalker();
    if (server.length() == 0) {
        toast("请先设置服务器地址");
        return;
    }
    if (wxId.length() == 0 || id.length() == 0) {
        toast("还没有通过本插件发送追踪消息");
        return;
    }
    try {
        String url = server + "/count?wxId=" + URLEncoder.encode(wxId, "UTF-8") + "&id=" + id;
        get(url, new HashMap(), 10L, new java.util.function.Consumer() {
            public void accept(Object response) {
                if (response == null) {
                    toast("查询失败，请检查网络");
                    return;
                }
                try {
                    int count = new JSONObject(String.valueOf(response)).optInt("count", 0);
                    String message = "最近消息「" + content + "」已读 " + count + " 人";
                    try {
                        insertSystemMsg(talker, message, System.currentTimeMillis());
                    } catch (Throwable ignored) {
                        toast(message);
                    }
                } catch (Throwable error) {
                    log("解析已读数失败: " + response + " error=" + error);
                    toast("查询失败：服务器响应格式错误");
                }
            }
        });
    } catch (Throwable error) {
        log("查询已读数失败: " + error);
        toast("查询失败");
    }
}

void openDashboard() {
    String server = normalizeServer(getString(CFG_SERVER, ""));
    if (server.length() == 0) {
        toast("请先设置服务器地址");
        return;
    }
    try {
        Activity activity = getTopActivity();
        if (activity == null) {
            toast("当前没有可用界面");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(server));
        activity.startActivity(intent);
    } catch (Throwable error) {
        log("打开管理面板失败: " + error);
        toast("无法打开管理面板");
    }
}

EditText addSettingField(LinearLayout root, Activity activity, String label, String value, int inputType) {
    TextView title = new TextView(activity);
    title.setText(label);
    title.setPadding(0, 18, 0, 4);
    root.addView(title);
    EditText field = new EditText(activity);
    field.setSingleLine(true);
    field.setText(value);
    field.setInputType(inputType);
    root.addView(field, new LinearLayout.LayoutParams(-1, -2));
    return field;
}

void openSettings() {
    final Activity activity = getTopActivity();
    if (activity == null) {
        toast("当前没有可用界面");
        return;
    }
    int padding = (int) (20.0f * activity.getResources().getDisplayMetrics().density);
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(padding, 0, padding, 0);
    final EditText serverField = addSettingField(
        root, activity, "服务器地址", getString(CFG_SERVER, ""),
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
    );
    final EditText prefixField = addSettingField(
        root, activity, "触发前缀", getString(CFG_PREFIX, "#"),
        InputType.TYPE_CLASS_TEXT
    );
    final AlertDialog dialog = new AlertDialog.Builder(activity)
        .setTitle("已读追踪设置")
        .setView(root)
        .setNegativeButton("取消", null)
        .setNeutralButton("管理面板", null)
        .setPositiveButton("保存", null)
        .create();
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
        public void onShow(DialogInterface ignored) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    String server = normalizeServer(serverField.getText().toString());
                    String prefix = prefixField.getText().toString();
                    if (server.length() == 0 || (!server.startsWith("http://") && !server.startsWith("https://"))) {
                        serverField.setError("请输入以 http:// 或 https:// 开头的地址");
                        return;
                    }
                    putString(CFG_SERVER, server);
                    putString(CFG_PREFIX, prefix);
                    dialog.dismiss();
                    toast("设置已保存");
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    openDashboard();
                }
            });
        }
    });
    dialog.show();
}

boolean onClickSendBtn(String text) {
    if (text == null) return false;
    if ("/已读".equals(text) || "/rr".equalsIgnoreCase(text)) {
        queryLastCount();
        return true;
    }
    if ("/已读面板".equals(text) || "/rr-panel".equalsIgnoreCase(text)) {
        openDashboard();
        return true;
    }
    String prefix = getString(CFG_PREFIX, "#");
    if (!text.startsWith(prefix)) return false;
    String content = text.substring(prefix.length());
    if (content.trim().length() == 0) {
        toast("追踪消息内容不能为空");
        return true;
    }
    String talker = getTargetTalker();
    if (talker == null || talker.length() == 0) {
        toast("无法获取当前会话");
        return true;
    }
    registerAndSend(talker, content);
    return true;
}

void onLoad() {
    log("已读追踪客户端已加载");
    resolveWeKitAppMsgMethods();
    installMessageViewHook();
    receiptPolling = true;
    pollVisibleReceiptViews();
}

void onUnload() {
    receiptPolling = false;
    for (int i = 0; i < messageViewHookHandles.size(); i++) {
        try {
            unhook(messageViewHookHandles.get(i));
        } catch (Throwable ignored) {
        }
    }
    messageViewHookHandles.clear();
    activeReceiptViews.clear();
    receiptLastQueryAt.clear();
    receiptCountCache.clear();
    log("已读追踪客户端已卸载");
}
