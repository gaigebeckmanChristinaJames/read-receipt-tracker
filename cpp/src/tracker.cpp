#include "rrtracker.h"
#include <sqlite3.h>
#include <sstream>
#include <ctime>
#include <cstring>
#include <algorithm>
#include <iomanip>

extern "C" {
#include <openssl/evp.h>
}

namespace rrtracker {

// ================================================================
//  SHA-256
// ================================================================
static std::string sha256(const std::string& input) {
    unsigned char hash[EVP_MAX_MD_SIZE];
    unsigned int hash_len = 0;
    EVP_MD_CTX* ctx = EVP_MD_CTX_new();
    EVP_DigestInit_ex(ctx, EVP_sha256(), nullptr);
    EVP_DigestUpdate(ctx, input.c_str(), input.size());
    EVP_DigestFinal_ex(ctx, hash, &hash_len);
    EVP_MD_CTX_free(ctx);

    std::stringstream ss;
    for (unsigned int i = 0; i < hash_len; i++)
        ss << std::hex << std::setw(2) << std::setfill('0') << (int)hash[i];
    return ss.str();
}

// ================================================================
//  Tracker 实现
// ================================================================
Tracker::Tracker(Database* db) : db_(db) {
    // 1x1 透明 GIF
    transparent_gif_ = {
        0x47,0x49,0x46,0x38,0x39,0x61,0x01,0x00,0x01,0x00,
        0x80,0x00,0x00,0x00,0x00,0x00,0xFF,0xFF,0xFF,0x21,
        0xF9,0x04,0x01,0x00,0x00,0x00,0x00,0x2C,0x00,0x00,
        0x00,0x00,0x01,0x00,0x01,0x00,0x00,0x02,0x02,0x44,
        0x01,0x00,0x3B,
    };

    // 初始化表结构
    db_->execute(
        "CREATE TABLE IF NOT EXISTS messages("
        "id TEXT PRIMARY KEY, wx_id TEXT NOT NULL, content TEXT DEFAULT '', "
        "create_time INTEGER NOT NULL, "
        "registered_at INTEGER DEFAULT (CAST(strftime('%s','now') AS INTEGER)))"
    );
    db_->execute(
        "CREATE TABLE IF NOT EXISTS reads("
        "id INTEGER PRIMARY KEY AUTOINCREMENT, msg_id TEXT NOT NULL, "
        "wx_id TEXT NOT NULL, ip_address TEXT, user_agent TEXT, "
        "country TEXT DEFAULT '', region TEXT DEFAULT '', "
        "city TEXT DEFAULT '', isp TEXT DEFAULT '', "
        "read_at INTEGER DEFAULT (CAST(strftime('%s','now') AS INTEGER)), "
        "UNIQUE(msg_id, ip_address))"
    );
    db_->execute("CREATE INDEX IF NOT EXISTS idx_reads_msg ON reads(msg_id)");
    db_->execute("CREATE INDEX IF NOT EXISTS idx_reads_wx ON reads(wx_id)");
    db_->execute("CREATE INDEX IF NOT EXISTS idx_msgs_wx ON messages(wx_id)");
}

Tracker::~Tracker() = default;

std::string Tracker::register_message(
    const std::string& wx_id,
    const std::string& content,
    int64_t create_time_ms) {

    std::string input = wx_id + '\0' + content + '\0' + std::to_string(create_time_ms);
    std::string msg_id = sha256(input);

    auto* sdb = dynamic_cast<SQLiteDatabase*>(db_);
    sqlite3_stmt* stmt = nullptr;
    if (sdb) {
        const char* sql = "INSERT OR IGNORE INTO messages(id,wx_id,content,create_time) VALUES(?,?,?,?)";
        sqlite3_prepare_v2(sdb->raw(), sql, -1, &stmt, nullptr);
        sqlite3_bind_text(stmt, 1, msg_id.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(stmt, 2, wx_id.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(stmt, 3, content.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_int64(stmt, 4, create_time_ms);
        sqlite3_step(stmt);
        sqlite3_finalize(stmt);
    }
    return msg_id;
}

void Tracker::record_read(const std::string& msg_id,
                          const std::string& wx_id,
                          const std::string& ip,
                          const std::string& ua) {
    auto* sdb = dynamic_cast<SQLiteDatabase*>(db_);
    if (!sdb) return;
    const char* sql =
        "INSERT OR IGNORE INTO reads(msg_id,wx_id,ip_address,user_agent) "
        "VALUES(?,?,?,?)";
    sqlite3_stmt* stmt = nullptr;
    sqlite3_prepare_v2(sdb->raw(), sql, -1, &stmt, nullptr);
    sqlite3_bind_text(stmt, 1, msg_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 2, wx_id.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 3, ip.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 4, ua.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);
}

int Tracker::count_reads(const std::string& msg_id, const std::string& wx_id) {
    auto rows = db_->query(
        "SELECT COUNT(DISTINCT ip_address) FROM reads WHERE msg_id=? AND wx_id=?",
        {msg_id, wx_id});
    if (rows.empty() || rows[0].empty()) return 0;
    return std::stoi(rows[0][0]);
}

Stats Tracker::get_stats() {
    Stats s{0, 0, 0.0};
    auto tm = db_->query("SELECT COUNT(*) FROM messages");
    auto tr = db_->query("SELECT COUNT(DISTINCT ip_address) FROM reads");
    if (!tm.empty() && !tm[0].empty()) s.total_messages = std::stoi(tm[0][0]);
    if (!tr.empty() && !tr[0].empty()) s.total_reads = std::stoi(tr[0][0]);
    if (s.total_messages > 0)
        s.avg_reads = static_cast<double>(s.total_reads) / s.total_messages;
    return s;
}

std::vector<Message> Tracker::list_messages(int limit) {
    std::vector<Message> result;
    auto rows = db_->query(
        "SELECT m.id,m.wx_id,m.content,m.create_time,m.registered_at,"
        "COALESCE((SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id),0) "
        "FROM messages m ORDER BY registered_at DESC LIMIT " + std::to_string(limit));
    for (auto& r : rows) {
        if (r.size() >= 6) {
            result.push_back({
                r[0], r[1], r[2],
                r[3].empty() ? 0LL : std::stoll(r[3]),
                r[4].empty() ? 0LL : std::stoll(r[4]),
                r[5].empty() ? 0 : std::stoi(r[5])
            });
        }
    }
    return result;
}

std::optional<Message> Tracker::get_message(const std::string& msg_id) {
    auto rows = db_->query(
        "SELECT m.id,m.wx_id,m.content,m.create_time,m.registered_at,"
        "COALESCE((SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id),0) "
        "FROM messages m WHERE m.id=?", {msg_id});
    if (rows.empty() || rows[0].size() < 6) return std::nullopt;
    auto& r = rows[0];
    return Message{
        r[0], r[1], r[2],
        r[3].empty() ? 0LL : std::stoll(r[3]),
        r[4].empty() ? 0LL : std::stoll(r[4]),
        r[5].empty() ? 0 : std::stoi(r[5])
    };
}

std::vector<ReadRecord> Tracker::get_reads(const std::string& msg_id) {
    std::vector<ReadRecord> result;
    auto rows = db_->query(
        "SELECT ip_address,user_agent,read_at,country,region,city,isp "
        "FROM reads WHERE msg_id=? ORDER BY read_at DESC", {msg_id});
    for (auto& r : rows) {
        if (r.size() >= 7)
            result.push_back({r[0], r[1], r[2], r[3], r[4], r[5], r[6],
                              r[2].empty() ? 0LL : std::stoll(r[2])});
    }
    return result;
}

bool Tracker::delete_message(const std::string& msg_id) {
    db_->execute("DELETE FROM reads WHERE msg_id='" + msg_id + "'");
    db_->execute("DELETE FROM messages WHERE id='" + msg_id + "'");
    return true;
}

bool Tracker::delete_all() {
    db_->execute("DELETE FROM reads");
    db_->execute("DELETE FROM messages");
    return true;
}

} // namespace rrtracker
