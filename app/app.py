"""
read-receipt-tracker - 轻量级消息已读追踪服务
"""

import os
import logging

from flask import Flask

from .database import init_db, close_db
from .routes import register_routes

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


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
    db_path = os.environ.get(
        "DATABASE_PATH",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipts.db"),
    )
    api_key = os.environ.get("API_KEY", "")

    app.config.update(
        DATABASE=db_path,
        API_KEY=api_key,
        TESTING=testing,
        MAX_CONTENT_LENGTH=int(os.environ.get("MAX_CONTENT_LENGTH", 16 * 1024 * 1024)),
        RATE_LIMIT_PER_MINUTE=int(os.environ.get("RATE_LIMIT_PER_MINUTE", 60)),
    )

    logger.info(
        "App created — db=%s, api_key=%s, testing=%s",
        db_path, "SET" if api_key else "NOT SET (admin routes disabled)", testing,
    )

    # --- 数据库 ---
    with app.app_context():
        init_db(db_path)

    # 在每次请求开始前注入 db_path 到 g，供 get_db() 使用
    @app.before_request
    def _inject_db_path():
        from flask import g
        g._db_path = db_path

    # --- 路由 ---
    register_routes(app)

    # --- 收尾 ---
    app.teardown_appcontext(close_db)

    return app
