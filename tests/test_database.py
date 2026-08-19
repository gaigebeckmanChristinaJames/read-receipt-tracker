"""database 模块单元测试 — 使用临时数据库，不影响生产数据"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "python"))

from app.database import init_db, _new_connection


class TestDatabaseInit(unittest.TestCase):
    """测试数据库初始化"""

    def setUp(self):
        self.tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        self.tmp.close()
        self.db_path = self.tmp.name

    def tearDown(self):
        if os.path.exists(self.db_path):
            os.unlink(self.db_path)
            for ext in ("-wal", "-shm"):
                p = self.db_path + ext
                if os.path.exists(p):
                    os.unlink(p)

    def test_init_creates_tables(self):
        """init_db 应创建 messages 和 reads 表"""
        init_db(self.db_path)
        conn = _new_connection(self.db_path)
        tables = [r[0] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        ).fetchall()]
        self.assertIn("messages", tables)
        self.assertIn("reads", tables)
        conn.close()

    def test_init_idempotent(self):
        """多次调用 init_db 不应报错"""
        init_db(self.db_path)
        init_db(self.db_path)
        init_db(self.db_path)

    def test_messages_schema(self):
        """messages 表应包含必要列"""
        init_db(self.db_path)
        conn = _new_connection(self.db_path)
        cols = [r[1] for r in conn.execute("PRAGMA table_info(messages)").fetchall()]
        for col in ("id", "wx_id", "content", "create_time", "registered_at"):
            self.assertIn(col, cols)
        conn.close()

    def test_reads_schema(self):
        """reads 表应包含必要列"""
        init_db(self.db_path)
        conn = _new_connection(self.db_path)
        cols = [r[1] for r in conn.execute("PRAGMA table_info(reads)").fetchall()]
        for col in ("id", "msg_id", "wx_id", "ip_address", "user_agent",
                    "country", "region", "city", "isp", "read_at"):
            self.assertIn(col, cols)
        conn.close()

    def test_unique_constraint_msg_ip(self):
        """reads 表应有 (msg_id, ip_address) 唯一约束（智能去重）"""
        init_db(self.db_path)
        conn = _new_connection(self.db_path)
        conn.execute(
            "INSERT INTO reads (msg_id, wx_id, ip_address) VALUES (?, ?, ?)",
            ("msg1", "user1", "1.2.3.4")
        )
        conn.commit()
        with self.assertRaises(Exception):
            conn.execute(
                "INSERT INTO reads (msg_id, wx_id, ip_address) VALUES (?, ?, ?)",
                ("msg1", "user1", "1.2.3.4")
            )
        conn.close()


if __name__ == "__main__":
    unittest.main()
