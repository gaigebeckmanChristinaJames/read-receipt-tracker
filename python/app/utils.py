"""工具函数 — IP 获取、消息 ID 生成、IP 定位（可选）"""
import hashlib
import logging
import os
from typing import Optional

from flask import request

logger = logging.getLogger(__name__)

# GeoIP 数据库路径缓存
_geoip_reader = None


def get_client_ip() -> str:
    """获取客户端真实 IP（支持反向代理）。"""
    xff = request.headers.get("X-Forwarded-For", "")
    if xff:
        return xff.split(",")[0].strip()
    xri = request.headers.get("X-Real-IP", "")
    if xri:
        return xri.strip()
    return request.remote_addr or "0.0.0.0"


def generate_message_id(wx_id: str, content: str, create_time_ms: int) -> str:
    """生成消息唯一 ID (SHA-256)。"""
    m = hashlib.sha256()
    m.update(wx_id.encode("utf-8"))
    m.update(b"\x00")
    m.update(content.encode("utf-8"))
    m.update(b"\x00")
    m.update(str(create_time_ms).encode("utf-8"))
    return m.hexdigest()


def lookup_ip_location(ip: str) -> Optional[dict]:
    """查询 IP 地理位置信息 (需要 GeoLite2 或类似数据库)。

    设置环境变量 GEOIP_DB 指向 .mmdb 文件即可启用。
    使用 maxminddb 库（pip install maxminddb）。
    未安装或未配置时静默跳过。
    """
    global _geoip_reader
    try:
        import maxminddb  # type: ignore[import-untyped]
    except ImportError:
        logger.debug("maxminddb not installed, ip geolocation disabled")
        return None

    db_path = os.environ.get("GEOIP_DB", "")
    if not db_path or not os.path.isfile(db_path):
        return None

    if _geoip_reader is None:
        try:
            _geoip_reader = maxminddb.open_database(db_path)
        except Exception as exc:
            logger.warning("Failed to open GeoIP DB: %s", exc)
            return None

    try:
        result = _geoip_reader.get(ip)
    except Exception:
        return None

    if not result:
        return None

    # 标准化字段
    return {
        "country": result.get("country", {}).get("names", {}).get("zh-CN",
                              result.get("country", {}).get("names", {}).get("en", "")),
        "region": result.get("subdivisions", [{}])[0].get("names", {}).get("zh-CN",
                              result.get("subdivisions", [{}])[0].get("names", {}).get("en", "")),
        "city": result.get("city", {}).get("names", {}).get("zh-CN",
                            result.get("city", {}).get("names", {}).get("en", "")),
        "iso_code": result.get("country", {}).get("iso_code", ""),
    }
