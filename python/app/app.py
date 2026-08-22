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
    
    # --- 鉴权与会话配置 ---
    api_key = os.environ.get("API_KEY", "")
    admin_password = os.environ.get("ADMIN_PASSWORD", "")
    session_days = int(os.environ.get("SESSION_DAYS", "7"))
    # 兼容各种布尔值写法 (true, 1, yes)
    secure_cookie = os.environ.get("SESSION_COOKIE_SECURE", "false").lower() in ("true", "1", "yes")

    app.config.update(
        DATABASE=db_path,
        API_KEY=api_key,
        ADMIN_PASSWORD=admin_password,   # 独立登录密码（可选）
        SESSION_DAYS=session_days,       # 登录有效期（天）
        SESSION_COOKIE_SECURE=secure_cookie, # HTTPS 下务必开启
        
        # --- 原有配置 ---
        GEOIP_DB=os.environ.get("GEOIP_DB", ""),
        TESTING=testing,
        MAX_CONTENT_LENGTH=int(os.environ.get("MAX_CONTENT_LENGTH", 16 * 1024 * 1024)),
        RATE_LIMIT_PER_MINUTE=int(os.environ.get("RATE_LIMIT_PER_MINUTE", 60)),
    )

    logger.info("App created — db=%s api_key=%s admin_pwd=%s geoip=%s testing=%s",
                 db_path,
                 "SET" if api_key else "NOT SET",
                 "SET" if admin_password else "NOT SET (Fallback to API_KEY)",
                 "SET" if app.config["GEOIP_DB"] else "NOT SET",
                 testing)
    if app.config.get("ADMIN_PASSWORD") and not app.config.get("API_KEY"):
        logger.warning(
        "ADMIN_PASSWORD 已设置但 API_KEY 为空，鉴权未启用。"
        "请先设置 API_KEY 环境变量。"
    )
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
