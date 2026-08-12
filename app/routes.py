"""
路由模块 - API + 管理后台。
"""
import time
from datetime import datetime
from functools import wraps
from io import BytesIO

from flask import (
    Flask,
    current_app,
    g,
    jsonify,
    render_template,
    request,
    send_file,
)

from .database import get_db
from .utils import get_client_ip, generate_message_id

# 1×1 透明 GIF
TRANSPARENT_GIF = bytes(
    [
        0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
        0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0x21,
        0xF9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00,
        0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44,
        0x01, 0x00, 0x3B,
    ]
)

# 公开接口白名单（无需 API Key）
PUBLIC_ENDPOINTS = {"health", "register", "pixel", "count"}


# --------------------------------------------------------------- #
#  装饰器
# --------------------------------------------------------------- #
def require_api_key(f):
    """API Key 认证装饰器。设置了 API_KEY 环境变量后生效。"""
    @wraps(f)
    def decorated(*args, **kwargs):
        api_key = current_app.config.get("API_KEY", "")
        if not api_key:
            # 未配置 API_KEY，放行所有请求（向后兼容）
            return f(*args, **kwargs)
        # 支持 Header (X-API-Key) 或 Query (?api_key=xxx)
        req_key = request.headers.get("X-API-Key") or request.args.get("api_key", "")
        if req_key != api_key:
            return jsonify({"error": "Unauthorized"}), 401
        return f(*args, **kwargs)
    return decorated


def rate_limit(f):
    """简易 IP 级请求频率限制。"""
    @wraps(f)
    def decorated(*args, **kwargs):
        limit = current_app.config.get("RATE_LIMIT_PER_MINUTE", 60)
        if limit <= 0:
            return f(*args, **kwargs)

        ip = get_client_ip()
        key = f"_rl_{f.__name__}_{ip}"
        now = int(time.time())
        window = getattr(g, key, None)
        if window is None:
            g.__dict__[key] = {"start": now, "count": 1}
        else:
            if now - window["start"] > 60:
                window["start"] = now
                window["count"] = 1
            else:
                window["count"] += 1
                if window["count"] > limit:
                    return jsonify({"error": "Too many requests"}), 429
        return f(*args, **kwargs)
    return decorated


# --------------------------------------------------------------- #
#  Jinja 过滤器
# --------------------------------------------------------------- #
def _timestamp_to_date(ts):
    try:
        return datetime.fromtimestamp(int(ts)).strftime("%Y-%m-%d %H:%M:%S")
    except (ValueError, TypeError):
        return str(ts)


def register_routes(app: Flask) -> None:
    """向 Flask app 注册所有路由。"""
    app.template_filter("ts2date")(_timestamp_to_date)

    # ---- API (公开) ----

    @app.route("/health")
    @rate_limit
    def health():
        return jsonify({"status": "ok", "service": "read-receipt-tracker"})

    @app.route("/register", methods=["POST"])
    @rate_limit
    def register():
        """
        注册新消息。
        POST JSON: {"wxId":"...", "content":"...", "createTime":<ms>}
        返回 pixel_url 用于嵌入信件追踪。
        """
        try:
            data = request.get_json(force=True)
        except Exception:
            return jsonify({"error": "Invalid JSON"}), 400

        wx_id = (data.get("wxId", "") or "").strip()
        content = data.get("content", "") or ""
        create_time = data.get("createTime", int(time.time() * 1000))

        if not wx_id:
            return jsonify({"error": "wxId is required"}), 400

        # 内容长度限制
        if len(content) > 50000:
            return jsonify({"error": "content too long (max 50000 chars)"}), 400

        msg_id = generate_message_id(wx_id, content, create_time)

        db = get_db()
        try:
            db.execute(
                "INSERT OR IGNORE INTO messages(id, wx_id, content, create_time) "
                "VALUES (?, ?, ?, ?)",
                (msg_id, wx_id, content, create_time),
            )
            db.commit()
        except Exception as exc:
            return jsonify({"error": str(exc)}), 500

        pixel_url = f"{request.host_url.rstrip('/')}/pixel?wxId={wx_id}&id={msg_id}"
        return jsonify(
            {
                "success": True,
                "id": msg_id,
                "wxId": wx_id,
                "pixel_url": pixel_url,
            }
        )

    @app.route("/pixel")
    @rate_limit
    def pixel():
        """
        追踪像素端点。
        返回 1×1 透明 GIF，同时记录已读。
        参数: wxId, id
        """
        wx_id = request.args.get("wxId", "")
        msg_id = request.args.get("id", "")
        if not wx_id or not msg_id:
            return send_file(BytesIO(TRANSPARENT_GIF), mimetype="image/gif")

        ip = get_client_ip()
        ua = (request.headers.get("User-Agent", "") or "")[:500]

        db = get_db()
        db.execute(
            "INSERT OR IGNORE INTO reads(msg_id, wx_id, ip_address, user_agent) "
            "VALUES (?, ?, ?, ?)",
            (msg_id, wx_id, ip, ua),
        )
        db.commit()

        return send_file(BytesIO(TRANSPARENT_GIF), mimetype="image/gif")

    @app.route("/count")
    @rate_limit
    def count():
        """
        查询某条消息的已读人数。
        参数: wxId, id
        """
        wx_id = request.args.get("wxId", "")
        msg_id = request.args.get("id", "")
        if not wx_id or not msg_id:
            return jsonify({"count": 0, "error": "wxId and id are required"})

        db = get_db()
        row = db.execute(
            "SELECT COUNT(DISTINCT ip_address) AS cnt "
            "FROM reads WHERE msg_id = ? AND wx_id = ?",
            (msg_id, wx_id),
        ).fetchone()
        return jsonify({"count": row["cnt"] if row else 0, "msg_id": msg_id})

    # ---- 管理后台 (需 API Key 认证) ----

    @app.route("/")
    @require_api_key
    @rate_limit
    def index():
        """
        管理面板首页 - 消息列表 + 统计。
        """
        db = get_db()

        # 单次查询获取全部统计数据
        stats = db.execute("""
            SELECT
                (SELECT COUNT(*) FROM messages)                                   AS total_messages,
                (SELECT COUNT(DISTINCT ip_address) FROM reads)                    AS total_reads,
                CASE
                    WHEN (SELECT COUNT(*) FROM messages) = 0 THEN 0.0
                    ELSE ROUND(
                        (SELECT COUNT(*) FROM reads) * 1.0
                        / (SELECT COUNT(*) FROM messages), 1
                    )
                END                                                               AS avg_reads
        """).fetchone()

        total_messages = stats["total_messages"] or 0
        total_reads = stats["total_reads"] or 0
        avg_reads = stats["avg_reads"] or 0.0

        messages = db.execute(
            "SELECT m.*, "
            "(SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id = m.id) AS read_cnt "
            "FROM messages m ORDER BY registered_at DESC LIMIT 100"
        ).fetchall()

        return render_template(
            "index.html",
            total_messages=total_messages,
            total_reads=total_reads,
            avg_reads=avg_reads,
            messages=messages,
        )

    @app.route("/message/<mid>")
    @require_api_key
    @rate_limit
    def detail(mid: str):
        """
        消息详情页 - 展示所有已读记录。
        """
        db = get_db()
        msg = db.execute(
            "SELECT m.*, "
            "(SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id = m.id) AS read_cnt "
            "FROM messages m WHERE m.id = ?",
            (mid,),
        ).fetchone()

        if not msg:
            return render_template("error.html", message="404 — 消息不存在"), 404

        reads = db.execute(
            "SELECT ip_address, user_agent, read_at "
            "FROM reads WHERE msg_id = ? ORDER BY read_at DESC",
            (mid,),
        ).fetchall()

        return render_template("detail.html", message=msg, reads=reads)

    @app.route("/api/delete/<mid>", methods=["POST"])
    @require_api_key
    @rate_limit
    def delete_message(mid: str):
        """删除单条消息及其已读记录。"""
        db = get_db()
        db.execute("DELETE FROM reads WHERE msg_id = ?", (mid,))
        db.execute("DELETE FROM messages WHERE id = ?", (mid,))
        db.commit()
        return jsonify({"success": True})

    @app.route("/api/delete-all", methods=["POST"])
    @require_api_key
    @rate_limit
    def delete_all():
        """清空全部消息和已读记录。"""
        db = get_db()
        db.execute("DELETE FROM reads")
        db.execute("DELETE FROM messages")
        db.commit()
        return jsonify({"success": True})
