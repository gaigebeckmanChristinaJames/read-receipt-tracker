"""工具函数 — IP 获取、消息 ID 生成、IP 定位（中文优先）"""
import hashlib
import logging
import os
import urllib.request
from typing import Optional

from flask import request

logger = logging.getLogger(__name__)


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
    """查询 IP 地理位置（中文优先，支持 IPv4/IPv6）。

    全局开关：环境变量 ENABLE_GEO=0 时直接关闭定位（Lite 模式），
    不发起任何外部请求，零延迟、零外部依赖。

    三级接口备份，全部免费无需 Key：
    1. ip-api.com  (lang=zh-CN，返回 中国/上海市/上海)
    2. ipwho.is    (lang=zh-CN)
    3. ipinfo.io   (英文，兜底)

    失败静默降级返回 None，绝不阻塞调用方。
    """
    # Lite 模式开关：ENABLE_GEO=0/off/false 时关闭定位
    if os.environ.get("ENABLE_GEO", "1").lower() in ("0", "off", "false", "no"):
        return None

    if ip in ("0.0.0.0", "127.0.0.1", "::1", "") or not ip:
        return None

    try:
        import json as _json
    except ImportError:
        return None

    # 接口 1: ip-api.com 中文
    try:
        req = urllib.request.Request(
            f"http://ip-api.com/json/{ip}"
            f"?lang=zh-CN&fields=status,message,country,regionName,city,isp,lat,lon",
            headers={"User-Agent": "rrt/2.1"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            d = _json.load(resp)
        if d.get("status") == "success":
            return {
                "country": d.get("country", ""),
                "region": d.get("regionName", ""),
                "city": d.get("city", ""),
            }
    except Exception:
        pass

    # 接口 2: ipwho.is 中文
    try:
        req = urllib.request.Request(
            f"https://ipwho.is/{ip}?lang=zh-CN",
            headers={"User-Agent": "rrt/2.1"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            d = _json.load(resp)
        if d.get("success", False):
            return {
                "country": d.get("country", ""),
                "region": d.get("region", ""),
                "city": d.get("city", ""),
            }
    except Exception:
        pass

    # 接口 3: ipinfo.io 英文兜底
    try:
        req = urllib.request.Request(
            f"https://ipinfo.io/{ip}/json",
            headers={"User-Agent": "curl/7.81.0"})
        with urllib.request.urlopen(req, timeout=6) as resp:
            d = _json.load(resp)
        if d.get("country"):
            return {
                "country": d.get("country", ""),
                "region": d.get("region", ""),
                "city": d.get("city", ""),
            }
    except Exception:
        pass

    return None
