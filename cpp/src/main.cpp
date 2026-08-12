#include <cstdlib>
#include <iostream>
#include "rrtracker.h"

int main(int argc, char* argv[]) {
    int port = 5000;
    std::string db_path = "receipts.db";

    if (argc > 1) port = std::atoi(argv[1]);
    if (argc > 2) db_path = argv[2];

    std::cout << "=== rrtracker-cpp v2.1.0 ===" << std::endl;
    std::cout << "Port: " << port << std::endl;
    std::cout << "DB:   " << db_path << std::endl;
    std::cout << "=============================" << std::endl;

    return rrtracker::start_server(port, db_path);
}
