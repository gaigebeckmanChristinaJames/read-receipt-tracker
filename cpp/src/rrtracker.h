#ifndef RRTRACKER_H
#define RRTRACKER_H

#include <string>
#include <vector>
#include <cstdint>
#include <optional>

namespace rrtracker {

// ================================================================
//  消息
// ================================================================
struct Message {
    std::string id;
    std::string wx_id;
    std::string content;
    int64_t create_time;
    int64_t registered_at;
    int read_count;
};

// ================================================================
//  已读记录
// ================================================================
struct ReadRecord {
    std::string ip_address;
    std::string user_agent;
    std::string country;
    std::string region;
    std::string city;
    std::string isp;
    int64_t read_at;
};

// ================================================================
//  统计数据
// ================================================================
struct Stats {
    int total_messages;
    int total_reads;
    double avg_reads;
};

// ================================================================
//  HTTP 服务器 (微框架，仅支持 GET/POST)
// ================================================================
enum class Method { GET, POST };

struct Request {
    Method method;
    std::string path;
    std::string body;
    std::string query;
    std::string header(const std::string& name) const;
};

struct Response {
    int status = 200;
    std::string content_type = "application/json";
    std::string body;
};

using Handler = std::function<Response(const Request&)>;

// ================================================================
//  数据库抽象
// ================================================================
class Database {
public:
    virtual ~Database() = default;
    virtual bool open(const std::string& path) = 0;
    virtual bool execute(const std::string& sql) = 0;
    virtual std::vector<std::vector<std::string>> query(
        const std::string& sql,
        const std::vector<std::string>& params = {}) = 0;
};

// ================================================================
//  追踪服务
// ================================================================
class Tracker {
public:
    explicit Tracker(Database* db);
    ~Tracker();

    // 注册消息
    std::string register_message(
        const std::string& wx_id,
        const std::string& content,
        int64_t create_time_ms);

    // 记录已读
    void record_read(const std::string& msg_id,
                     const std::string& wx_id,
                     const std::string& ip,
                     const std::string& ua);

    // 查询已读人数
    int count_reads(const std::string& msg_id,
                    const std::string& wx_id);

    // 统计
    Stats get_stats();

    // 消息列表
    std::vector<Message> list_messages(int limit = 100);

    // 消息详情
    std::optional<Message> get_message(const std::string& msg_id);
    std::vector<ReadRecord> get_reads(const std::string& msg_id);

    // 删除
    bool delete_message(const std::string& msg_id);
    bool delete_all();

private:
    Database* db_;
    std::vector<unsigned char> transparent_gif_;
};

} // namespace rrtracker

#endif // RRTRACKER_H
