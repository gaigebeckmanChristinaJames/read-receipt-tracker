"""
工具函数 - IP 获取、消息 ID 生成。
"""
import hashlib

from flask import request


def get_client_ip() -> str:
    """获取客户端真实 IP（支持反向代理）。"""
    # X-Forwarded-For
    xff = request.headers.get("X-Forwarded-For", "")
    if xff:
        return xff.split(",")[0].strip()
    # X-Real-IP
    xri = request.headers.get("X-Real-IP", "")
    if xri:
        return xri.strip()
    # 兜底
    return request.remote_addr or "0.0.0.0"


def generate_message_id(wx_id: str, content: str, create_time_ms: int) -> str:
    """
    生成消息唯一 ID。
    算法: SHA-256(wx_id + '\0' + content + '\0' + create_time_ms)
    """
    m = hashlib.sha256()
    m.update(wx_id.encode("utf-8"))
    m.update(b"\x00")
    m.update(content.encode("utf-8"))
    m.update(b"\x00")
    m.update(str(create_time_ms).encode("utf-8"))
    return m.hexdigest()
