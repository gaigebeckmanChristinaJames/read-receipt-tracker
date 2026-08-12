#!/usr/bin/env python3
"""
read-receipt-tracker 启动入口
用法:
    python run.py                  # 默认 5000 端口
    python run.py --port 8080      # 自定义端口
    python run.py --host 0.0.0.0  # 监听所有网口
"""
import argparse

from app import create_app


def main():
    parser = argparse.ArgumentParser(description="read-receipt-tracker 服务")
    parser.add_argument("--host", default="0.0.0.0", help="监听地址 (默认: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=5000, help="监听端口 (默认: 5000)")
    parser.add_argument("--debug", action="store_true", help="开启 debug 模式")
    args = parser.parse_args()

    app = create_app()
    app.run(host=args.host, port=args.port, debug=args.debug)


if __name__ == "__main__":
    main()
