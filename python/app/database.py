"""数据库模块 — SQLite 连接管理，优化并发写入"""
import os
import sqlite3
import threading

from flask import g

# 线程锁，保护单连接写入
_db_lock = threading.Lock()


def init_db(database_path: str) -> None:
    """创建表结构（幂等）。"""
    db = sqlite3.connect(database_path)
    db.executescript("""
        CREATE TABLE IF NOT EXISTS messages (
            id              TEXT PRIMARY KEY,
            wx_id           TEXT NOT NULL,
            content         TEXT DEFAULT '',
            create_time     INTEGER NOT NULL,
            registered_at   INTEGER DEFAULT (CAST(strftime('%s','now') AS INTEGER))
        );

        CREATE TABLE IF NOT EXISTS reads (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            msg_id          TEXT NOT NULL,
            wx_id           TEXT NOT NULL,
            ip_address      TEXT,
            user_agent      TEXT,
            country         TEXT DEFAULT '',
            region          TEXT DEFAULT '',
            city            TEXT DEFAULT '',
            isp             TEXT DEFAULT '',
            loc             TEXT DEFAULT '',
            read_at         INTEGER DEFAULT (CAST(strftime('%s','now') AS INTEGER)),
            UNIQUE(msg_id, ip_address)
        );

        CREATE INDEX IF NOT EXISTS idx_reads_msg  ON reads(msg_id);
        CREATE INDEX IF NOT EXISTS idx_reads_wx   ON reads(wx_id);
        CREATE INDEX IF NOT EXISTS idx_msgs_wx    ON messages(wx_id);
    """)
    # 兼容旧库：动态补列（loc 等新字段）
    for col in ["country", "region", "city", "isp", "loc"]:
        try:
            db.execute(f"ALTER TABLE reads ADD COLUMN {col} TEXT DEFAULT ''")
        except Exception:
            pass
    db.commit()
    db.close()


def get_db(readonly: bool = False) -> sqlite3.Connection:
    """获取数据库连接。

    写入场景使用线程锁，避免 SQLITE_BUSY。
    readonly=True 用于只读查询（无需加锁）。
    """
    if readonly:
        # 只读：直接复用连接，无锁
        db = getattr(g, "_database_ro", None)
        if db is None:
            return _new_connection(g._db_path)
        return db

    # 写入：加锁保护
    db = getattr(g, "_database_rw", None)
    if db is None:
        db = g._database_rw = _new_connection(g._db_path)
    return db

def close_db(exception=None) -> None:
    """关闭连接。"""
    for k in ("_database_ro", "_database_rw"):
        db = getattr(g, k, None)
        if db is not None:
            try:
                db.close()
            except Exception:
                pass


def _new_connection(path: str) -> sqlite3.Connection:
    """创建优化后的数据库连接。

    性能参数:
    - WAL: 写入不阻塞读取
    - synchronous=NORMAL: 写入不等待 fsync（崩溃安全）
    - busy_timeout=5000: 锁等待 5 秒而非立即报错
    - cache_size=-8000: 8MB 缓存
    - mmap_size: 内存映射加速
    """
    conn = sqlite3.connect(path, check_same_thread=False, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA busy_timeout=5000")
    conn.execute("PRAGMA cache_size=-8000")
    conn.execute("PRAGMA mmap_size=67108864")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def atomic_write(db: sqlite3.Connection, sql: str, params: tuple = ()) -> None:
    """原子写入操作（带线程锁 + 自动提交）。

    用于 pixel、register 等高频写入端点，防止并发冲突。
    """
    with _db_lock:
        db.execute(sql, params)
        db.commit()
