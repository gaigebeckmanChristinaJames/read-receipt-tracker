project('read-receipt-tracker-cpp', 'cpp',
  version: '2.1.0',
  license: 'MIT',
  default_options: [
    'cpp_std=c++17',
    'warning_level=3',
    'buildtype=release',
  ],
)

# SQLite3 依赖
sqlite3_dep = dependency('sqlite3', required: true)

# 主库
cpp_lib = shared_library('rrtracker',
  sources: files('src/server.cpp', 'src/tracker.cpp', 'src/database.cpp'),
  dependencies: [sqlite3_dep],
  install: true,
)

# 主程序
executable('rrtracker-server',
  sources: files('src/main.cpp'),
  link_with: cpp_lib,
  dependencies: [sqlite3_dep],
  install: true,
)

# 静态库
cpp_static = static_library('rrtracker_static',
  sources: files('src/server.cpp', 'src/tracker.cpp', 'src/database.cpp'),
  dependencies: [sqlite3_dep],
  install: true,
)

# 安装头文件
install_headers('src/rrtracker.h', subdir: 'rrtracker')

# 测试
if get_option('tests')
  test('test_database', executable('test_database',
    sources: files('test/test_database.cpp'),
    link_with: cpp_lib,
    dependencies: [sqlite3_dep],
  ))
  test('test_tracker', executable('test_tracker',
    sources: files('test/test_tracker.cpp'),
    link_with: cpp_lib,
    dependencies: [sqlite3_dep],
  ))
endif
