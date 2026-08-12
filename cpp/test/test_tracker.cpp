#include "rrtracker.h"
#include <cassert>
#include <iostream>
#include <cstring>
#include <cstdlib>

using namespace rrtracker;

class MockDatabase : public Database {
public:
    bool open(const std::string&) override { return true; }
    bool execute(const std::string&) override { return true; }

    std::vector<std::vector<std::string>> query(
        const std::string&, const std::vector<std::string>&) override {
        return {{"3"}}; // mock count
    }
};

int main() {
    MockDatabase db;
    Tracker tracker(&db);

    // 测试统计
    auto stats = tracker.get_stats();
    assert(stats.total_messages >= 0);

    // 测试已读计数
    int cnt = tracker.count_reads("test-id", "user1");
    assert(cnt == 3);

    std::cout << "✅ All C++ tracker tests passed" << std::endl;
    return 0;
}
