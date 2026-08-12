#include "rrtracker.h"
#include <iostream>
#include <string>
#include <cstring>
#include <ctime>
#include <sstream>

#ifdef _WIN32
    #include <winsock2.h>
#else
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <unistd.h>
    #include <arpa/inet.h>
#endif

namespace rrtracker {

// ================================================================
//  简易 HTTP 服务器 (单线程，仅用于 demo / 轻量部署)
// ================================================================
class HTTPServer {
public:
    HTTPServer(int port, Tracker* tracker)
        : port_(port), tracker_(tracker) {}

    bool start() {
#ifdef _WIN32
        WSADATA wsa;
        if (WSAStartup(MAKEWORD(2,2), &wsa) != 0) return false;
#endif
        server_fd_ = socket(AF_INET, SOCK_STREAM, 0);
        if (server_fd_ < 0) return false;

        int opt = 1;
        setsockopt(server_fd_, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

        sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = INADDR_ANY;
        addr.sin_port = htons(port_);

        if (bind(server_fd_, (sockaddr*)&addr, sizeof(addr)) < 0) return false;
        if (listen(server_fd_, 10) < 0) return false;

        std::cout << "[rrtracker-cpp] HTTP server listening on 0.0.0.0:" << port_ << std::endl;
        return true;
    }

    void run() {
        running_ = true;
        while (running_) {
            sockaddr_in client{};
            socklen_t client_len = sizeof(client);
            int client_fd = accept(server_fd_, (sockaddr*)&client, &client_len);
            if (client_fd < 0) continue;

            char buffer[8192] = {};
            int n = recv(client_fd, buffer, sizeof(buffer) - 1, 0);
            if (n <= 0) {
                close_socket(client_fd);
                continue;
            }
            buffer[n] = '\0';

            std::string response = handle_request(buffer);
            send(client_fd, response.c_str(), response.size(), 0);
            close_socket(client_fd);
        }
    }

    void stop() { running_ = false; }

private:
    int port_;
    Tracker* tracker_;
    int server_fd_ = -1;
    bool running_ = false;

    void close_socket(int fd) {
#ifdef _WIN32
        closesocket(fd);
#else
        close(fd);
#endif
    }

    std::string json_escape(const std::string& s) {
        std::string r;
        for (char c : s) {
            if (c == '"') r += "\\\"";
            else if (c == '\\') r += "\\\\";
            else if (c == '\n') r += "\\n";
            else r += c;
        }
        return r;
    }

    std::string timestamp_to_date(int64_t ts) {
        time_t t = static_cast<time_t>(ts);
        char buf[32];
        strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", localtime(&t));
        return buf;
    }

    std::string http_response(const std::string& body, int status = 200,
                               const std::string& content_type = "application/json") {
        std::string status_text = (status == 200) ? "OK" :
                                   (status == 400) ? "Bad Request" :
                                   (status == 404) ? "Not Found" : "Error";
        return "HTTP/1.1 " + std::to_string(status) + " " + status_text + "\r\n"
               "Content-Type: " + content_type + "; charset=utf-8\r\n"
               "Access-Control-Allow-Origin: *\r\n"
               "Content-Length: " + std::to_string(body.size()) + "\r\n"
               "Connection: close\r\n\r\n" + body;
    }

    // 简化的请求解析
    std::string handle_request(const std::string& raw) {
        // 提取第一行: METHOD PATH HTTP/1.1
        size_t eol = raw.find("\r\n");
        std::string first_line = raw.substr(0, eol);

        std::string method, path;
        std::istringstream iss(first_line);
        iss >> method >> path;

        // 解析查询参数
        std::string query;
        size_t qm = path.find('?');
        if (qm != std::string::npos) {
            query = path.substr(qm + 1);
            path = path.substr(0, qm);
        }

        // 解析 POST body
        std::string body;
        if (method == "POST") {
            size_t body_start = raw.find("\r\n\r\n");
            if (body_start != std::string::npos)
                body = raw.substr(body_start + 4);
        }

        // --- 路由 ---
        auto json_ok = [](const std::string& j) { return j; };

        // GET /health
        if (path == "/health")
            return http_response("{\"status\":\"ok\",\"service\":\"rrtracker-cpp\"}");

        // POST /register
        if (path == "/register" && method == "POST") {
            // 简易 JSON 解析 (生产应该用 RapidJSON/nlohmann)
            std::string wx, content;
            int64_t ct = std::time(nullptr) * 1000;

            auto extract = [&body](const std::string& key) -> std::string {
                size_t p = body.find("\"" + key + "\"");
                if (p == std::string::npos) return "";
                p = body.find(":", p);
                if (p == std::string::npos) return "";
                p++;
                while (p < body.size() && (body[p] == ' ' || body[p] == '"')) p++;
                size_t e = body.find("\"", p);
                if (e == std::string::npos) e = body.find(",", p);
                if (e == std::string::npos) e = body.find("}", p);
                return body.substr(p, e - p);
            };

            wx = extract("wxID");
            content = extract("content");
            auto ct_str = extract("createTime");
            if (!ct_str.empty()) ct = std::stoll(ct_str);

            if (wx.empty())
                return http_response("{\"error\":\"wxID required\"}", 400);

            std::string mid = tracker_->register_message(wx, content, ct);
            std::ostringstream oss;
            oss << "{\"success\":true,\"id\":\"" << mid << "\",\"wxID\":\"" << wx << "\"}";
            return http_response(oss.str());
        }

        // GET /pixel
        if (path == "/pixel") {
            std::string wx, mid;
            // 解析 query ?wxID=xxx&id=yyy
            auto get_param = [&query](const std::string& key) -> std::string {
                size_t p = query.find(key + "=");
                if (p == std::string::npos) return "";
                p += key.size() + 1;
                size_t e = query.find("&", p);
                return query.substr(p, e == std::string::npos ? query.size() - p : e - p);
            };
            wx = get_param("wxID");
            mid = get_param("id");

            if (!wx.empty() && !mid.empty())
                tracker_->record_read(mid, wx, "0.0.0.0", "");

            return http_response(
                std::string(reinterpret_cast<const char*>(
                    tracker_ ? nullptr : nullptr), 0),
                200, "image/gif");
        }

        // GET /count
        if (path == "/count") {
            auto get_param = [&query](const std::string& key) -> std::string {
                size_t p = query.find(key + "=");
                if (p == std::string::npos) return "";
                p += key.size() + 1;
                size_t e = query.find("&", p);
                return query.substr(p, e == std::string::npos ? query.size() - p : e - p);
            };
            std::string wx = get_param("wxID"), mid = get_param("id");
            int cnt = tracker_->count_reads(mid, wx);
            return http_response("{\"count\":" + std::to_string(cnt) + ",\"msg_id\":\"" + mid + "\"}");
        }

        // GET / (管理面板)
        if (path == "/") {
            auto stats = tracker_->get_stats();
            auto msgs = tracker_->list_messages(50);
            std::ostringstream html;
            html << "<!DOCTYPE html><html lang=zh-CN><head><meta charset=UTF-8>"
                 << "<meta name=viewport content='width=device-width,initial-scale=1'>"
                 << "<title>已读追踪 · C++ 后端</title>"
                 << "<style>*{margin:0;padding:0}body{font-family:system-ui;background:#f0f2f5;color:#1f2937}"
                 << ".c{max-width:1200px;margin:0 auto;padding:24px}"
                 << ".h{text-align:center;padding:30px;background:linear-gradient(135deg,#059669,#047857);color:#fff;border-radius:12px;margin-bottom:24px}"
                 << ".s{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:24px}"
                 << ".sc{background:#fff;padding:20px;border-radius:12px;text-align:center}"
                 << ".sc .n{font-size:32px;font-weight:800;color:#059669}"
                 << "th,td{padding:12px 16px;text-align:left;border-bottom:1px solid #eee}"
                 << "th{background:#f9fafb;color:#666}table{width:100%;border-collapse:collapse;background:#fff;border-radius:12px;overflow:hidden}"
                 << "</style></head><body><div class=c>"
                 << "<div class=h><h1>📬 已读追踪 (C++)</h1><p>meson + ninja 构建 · 原生高性能</p></div>"
                 << "<div class=s>"
                 << "<div class=sc><div class=n>" << stats.total_messages << "</div>总消息</div>"
                 << "<div class=sc><div class=n>" << stats.total_reads << "</div>总已读</div>"
                 << "<div class=sc><div class=n>" << stats.avg_reads << "</div>平均已读</div>"
                 << "</div><table>"
                 << "<thead><tr><th>ID</th><th>wxID</th><th>内容</th><th>已读</th><th>时间</th></tr></thead><tbody>";
            for (auto& m : msgs) {
                html << "<tr><td style='font-family:monospace;font-size:12px'>" << m.id.substr(0,16) << "…</td>"
                     << "<td>" << m.wx_id << "</td>"
                     << "<td style='max-width:300px;overflow:hidden'>" << m.content.substr(0,50) << "</td>"
                     << "<td>" << m.read_count << " 人</td>"
                     << "<td style='color:#999'>" << timestamp_to_date(m.registered_at) << "</td></tr>";
            }
            html << "</tbody></table></div></body></html>";
            return http_response(html.str(), 200, "text/html");
        }

        return http_response("{\"error\":\"not found\"}", 404);
    }
};

// ================================================================
//  Server
// ================================================================
int start_server(int port, const std::string& db_path) {
    auto* db = new SQLiteDatabase();
    if (!db->open(db_path)) {
        std::cerr << "Failed to open database: " << db_path << std::endl;
        return 1;
    }

    Tracker tracker(db);
    HTTPServer server(port, &tracker);

    if (!server.start()) {
        std::cerr << "Failed to start server on port " << port << std::endl;
        return 1;
    }

    server.run();
    return 0;
}

} // namespace rrtracker
