"""
数据库模块 - SQLite 初始化、连接管理。
"""
import os
import sqlite3

from flask import g


def init_db(database_path: str) -> None:
    """创建表结构和索引（幂等）。"""
    db = sqlite3.connect(database_path)
    db.executescript("""
        CREATE TABLE IF NOT EXISTS messages (
            id          TEXT PRIMARY KEY,
            wx_id       TEXT NOT NULL,
            content     TEXT DEFAULT '',
            create_time INTEGER NOT NULL,
            registered_at INTEGER DEFAULT (CAST(strftime('%s','now') AS INTEGER))
        );

        CREATE TABLE IF NOT EXISTS reads (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            msg_id      TEXT NOT NULL,
            wx_id       TEXT NOT NULL,
            ip_address  TEXT,
            user_agent  TEXT,
            read_at     INTEGER DEFAULT (CAST(strftime('%s','now') AS INTEGER)),
            UNIQUE(msg_id, ip_address)
        );

        CREATE INDEX IF NOT EXISTS idx_reads_msg  ON reads(msg_id);
        CREATE INDEX IF NOT EXISTS idx_reads_wx   ON reads(wx_id);
        CREATE INDEX IF NOT EXISTS idx_msgs_wx    ON messages(wx_id);
    """)
    db.commit()
    db.close()


def get_db() -> sqlite3.Connection:
    """获取当前请求上下文的数据库连接（惰性创建）。"""
    db = getattr(g, "_database", None)
    if db is None:
        db_path = g.get(
            "_db_path",
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipts.db"),
        )
        db = g._database = sqlite3.connect(db_path)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA journal_mode=WAL")
        db.execute("PRAGMA foreign_keys=ON")
    return db


def close_db(exception=None) -> None:
    """关闭当前请求上下文的数据库连接。"""
    db = getattr(g, "_database", None)
    if db is not None:
        db.close()
