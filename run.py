#!/usr/bin/env python3
"""read-receipt-tracker 启动入口

直接启动:
    python run.py
    python run.py --port 8080

开发:
    python run.py --debug

生产 (推荐):
    gunicorn -w 4 -b 0.0.0.0:5000 "python.app:create_app()"
    # -w 4: 4个worker进程（多核并发）
    # 或使用 gevent 异步:
    pip install gevent
    gunicorn -k gevent -w 1 -b 0.0.0.0:5000 "python.app:create_app()"
"""
import argparse
import os
import sys
import threading


def _load_dotenv():
    """手动加载 .env。"""
    for candidate in [".env",
                      os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")]:
        if not os.path.isfile(candidate):
            continue
        with open(candidate, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, _, v = line.partition("=")
                k = k.strip()
                v = v.strip().strip('"').strip("'")
                if k and k not in os.environ:
                    os.environ[k] = v
        break


def _flush_pixel_queue():
    """定期刷新 pixel 队列中的积压记录（高并发场景的兜底）。"""
    import time as _time
    from python.app.database import atomic_write, _new_connection as new_conn

    db_path = os.environ.get(
        "DATABASE_PATH",
        os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "python", "app", "receipts.db"),
    )
    conn = new_conn(db_path)

    while True:
        _time.sleep(5)
        from python.app import routes as rt
        while rt._pixel_queue:
            try:
                mid, wx, ip, ua = rt._pixel_queue.pop(0)
                atomic_write(conn,
                    "INSERT OR IGNORE INTO reads(msg_id,wx_id,ip_address,"
                    "user_agent) VALUES(?,?,?,?)",
                    (mid, wx, ip, ua))
            except Exception:
                pass


def main():
    _load_dotenv()

    parser = argparse.ArgumentParser(description="read-receipt-tracker")
    parser.add_argument("--host", default=os.environ.get("HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("PORT", 5000)))
    parser.add_argument("--debug", action="store_true")

    if os.environ.get("FLASK_DEBUG", "").lower() in ("1", "true", "yes"):
        sys.argv.append("--debug")

    args = parser.parse_args()
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

    # 启动 pixel 队列刷新线程
    t = threading.Thread(target=_flush_pixel_queue, daemon=True)
    t.start()

    from python.app import create_app
    app = create_app()
    app.run(host=args.host, port=args.port, debug=args.debug,
            threaded=True)  # Flask 内置多线程模式


if __name__ == "__main__":
    main()
