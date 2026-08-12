# 📬 read-receipt-tracker

> 轻量级消息已读追踪服务 · Python(uv) + C++(meson) 双实现 <br/>
> Flask 像素埋点 · 管理后台 · 三端部署 · 可选 IP 定位

[![Python](https://img.shields.io/badge/python-3.9+-blue.svg)](https://www.python.org/)
[![C++](https://img.shields.io/badge/C++-17-00599C.svg)](https://isocpp.org/)
[![Flask](https://img.shields.io/badge/flask-2.3+-green.svg)](https://flask.palletsprojects.com/)
[![uv](https://img.shields.io/badge/uv-package%20manager-purple.svg)](https://astral.sh/uv)
[![ruff](https://img.shields.io/badge/ruff-linter-black.svg)](https://astral.sh/ruff)
[![meson](https://img.shields.io/badge/meson-build-5f5f5f.svg)](https://mesonbuild.com/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Docker](https://img.shields.io/badge/docker-ready-2496ED.svg)](Dockerfile)

通过嵌入 **1×1 透明像素** 记录邮件/消息是否被打开。提供 Web 管理后台，支持 Termux (Android)、Linux 服务器、Docker 三种部署方式。

---

## ✨ 特性

- 🔌 **一行注册** — `POST /register` 注册消息，返回追踪像素 URL
- 👁 **透明追踪** — `/pixel` 端点返回 1x1 透明 GIF，无感记录已读
- 📊 **管理面板** — 内置 Web 后台，消息列表 + 统计 + 搜索 + CSV 导出
- 🌍 **IP 定位** (可选) — 设置 GeoLite2 数据库即可获取读取者国家/地区/城市
- ⚡ **请求限流** — IP 级频率限制 (可配置)
- 🔐 **API 认证** — 管理接口支持 API Key 保护
- 🐳 **Docker** — 一键构建，非 root 用户运行
- 📱 **Termux** — Android 手机端一键脚本 + Cloudflare Tunnel
- 📦 **双语言后端**:
  - **Python** — uv 管理依赖 + ruff 代码检测
  - **C++** — meson + ninja 构建，原生高性能
- 🔒 **去重** — 同一消息 + 同一 IP 只计一次已读

---

## 🚀 快速开始

### 1. Python 后端 (推荐)

```bash
git clone https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker.git
cd read-receipt-tracker

# 方式 A: uv
curl -LsSf https://astral.sh/uv/install.sh | sh
uv pip install --system flask
python run.py

# 方式 B: pip
pip install flask
python run.py
```

打开 `http://localhost:5000` 即可看到管理面板。

### 2. C++ 后端

```bash
pip install meson  # 或 apt install meson ninja-build
apt install libsqlite3-dev libssl-dev

cd cpp
meson setup builddir
meson compile -C builddir
./builddir/rrtracker-server 5000 receipts.db
```

### 3. Docker

```bash
# 构建
docker compose up -d

# 或
docker build -t read-receipt-tracker .
docker run -d -p 5000:5000 -v $(pwd)/data:/app/data read-receipt-tracker
```

### 4. Termux (Android)

```bash
bash <(curl -s https://raw.githubusercontent.com/gaigebeckmanChristinaJames/read-receipt-tracker/main/scripts/setup-termux.sh)
```

---

## 📡 API 文档

### 注册消息

```http
POST /register
Content-Type: application/json

{
    "wxId": "user123",
    "content": "你好，这是一条测试消息",
    "createTime": 1700000000000
}
```

**响应：**

```json
{
    "success": true,
    "id": "a1b2c3d4e5f6...",
    "wxId": "user123",
    "pixel_url": "http://your-server:5000/pixel?wxId=user123&id=a1b2c3d4..."
}
```

### 已读追踪像素

```http
GET /pixel?wxId=user123&id=a1b2c3d4...
```

返回 1×1 透明 GIF，可嵌入 HTML 邮件或网页。

### 查询已读数

```http
GET /count?wxId=user123&id=a1b2c3d4...
```

```json
{ "count": 5, "msg_id": "a1b2c3d4..." }
```

### 批量查询

```http
GET /batch-status?ids=id1,id2,id3
```

```json
{ "statuses": { "id1": 3, "id2": 0, "id3": 7 } }
```

### 健康检查

```http
GET /health
```

---

## 📊 管理后台

| 页面 | 路由 | 说明 |
|------|------|------|
| 仪表盘 | `/` | 统计 + 消息列表 + 搜索 + CSV 导出 |
| 消息详情 | `/message/<id>` | 消息内容 + IP/地理位置/UA/时间 |
| 健康检查 | `/health` | 服务健康状态 |

管理接口（需 API Key）：
- `POST /api/delete/<id>` — 删除单条
- `POST /api/delete-all` — 清空全部

---

## 🗂 项目结构

```
read-receipt-tracker/
├── python/                    # Python 后端
│   ├── app/
│   │   ├── __init__.py        # 入口，version 2.1.0
│   │   ├── app.py             # Flask 应用工厂
│   │   ├── database.py        # SQLite 数据库层
│   │   ├── routes.py          # API 路由 + 管理后台
│   │   ├── utils.py           # 工具 + IP 定位
│   │   └── templates/
│   │       ├── index.html     # 管理后台首页
│   │       ├── detail.html    # 消息详情
│   │       └── error.html     # 错误页面
├── cpp/                       # C++ 后端 (meson + ninja)
│   ├── meson.build
│   ├── src/
│   │   ├── rrtracker.h        # 头文件
│   │   ├── main.cpp           # 入口
│   │   ├── server.cpp         # HTTP 服务器
│   │   ├── tracker.cpp        # 追踪核心逻辑
│   │   └── database.cpp       # SQLite 封装
│   └── test/
│       └── test_tracker.cpp   # 单元测试
├── scripts/
│   ├── setup-termux.sh        # Termux 一键部署
│   └── setup-linux.sh         # Linux 服务器部署
├── docs/                      # 文档
│   ├── API.md                 # API 详细文档
│   ├── DEVELOPMENT.md         # 开发指南
│   └── TOOLCHAIN.md           # 构建工具链教程
├── run.py                     # Python 启动入口
├── pyproject.toml             # Python 项目配置 (uv + ruff)
├── Dockerfile
├── docker-compose.yml
├── .env.example
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🔧 配置

复制配置模板：

```bash
cp .env.example .env
# 编辑 .env
```

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DATABASE_PATH` | `receipts.db` | 数据库路径 |
| `API_KEY` | (空) | 管理后台认证密钥 |
| `GEOIP_DB` | (空) | GeoLite2-City.mmdb 路径 (启用 IP 定位) |
| `RATE_LIMIT_PER_MINUTE` | 60 | 请求频率限制 |
| `HOST` | `0.0.0.0` | 监听地址 |
| `PORT` | 5000 | 监听端口 |

---

## 🔧 消息 ID 算法

```
SHA-256(wxId + '\0' + content + '\0' + createTime)
```

- 相同的 wxId + content + createTime → 相同 ID
- `INSERT OR IGNORE` 保证幂等性

---

## 🛠 技术栈

| 层级 | Python | C++ |
|------|--------|-----|
| 框架 | Flask 2.3+ | 自建 HTTP 服务 |
| 数据库 | SQLite (WAL) | SQLite3 C API |
| 包管理 | uv | meson + ninja |
| 代码检查 | ruff | clang-tidy (可选) |
| 部署 | Docker / systemd / Termux | 原生二进制 |
| IP 定位 | maxminddb (可选) | — |

---

## 🌍 IP 定位（可选）

启用 IP 地理位置功能：

```bash
# 1. 注册 MaxMind 账号并下载 GeoLite2-City.mmdb
#    https://www.maxmind.com/en/account/

# 2. 安装依赖
pip install maxminddb

# 3. 配置
echo "GEOIP_DB=./data/GeoLite2-City.mmdb" >> .env

# 4. 重启服务
```

启用后已读记录会包含国家/地区/城市信息，在管理后台详情页可见。

---

## 📄 License

MIT © [gaigebeckmanChristinaJames](https://github.com/gaigebeckmanChristinaJames)
