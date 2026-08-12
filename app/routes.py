"""
路由模块 - API + 管理后台。
"""
import time
from datetime import datetime
from io import BytesIO

from flask import (
    Blueprint,
    Flask,
    jsonify,
    render_template,
    request,
    send_file,
    redirect,
    url_for,
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

    # ---- API ----

    @app.route("/health")
    def health():
        return jsonify({"status": "ok", "service": "read-receipt-tracker"})

    @app.route("/register", methods=["POST"])
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

        wx_id = data.get("wxId", "").strip()
        content = data.get("content", "")
        create_time = data.get("createTime", int(time.time() * 1000))

        if not wx_id:
            return jsonify({"error": "wxId is required"}), 400

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

        pixel_url = f"{request.host_url}pixel?wxId={wx_id}&id={msg_id}"
        return jsonify(
            {
                "success": True,
                "id": msg_id,
                "wxId": wx_id,
                "pixel_url": pixel_url,
            }
        )

    @app.route("/pixel")
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

    # ---- 管理后台 ----

    @app.route("/")
    def index():
        """
        管理面板首页 - 消息列表 + 统计。
        """
        db = get_db()

        # 统计数据
        total_messages = db.execute("SELECT COUNT(*) AS t FROM messages").fetchone()["t"]
        total_reads = db.execute(
            "SELECT COUNT(DISTINCT ip_address) AS t FROM reads"
        ).fetchone()["t"]

        avg_reads = 0.0
        if total_messages > 0:
            avg_row = db.execute(
                "SELECT AVG(cnt) AS a FROM ("
                "SELECT COUNT(DISTINCT ip_address) AS cnt FROM reads GROUP BY msg_id"
                ")"
            ).fetchone()
            if avg_row and avg_row["a"]:
                avg_reads = round(avg_row["a"], 1)

        messages = db.execute(
            "SELECT m.*, "
            "(SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id = m.id) AS read_cnt "
            "FROM messages m ORDER BY registered_at DESC LIMIT 50"
        ).fetchall()

        return render_template(
            "index.html",
            total_messages=total_messages,
            total_reads=total_reads,
            avg_reads=avg_reads,
            messages=messages,
        )

    @app.route("/message/<mid>")
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
            return "404 — 消息不存在", 404

        reads = db.execute(
            "SELECT ip_address, user_agent, read_at "
            "FROM reads WHERE msg_id = ? ORDER BY read_at DESC",
            (mid,),
        ).fetchall()

        return render_template("detail.html", message=msg, reads=reads)

    @app.route("/api/delete/<mid>", methods=["POST"])
    def delete_message(mid: str):
        """删除单条消息及其已读记录。"""
        db = get_db()
        db.execute("DELETE FROM reads WHERE msg_id = ?", (mid,))
        db.execute("DELETE FROM messages WHERE id = ?", (mid,))
        db.commit()
        return jsonify({"success": True})

    @app.route("/api/delete-all", methods=["POST"])
    def delete_all():
        """清空全部消息和已读记录。"""
        db = get_db()
        db.execute("DELETE FROM reads")
        db.execute("DELETE FROM messages")
        db.commit()
        return jsonify({"success": True})
