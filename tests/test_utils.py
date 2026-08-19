"""utils 模块单元测试 — 不依赖 Flask 请求上下文的纯函数"""
import hashlib
import os
import sys
import unittest

# 将项目根目录加入 path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "python"))

from app.utils import generate_message_id, _cn_isp


class TestGenerateMessageId(unittest.TestCase):
    """测试消息 ID 生成算法"""

    def test_deterministic(self):
        """相同输入应产生相同 ID"""
        id1 = generate_message_id("user1", "hello", 1700000000000)
        id2 = generate_message_id("user1", "hello", 1700000000000)
        self.assertEqual(id1, id2)

    def test_different_content(self):
        """不同内容应产生不同 ID"""
        id1 = generate_message_id("user1", "hello", 1700000000000)
        id2 = generate_message_id("user1", "world", 1700000000000)
        self.assertNotEqual(id1, id2)

    def test_different_wxid(self):
        """不同 wxId 应产生不同 ID"""
        id1 = generate_message_id("user1", "hello", 1700000000000)
        id2 = generate_message_id("user2", "hello", 1700000000000)
        self.assertNotEqual(id1, id2)

    def test_different_time(self):
        """不同时间应产生不同 ID"""
        id1 = generate_message_id("user1", "hello", 1700000000000)
        id2 = generate_message_id("user1", "hello", 1700000000001)
        self.assertNotEqual(id1, id2)

    def test_sha256_format(self):
        """ID 应为 64 位十六进制字符串（SHA-256）"""
        mid = generate_message_id("user1", "hello", 1700000000000)
        self.assertEqual(len(mid), 64)
        int(mid, 16)  # 应能解析为十六进制

    def test_matches_manual_hash(self):
        """验证算法与文档描述一致: SHA-256(wxId + '\\0' + content + '\\0' + createTime)"""
        wx_id, content, ts = "user1", "hello", 1700000000000
        expected = hashlib.sha256(
            wx_id.encode() + b"\x00" + content.encode() + b"\x00" + str(ts).encode()
        ).hexdigest()
        self.assertEqual(generate_message_id(wx_id, content, ts), expected)

    def test_unicode_content(self):
        """中文内容应正常处理"""
        mid = generate_message_id("user1", "你好世界", 1700000000000)
        self.assertEqual(len(mid), 64)


class TestCnIsp(unittest.TestCase):
    """测试运营商中文名转换"""

    def test_china_mobile(self):
        self.assertEqual(_cn_isp("China Mobile"), "中国移动")

    def test_china_unicom(self):
        self.assertEqual(_cn_isp("China Unicom"), "中国联通")

    def test_china_telecom(self):
        self.assertEqual(_cn_isp("China Telecom"), "中国电信")

    def test_case_insensitive(self):
        self.assertEqual(_cn_isp("CHINA MOBILE"), "中国移动")

    def test_unknown_isp(self):
        """未知运营商保留原文"""
        self.assertEqual(_cn_isp("Some Unknown ISP"), "Some Unknown ISP")

    def test_empty_string(self):
        self.assertEqual(_cn_isp(""), "")

    def test_none(self):
        self.assertIsNone(_cn_isp(None)) if _cn_isp(None) is None else self.assertEqual(_cn_isp(None), "")


if __name__ == "__main__":
    unittest.main()
