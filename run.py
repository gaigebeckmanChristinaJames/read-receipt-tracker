#!/usr/bin/env python3
"""
read-receipt-tracker 启动入口

用法:
    python run.py                  # 默认 5000 端口
    python run.py --port 8080      # 自定义端口
    python run.py --host 0.0.0.0  # 监听所有网口

生产环境推荐使用 gunicorn:
    pip install gunicorn
    gunicorn -w 4 -b 0.0.0.0:5000 "app:create_app()"
"""
import argparse
import os
import sys


def _load_dotenv():
    """尝试加载 .env 文件（无额外依赖，手动解析）。"""
    env_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if not os.path.isfile(env_file):
        return
    with open(env_file, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key and key not in os.environ:
                os.environ[key] = value


def main():
    _load_dotenv()

    parser = argparse.ArgumentParser(description="read-receipt-tracker 服务")
    parser.add_argument("--host", default=os.environ.get("HOST", "0.0.0.0"),
                        help="监听地址 (默认: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=int(os.environ.get("PORT", 5000)),
                        help="监听端口 (默认: 5000)")
    parser.add_argument("--debug", action="store_true",
                        help="开启 debug 模式 (生产环境请勿使用)")

    # 兼容通过环境变量控制
    if os.environ.get("FLASK_DEBUG", "").lower() in ("1", "true", "yes"):
        sys.argv.append("--debug")

    args = parser.parse_args()

    from app import create_app
    app = create_app()
    app.run(host=args.host, port=args.port, debug=args.debug)


if __name__ == "__main__":
    main()
