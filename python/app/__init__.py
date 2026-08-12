"""read-receipt-tracker — 轻量级消息已读追踪服务

Flask + SQLite 实现，支持像素埋点、管理后台、Docker/Termux/Linux 多端部署。
"""
__version__ = "2.1.0"
__author__ = "gaigebeckmanChristinaJames"

from .app import create_app

__all__ = ["create_app", "__version__"]
