"""read-receipt-tracker — Flask 应用工厂"""
import logging
import os

from flask import Flask

from .database import close_db, init_db
from .routes import register_routes

logger = logging.getLogger(__name__)


def create_app(testing: bool = False) -> Flask:
    """工厂模式创建 Flask app。"""
    app = Flask(
        __name__,
        template_folder=os.path.join(os.path.dirname(__file__), "templates"),
    )

    db_path = os.environ.get(
        "DATABASE_PATH",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipts.db"),
    )
    api_key = os.environ.get("API_KEY", "")
    geoip_db = os.environ.get("GEOIP_DB", "")

    app.config.update(
        DATABASE=db_path,
        API_KEY=api_key,
        GEOIP_DB=geoip_db,
        TESTING=testing,
        MAX_CONTENT_LENGTH=int(os.environ.get("MAX_CONTENT_LENGTH", 16 * 1024 * 1024)),
        RATE_LIMIT_PER_MINUTE=int(os.environ.get("RATE_LIMIT_PER_MINUTE", 60)),
    )

    logger.info("App created — db=%s api_key=%s geoip=%s testing=%s",
                 db_path,
                 "SET" if api_key else "NOT SET",
                 "SET" if geoip_db else "NOT SET",
                 testing)

    # 数据库初始化
    with app.app_context():
        init_db(db_path)

    # 每次请求注入 db_path
    @app.before_request
    def _inject_db_path():
        from flask import g
        g._db_path = db_path

    # 注册路由
    register_routes(app)

    # 关闭数据库
    app.teardown_appcontext(close_db)

    return app
