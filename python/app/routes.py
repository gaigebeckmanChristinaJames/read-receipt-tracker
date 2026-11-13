"""路由模块 — 优化并发、防阻塞版 + 登录鉴权"""
import hmac
import os
import secrets
import time
from datetime import datetime, timedelta
from functools import wraps
from io import BytesIO

from flask import (Flask, current_app, g, jsonify, redirect, render_template,
                   request, send_file, send_from_directory, make_response, 
                   session, url_for, abort)

from .database import atomic_write, get_db
from .utils import generate_message_id, get_client_ip, lookup_ip_location, reverse_geocode

# 1x1 透明 GIF
TRANSPARENT_GIF = bytes([
    0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
    0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0x21,
    0xF9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00,
    0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44,
    0x01, 0x00, 0x3B,
])

# pixel 端点的无阻塞写队列（高并发兜底）
_pixel_queue = []  # (msg_id, wx_id, ip, ua)

# 限流计数器（进程内；原实现存在 g 里，每请求重置，形同虚设）
_rl_store = {}


def _ts2date(ts):
    try:
        return datetime.fromtimestamp(int(ts)).strftime("%Y-%m-%d %H:%M:%S")
    except Exception:
        return str(ts)


def _fmt_loc(loc):
    """经纬度格式化: '31.2222,121.4581' -> '北纬31.2222°, 东经121.4581°'"""
    if not loc or "," not in loc:
        return loc
    try:
        lat_s, lon_s = loc.split(",", 1)
        lat = float(lat_s)
        lon = float(lon_s)
        lat_dir = "北纬" if lat >= 0 else "南纬"
        lon_dir = "东经" if lon >= 0 else "西经"
        return f"{lat_dir}{abs(lat):.4f}°, {lon_dir}{abs(lon):.4f}°"
    except Exception:
        return loc


def _wants_json():
    """判断请求是否期望 JSON（决定未授权时返回 401 还是跳转登录页）"""
    if request.path.startswith("/api/") or request.path == "/batch-status":
        return True
    if request.args.get("json") == "1":
        return True
    am = request.accept_mimetypes
    return am["application/json"] > am["text/html"]


def _admin_pwd():
    """登录密码：必须有 API_KEY 才启用鉴权，ADMIN_PASSWORD 是可选覆盖"""
    ak = current_app.config.get("API_KEY", "")
    if not ak:
        return ""  # 没有 API_KEY → 不启用鉴权，即使配了 ADMIN_PASSWORD 也无效
    return current_app.config.get("ADMIN_PASSWORD") or ak


def require_admin(f):
    """后台鉴权：会话 Cookie 或 API Key 二选一"""
    @wraps(f)
    def d(*a, **kw):
        # 1. 已登录会话直接放行
        if session.get("authed"):
            return f(*a, **kw)
        # 2. 未配置 API_KEY：保持开放（ADMIN_PASSWORD 单独存在不启用鉴权）
        ak = current_app.config.get("API_KEY", "")
        if not ak:
            return f(*a, **kw)
        # 3. 检查 API Key（Header 或 Query）
        rk = request.headers.get("X-API-Key") or request.args.get("api_key", "")
        if rk and hmac.compare_digest(rk, ak):
            return f(*a, **kw)
        # 4. 未通过鉴权
        if _wants_json():
            return jsonify({"error": "Unauthorized"}), 401
        return redirect(url_for("login", next=request.path))
    return d


def rate_limit(f):
    @wraps(f)
    def d(*a, **kw):
        lim = current_app.config.get("RATE_LIMIT_PER_MINUTE", 60)
        if lim <= 0:
            return f(*a, **kw)
        ip = get_client_ip()
        key = f.__name__ + "|" + ip
        now = int(time.time())
        w = _rl_store.get(key)
        if w is None or now - w[0] > 60:
            _rl_store[key] = (now, 1)
        else:
            c = w[1] + 1
            _rl_store[key] = (w[0], c)
            if c > lim:
                return jsonify({"error": "Too many requests"}), 429
        if len(_rl_store) > 10000:  # 轻量清理
            for k in [k for k, v in _rl_store.items() if now - v[0] > 120]:
                _rl_store.pop(k, None)
        return f(*a, **kw)
    return d


def _ensure_secret(app):
    """保证有持久化的 SECRET_KEY（会话签名用）：config > 环境变量 > instance/.secret_key"""
    if app.secret_key:
        return
    sk = os.environ.get("SECRET_KEY", "")
    if not sk:
        p = os.path.join(app.instance_path, ".secret_key")
        try:
            os.makedirs(app.instance_path, exist_ok=True)
            if os.path.exists(p):
                with open(p, "r", encoding="utf-8") as fp:
                    sk = fp.read().strip()
            if not sk:
                sk = secrets.token_hex(32)
                with open(p, "w", encoding="utf-8") as fp:
                    fp.write(sk)
                try:
                    os.chmod(p, 0o600)
                except OSError:
                    pass
        except OSError:
            sk = secrets.token_hex(32)  # 兜底：进程重启后会话失效而已
    app.secret_key = sk


def register_routes(app):
    app.template_filter("ts2date")(_ts2date)
    app.template_filter("fmtloc")(_fmt_loc)

    # ---- 会话配置 ----
    _ensure_secret(app)
    app.config.setdefault("SESSION_COOKIE_SAMESITE", "Lax")
    app.config.setdefault("SESSION_COOKIE_HTTPONLY", True)
    app.permanent_session_lifetime = timedelta(days=int(app.config.get("SESSION_DAYS", 7)))

    @app.route("/health")
    @rate_limit
    def health():
        return jsonify({"status": "ok", "service": "read-receipt-tracker"})

    @app.route("/login", methods=["GET", "POST"])
    @rate_limit
    def login():
        pwd = _admin_pwd()
        if not pwd:                       # 未配置密码：无需登录
            return redirect(url_for("index"))
        if session.get("authed"):
            return redirect(url_for("index"))
        err = None
        if request.method == "POST":
            pw = request.form.get("password", "")
            if hmac.compare_digest(pw, pwd):
                session.permanent = True
                session["authed"] = True
                nxt = request.args.get("next") or "/"
                if not nxt.startswith("/") or nxt.startswith("//") or "\\" in nxt:
                    nxt = "/"
                return redirect(nxt)
            err = "密码错误，请重试"
            time.sleep(0.6)               # 简单抬高爆破成本
        return render_template("login.html", error=err)

    @app.route("/logout", methods=["POST"])
    def logout():
        session.clear()
        return redirect(url_for("login"))

    @app.route("/register", methods=["POST"])
    @rate_limit
    def register():
        # 注册新消息，返回 pixel_url
        try:
            data = request.get_json(force=True)
        except Exception:
            return jsonify({"error": "Invalid JSON"}), 400

        wx = (data.get("wxId", "") or "").strip()
        c = data.get("content", "") or ""
        ct = data.get("createTime", int(time.time() * 1000))

        if not wx:
            return jsonify({"error": "wxId required"}), 400
        if len(c) > 50000:
            return jsonify({"error": "content too long"}), 400

        mid = generate_message_id(wx, c, ct)

        db = get_db()
        try:
            atomic_write(db,
                "INSERT OR IGNORE INTO messages(id,wx_id,content,create_time) "
                "VALUES(?,?,?,?)",
                (mid, wx, c, ct))
        except Exception as e:
            return jsonify({"error": str(e)}), 500

        pu = f"{request.host_url.rstrip('/')}/pixel?wxId={wx}&id={mid}"
        return jsonify({"success": True, "id": mid, "wxId": wx, "pixel_url": pu})

    @app.route("/pixel")
    @rate_limit
    def pixel():
        # 返回 1x1 透明 GIF 并记录已读（非阻塞：先返回，失败进队列）
        wx = request.args.get("wxId", "")
        mid = request.args.get("id", "")
        # 微信图片请求拿不到对方真实微信号，访客ID固定为「未知访客」
        reader = "未知访客"
        if not wx or not mid:
            return send_file(BytesIO(TRANSPARENT_GIF), mimetype="image/gif")

        ip = get_client_ip()
        ua = (request.headers.get("User-Agent", "") or "")[:500]

        # IP 定位（中文优先，失败静默降级）
        geo = lookup_ip_location(ip)
        country = geo.get("country", "") if geo else ""
        region = geo.get("region", "") if geo else ""
        city = geo.get("city", "") if geo else ""
        isp = geo.get("isp", "") if geo else ""
        loc = geo.get("loc", "") if geo else ""

        db = get_db()
        try:
            atomic_write(db,
                "INSERT OR IGNORE INTO reads(msg_id,wx_id,ip_address,user_agent,"
                "country,region,city,isp,loc,reader_wx_id) VALUES(?,?,?,?,?,?,?,?,?,?)",
                (mid, wx, ip, ua, country, region, city, isp, loc, reader))
        except Exception:
            _pixel_queue.append((mid, wx, ip, ua, country, region, city, isp, loc, reader))

        return send_file(BytesIO(TRANSPARENT_GIF), mimetype="image/gif")

    @app.route("/pixel.gif", strict_slashes=False)
    @rate_limit
    def pixel_gif():
        # 微信图片请求拿不到浏览者 wxId，固定标记为未知访客
        ip = get_client_ip()
        geo = lookup_ip_location(ip)
        country = geo.get("country", "") if geo else ""
        region = geo.get("region", "") if geo else ""
        city = geo.get("city", "") if geo else ""
        isp = geo.get("isp", "") if geo else ""
        loc = geo.get("loc", "") if geo else ""

        # 逆地理编码街道级地址（失败静默降级）
        street = reverse_geocode(loc) if loc else ""

        db = get_db()
        try:
            atomic_write(db,
                "INSERT OR IGNORE INTO reads(msg_id,wx_id,ip_address,user_agent,"
                "country,region,city,isp,loc,reader_wx_id) "
                "VALUES(?,?,?,?,?,?,?,?,?,?)",
                ("pixel.gif", "未知访客", ip, "wechat-image", country, region, city,
                 isp, loc, "未知访客"))
        except Exception:
            pass

        return send_file(BytesIO(TRANSPARENT_GIF), mimetype="image/gif")

    @app.route("/count")
    @rate_limit
    def count():
        # 查询已读人数 + 定位信息（客户端可直接拿省市）
        wx = request.args.get("wxId", "")
        mid = request.args.get("id", "")
        if not wx or not mid:
            return jsonify({"count": 0, "error": "wxId and id required"})

        db = get_db(readonly=True)
        r = db.execute(
            "SELECT COUNT(DISTINCT ip_address) cnt FROM reads "
            "WHERE msg_id=? AND wx_id=?",
            (mid, wx)).fetchone()
        rows = db.execute(
            "SELECT * FROM reads WHERE msg_id=? AND wx_id=? "
            "ORDER BY read_at DESC, id DESC",
            (mid, wx)).fetchall()
        return jsonify({
            "count": r["cnt"] if r else 0,
            "msg_id": mid,
            "reads": [{
                "ip_address": x["ip_address"],
                "reader_wx_id": x["reader_wx_id"] if "reader_wx_id" in x.keys() else "",
                "location": " ".join([y for y in [x["country"], x["region"],
                                                 x["city"]] if y]) or "-",
                "province": x["region"],
                "city": x["city"],
                "country": x["country"],
                "isp": x["isp"],
                "loc": _fmt_loc(x["loc"]) or "-",
                "user_agent": x["user_agent"],
                "read_at": datetime.fromtimestamp(x["read_at"])
                           .strftime("%Y-%m-%d %H:%M:%S"),
            } for x in rows],
        })

    @app.route("/")
    @require_admin
    @rate_limit
    def index():
        # 管理面板首页
        db = get_db(readonly=True)
        s = db.execute("""
            SELECT
                (SELECT COUNT(*) FROM messages) tm,
                (SELECT COUNT(DISTINCT ip_address) FROM reads) tr,
                CASE WHEN (SELECT COUNT(*) FROM messages)=0 THEN 0.0
                     ELSE ROUND((SELECT COUNT(*) FROM reads)*1.0
                          /(SELECT COUNT(*) FROM messages),1)
                END ar,
                (SELECT COUNT(DISTINCT ip_address) FROM reads
                 WHERE country != '' OR city != '') gr
        """).fetchone()
        tm = s["tm"] or 0
        tr = s["tr"] or 0
        ar = float(s["ar"] or 0)
        gr = s["gr"] or 0
        ms = db.execute(
            "SELECT m.*,(SELECT COUNT(DISTINCT ip_address) FROM reads r "
            "WHERE r.msg_id=m.id) read_cnt, "
            "(SELECT COUNT(DISTINCT ip_address) FROM reads r "
            "WHERE r.msg_id=m.id AND (r.country!='' OR r.city!='')) geo_cnt "
            "FROM messages m ORDER BY registered_at DESC LIMIT 100").fetchall()
        return render_template("index.html", total_messages=tm, total_reads=tr,
                               avg_reads=ar, geo_reads=gr, messages=ms)

    @app.route("/message/<mid>")
    @require_admin
    @rate_limit
    def detail(mid):
        # 消息详情（支持 ?json=1 返回 JSON）
        db = get_db(readonly=True)
        m = db.execute(
            "SELECT m.*,(SELECT COUNT(DISTINCT ip_address) FROM reads r "
            "WHERE r.msg_id=m.id) read_cnt "
            "FROM messages m WHERE m.id=?", (mid,)).fetchone()
        if not m:
            return render_template("error.html", message="404"), 404
        rs = db.execute(
            "SELECT wx_id,reader_wx_id,ip_address,user_agent,read_at,country,region,city,isp,loc "
            "FROM reads WHERE msg_id=? ORDER BY read_at DESC, id DESC", (mid,)).fetchall()
        hg = any(r["country"] or r["city"] for r in rs) if rs else False

        if request.args.get("json") == "1":
            return jsonify({
                "message": {"id": m["id"], "wxId": m["wx_id"],
                             "content": m["content"]},
                "reads": [{
                    "ip_address": r["ip_address"],
                    "location": " ".join([x for x in [r["country"], r["region"],
                                                     r["city"]] if x]) or "-",
                    "country": r["country"], "region": r["region"],
                    "city": r["city"], "isp": r["isp"],
                    "loc": _fmt_loc(r["loc"]) or "-",
                    "user_agent": r["user_agent"],
                    "read_at": datetime.fromtimestamp(r["read_at"])
                               .strftime("%Y-%m-%d %H:%M:%S"),
                } for r in rs],
            })
        return render_template("detail.html", message=m, reads=rs, has_geo=hg)

    @app.route("/api/reads/<mid>")
    @rate_limit
    def api_reads(mid):
        # 客户端专用：JSON 已读记录（含省市定位）—— 仍保持公开
        db = get_db(readonly=True)
        m = db.execute("SELECT * FROM messages WHERE id=?", (mid,)).fetchone()
        if not m:
            return jsonify({"error": "not found"}), 404
        rs = db.execute(
            "SELECT * FROM reads WHERE msg_id=? ORDER BY read_at DESC, id DESC",
            (mid,)).fetchall()
        return jsonify({
            "msg_id": mid,
            "wxId": m["wx_id"],
            "content": m["content"],
            "read_count": len(rs),
            "reads": [{
                "ip_address": r["ip_address"],
                "location": " ".join([x for x in [r["country"], r["region"],
                                                 r["city"]] if x]) or "-",
                "country": r["country"], "region": r["region"],
                "city": r["city"], "isp": r["isp"],
                "loc": _fmt_loc(r["loc"]) or "-",
                "user_agent": r["user_agent"],
                "read_at": datetime.fromtimestamp(r["read_at"])
                           .strftime("%Y-%m-%d %H:%M:%S"),
            } for r in rs],
        })

    @app.route("/api/messages")
    @rate_limit
    def api_messages():
        # 客户端专用：JSON 消息列表 —— 仍保持公开
        db = get_db(readonly=True)
        rows = db.execute(
            "SELECT m.*,(SELECT COUNT(DISTINCT ip_address) FROM reads r "
            "WHERE r.msg_id=m.id) cnt "
            "FROM messages m ORDER BY registered_at DESC LIMIT 100").fetchall()
        return jsonify({"messages": [{
            "id": r["id"], "wxId": r["wx_id"], "content": r["content"],
            "read_count": r["cnt"],
            "registered_at": datetime.fromtimestamp(r["registered_at"])
                             .strftime("%Y-%m-%d %H:%M:%S"),
        } for r in rows]})

    @app.route("/api/delete/<mid>", methods=["POST"])
    @require_admin
    @rate_limit
    def del_msg(mid):
        db = get_db()
        atomic_write(db, "DELETE FROM reads WHERE msg_id=?", (mid,))
        atomic_write(db, "DELETE FROM messages WHERE id=?", (mid,))
        return jsonify({"success": True})

    @app.route("/api/delete-all", methods=["POST"])
    @require_admin
    @rate_limit
    def del_all():
        db = get_db()
        atomic_write(db, "DELETE FROM reads")
        atomic_write(db, "DELETE FROM messages")
        return jsonify({"success": True})

    @app.route("/batch-status")
    @require_admin
    @rate_limit
    def batch_status():
        # 批量查询已读状态
        ids_str = request.args.get("ids", "")
        if not ids_str:
            return jsonify({"error": "ids required"}), 400
        ids = [i.strip() for i in ids_str.split(",") if i.strip()]
        if not ids:
            return jsonify({"error": "no valid ids"}), 400
        db = get_db(readonly=True)
        ph = ",".join("?" * len(ids))
        rows = db.execute(
            f"SELECT msg_id,COUNT(DISTINCT ip_address) cnt FROM reads "
            f"WHERE msg_id IN({ph}) GROUP BY msg_id",
            ids).fetchall()
        rv = {r["msg_id"]: r["cnt"] for r in rows}
        for mid in ids:
            if mid not in rv:
                rv[mid] = 0
        return jsonify({"statuses": rv})

    # ================= 字体文件静态服务 =================
    @app.route('/static/fonts/<path:filename>')
    def serve_font(filename):
        """
        将 /static/fonts/ 请求映射到 templates/fonts/ 目录
        """
        # 基础安全检查，防止路径穿越（虽然 send_from_directory 内部也有保护）
        if '..' in filename or filename.startswith(('/', '\\')):
            abort(404)

        # 拼接真实物理路径：templates/fonts/
        font_dir = os.path.join(app.template_folder, 'fonts')
        
        # 检查文件是否存在，不存在直接 404
        if not os.path.exists(os.path.join(font_dir, filename)):
            abort(404)

        # 发送文件
        response = make_response(send_from_directory(font_dir, filename))
        
        # 性能优化：字体文件是不变的，设置 1 年强缓存 (immutable)
        response.headers['Cache-Control'] = 'public, max-age=31536000, immutable'
        
        # 确保返回正确的 MIME 类型（部分旧版 Flask 可能无法自动识别 woff2）
        if filename.endswith('.woff2'):
            response.headers['Content-Type'] = 'font/woff2'
        elif filename.endswith('.woff'):
            response.headers['Content-Type'] = 'font/woff'
        elif filename.endswith('.ttf'):
            response.headers['Content-Type'] = 'font/ttf'
            
        return response
    # =======================================================
