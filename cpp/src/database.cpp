#include "rrtracker.h"
#include <sqlite3.h>
#include <stdexcept>

namespace rrtracker {

class SQLiteDatabase : public Database {
public:
    ~SQLiteDatabase() override { if (db_) sqlite3_close(db_); }

    bool open(const std::string& path) override {
        int rc = sqlite3_open(path.c_str(), &db_);
        if (rc != SQLITE_OK) return false;
        sqlite3_exec(db_, "PRAGMA journal_mode=WAL", nullptr, nullptr, nullptr);
        sqlite3_exec(db_, "PRAGMA foreign_keys=ON", nullptr, nullptr, nullptr);
        return true;
    }

    bool execute(const std::string& sql) override {
        char* err = nullptr;
        int rc = sqlite3_exec(db_, sql.c_str(), nullptr, nullptr, &err);
        if (rc != SQLITE_OK) { if (err) sqlite3_free(err); return false; }
        return true;
    }

    std::vector<std::vector<std::string>> query(
        const std::string& sql,
        const std::vector<std::string>& params) override {
        std::vector<std::vector<std::string>> result;
        sqlite3_stmt* stmt = nullptr;
        if (sqlite3_prepare_v2(db_, sql.c_str(), -1, &stmt, nullptr) != SQLITE_OK)
            return result;

        for (size_t i = 0; i < params.size(); i++)
            sqlite3_bind_text(stmt, i + 1, params[i].c_str(), -1, SQLITE_TRANSIENT);

        while (sqlite3_step(stmt) == SQLITE_ROW) {
            std::vector<std::string> row;
            for (int c = 0; c < sqlite3_column_count(stmt); c++)
                row.push_back(
                    sqlite3_column_text(stmt, c)
                        ? std::string(reinterpret_cast<const char*>(sqlite3_column_text(stmt, c)))
                        : "");
            result.push_back(row);
        }
        sqlite3_finalize(stmt);
        return result;
    }

    sqlite3* raw() { return db_; }

private:
    sqlite3* db_ = nullptr;
};

} // namespace rrtracker
