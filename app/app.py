"""
read-receipt-tracker - 轻量级消息已读追踪服务
"""

import os

from flask import Flask

from .database import init_db, close_db
from .routes import register_routes


def create_app(testing: bool = False) -> Flask:
    """
    Application factory.
    使用工厂模式创建 Flask app 实例。
    """
    app = Flask(
        __name__,
        template_folder=os.path.join(os.path.dirname(__file__), "templates"),
    )

    # --- 配置 ---
    app.config.update(
        DATABASE=os.environ.get(
            "DATABASE_PATH",
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipts.db"),
        ),
        TESTING=testing,
        MAX_CONTENT_LENGTH=int(os.environ.get("MAX_CONTENT_LENGTH", 16 * 1024 * 1024)),
    )

    # --- 数据库 ---
    with app.app_context():
        init_db(app.config["DATABASE"])

    # --- 路由 ---
    register_routes(app)

    # --- 收尾 ---
    app.teardown_appcontext(close_db)

    return app
