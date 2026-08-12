# 🛠 开发指南

## 环境要求

- Python 3.9+
- Flask 2.3+
- (C++ 后端) meson + ninja + SQLite3 + OpenSSL

## Python 后端开发

### 安装工具链

```bash
# 安装 uv
curl -LsSf https://astral.sh/uv/install.sh | sh

# 安装依赖
uv pip install --system flask

# 安装开发依赖
uv pip install --system ruff pytest pytest-cov
```

### 代码检查与格式化

```bash
# 代码检查
ruff check python/

# 代码格式化
ruff format python/
```

### 启动开发服务器

```bash
python run.py --debug
```

### 生产部署

```bash
pip install gunicorn
gunicorn -w 4 -b 0.0.0.0:5000 "python.app:create_app()"
```

---

## C++ 后端开发

### 安装工具链

```bash
# Ubuntu/Debian
apt install meson ninja-build libsqlite3-dev libssl-dev

# Termux
pkg install meson ninja clang sqlite openssl

# 或 pip
pip install meson
```

### 编译

```bash
cd cpp
meson setup builddir
meson compile -C builddir
```

### 运行

```bash
./builddir/rrtracker-server 5000 receipts.db
```

### 运行测试

```bash
cd cpp
meson setup builddir -Dtests=true
meson test -C builddir
```

---

## 目录规范

```
python/app/          # Python 应用代码
cpp/src/             # C++ 源码
scripts/             # 部署脚本
docs/                # 文档
```

## 添加新功能

1. **API 路由** → 加到 `python/app/routes.py` 的 `register_routes()` 中
2. **C++ 功能** → 加到 `cpp/src/tracker.cpp`，更新头文件
3. **文档** → 更新 `docs/API.md` 和 `README.md`
