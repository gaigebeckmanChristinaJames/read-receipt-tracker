# 📬 read-receipt-tracker

<p align="center">
  <b>轻量级消息已读追踪服务</b> — 像素埋点 · 管理后台 · 多端部署 · 可选 IP 定位
</p>

<p align="center">
  <img src="https://img.shields.io/badge/python-3.9+-blue.svg" alt="Python">
  <img src="https://img.shields.io/badge/C++-17-00599C.svg" alt="C++">
  <img src="https://img.shields.io/badge/flask-2.3+-green.svg" alt="Flask">
  <img src="https://img.shields.io/badge/uv-package%20manager-purple.svg" alt="uv">
  <img src="https://img.shields.io/badge/ruff-linter-black.svg" alt="ruff">
  <img src="https://img.shields.io/badge/meson-build-5f5f5f.svg" alt="meson">
  <img src="https://img.shields.io/badge/license-MIT-green.svg" alt="License">
  <img src="https://img.shields.io/badge/docker-ready-2496ED.svg" alt="Docker">
</p>

一个极简的 **消息已读回执追踪服务**：注册消息后得到一个 URL，将该 URL 作为 1×1 透明像素嵌入邮件或网页，对方打开时自动记录已读。提供漂亮的 Web 管理面板查看统计和每条消息的读取详情。

> 🎯 **定位**：为微信模块 **WeKit** / **WuYu** 提供已读回执后端服务。也适用于邮件营销已读率分析、消息回执追踪、网页埋点等场景。

---

## ✨ 为什么选择它

| 特性 | 说明 |
|------|------|
| 🔌 **一行注册** | `POST /register` 传入消息内容，返回追踪链接，直接嵌入即可 |
| 👁 **透明无感** | 1×1 透明 GIF，用户完全感知不到 |
| 📊 **管理后台** | Web 界面查看统计、消息列表、搜索、一键导出 CSV |
| 🌍 **IP 定位** | 自动获取读取者国家/省份/城市/运营商（中文，三接口备份，可开关） |
| ⚡ **请求限流** | IP 级频率限制，防止滥用 |
| 🔐 **API 认证** | 管理接口支持 API Key 保护 |
| 🔒 **智能去重** | 同一消息 + 同一 IP 只计一次已读 |
| 🧹 **自动清理** | Termux 支持定期清理过期数据 |
| 📦 **双语言后端** | Python (Flask) + C++ (原生 HTTP)，按需选择 |
| 🐳 **三端部署** | Docker / Linux (systemd) / Android (Termux + Tunnel) |
| 🛠 **现代工具链** | uv 包管理 · ruff 代码检测 · meson + ninja 构建 |

---

## 📱 客户端

选择适合你微信模块的客户端方案，发送追踪消息并查看已读统计。

### WeKit 版（APK，推荐）

基于 [WeKit](https://github.com/Ujhhgtg/WeKit) 的微信 Xposed 模块，已修复 DEX 缓存更新问题，内置已读追踪功能，开箱即用。

- **上游仓库**: https://github.com/Ujhhgtg/WeKit
- **特性**: 微信内直接启用已读追踪，消息注册、已读统计、访客信息一体化
- **环境要求**: Android 9.0+ / Root + LSPosed（或 Zygisk）/ 微信 8.0.65 - 8.0.76
- **下载**: [Releases 页面](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases) 下载 `wekit-*.apk`，CI 自动构建
- **源码**: [`wekit-module/`](wekit-module/)

### Java 插件版（支持 WeKit / WAuxiliary / WuYu / HChat）

轻量级 Java 插件，无需编译 APK，在支持 Java 脚本的微信模块中加载即可使用。包含**服务端**和**客户端**两个配套插件：

| 插件 | 作用 | 下载 | 源码 |
|------|------|------|------|
| 已读服务器（服务端） | 内置 HTTP 服务 + cloudflared 公网隧道 + Web 控制台 | `read-tracker-java-server.zip` | [`plugins/java-read-tracker/`](plugins/java-read-tracker/) |
| 已读追踪（客户端） | `#消息` 发送追踪卡片，`/已读` 查询人数，已读统计在浏览器控制台查看 | `read-tracker-java-client.zip` | [`plugins/read-tracker-client/`](plugins/read-tracker-client/) |

- **兼容模块**: WeKit、WAuxiliary (WA)、WuYu、HChat 等
- **使用**: 先装服务端并启动隧道，再装客户端并配置服务器地址
- **下载**: [Releases 页面](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases)

### WuYu 版

WuYu 模块用户请直接使用上方 **Java 插件版**，将插件放入 WuYu 的脚本目录即可，服务端和客户端插件均兼容 WuYu。

---

## 🖥️ 服务器部署

选择一种方式部署已读追踪后端服务。

### Linux 服务器

支持 Python 和 C++ 两种后端实现。

**Python 后端（推荐）：**
```bash
git clone https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker.git
cd read-receipt-tracker
pip install flask
python run.py
```

**C++ 后端（高性能）：**
```bash
apt install meson ninja-build libsqlite3-dev libssl-dev
cd cpp
meson setup builddir && meson compile -C builddir
./builddir/rrtracker-server 5000 receipts.db
```

**一键部署（systemd 托管）：**
```bash
bash scripts/setup-linux.sh                # 后台托管
bash scripts/setup-linux.sh --foreground   # 前台运行
bash scripts/setup-linux.sh --tunnel       # 自动隧道保活
```

### Windows

使用 Python 后端，安装 Python 3.9+ 后运行：
```powershell
pip install flask
python run.py
```
打开 `http://localhost:5000` 即可访问管理面板。需要公网访问时配合 cloudflared 或内网穿透工具。

### Java 插件版服务器

无需单独部署服务器，直接在微信模块中加载「已读服务器」Java 插件即可，内置 HTTP 服务和 cloudflared 公网隧道，启动后自动获取公网地址。适合不想额外准备服务器的用户。

- 下载: [Releases](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases) → `read-tracker-java-server.zip`
- 支持模块: WeKit / WAuxiliary / WuYu / HChat

### WeKit 内置服务器

使用 WeKit APK 的用户，模块内已内置已读追踪服务端能力，在 WeKit 设置中启用即可，无需额外部署。

### Termux / Android（手机本地部署）

**标准版（含 IP 定位）：**
```bash
pkg update -y && pkg install curl -y
bash <(curl -fL "https://cdn.jsdelivr.net/gh/gaigebeckmanChristinaJames/read-receipt-tracker@main/scripts/ultimate-setup.sh")
```

**Lite 版（无 IP 定位，零外部请求）：**
```bash
bash <(curl -fL "https://cdn.jsdelivr.net/gh/gaigebeckmanChristinaJames/read-receipt-tracker@main/scripts/ultimate-setup-lite.sh")
```

一条命令完成：配置清华源 → 安装 Python + Flask → 落地代码 → 前台运行，屏幕显示日志与控制台地址 `http://127.0.0.1:5000`。

需要公网地址时另开会话：
```bash
pkg install cloudflared
cloudflared tunnel --protocol http2 --url http://127.0.0.1:5000
```

### Docker

```bash
docker compose up -d
# 或
docker build -t read-receipt-tracker .
docker run -d -p 5000:5000 -v $(pwd)/data:/app/data read-receipt-tracker
```

> 💡 所有部署形态均可用环境变量 `ENABLE_GEO=0` 关闭 IP 定位（Lite 模式）。

---

## 🌐 公网访问 & Cloudflare Tunnel

### 我是否需要 Tunnel？

| 部署场景 | 需要 Tunnel？ | 说明 |
|----------|:---:|------|
| **VPS / 云服务器** (阿里云、腾讯云、AWS 等) | ❌ | 服务器自带公网 IP，直接 `http://你的IP:5000` 即可 |
| **本地电脑** (127.0.0.1) | ❌ | 本地测试用 `localhost:5000`，无需内网穿透 |
| **树莓派 / 内网服务器** | ✅ | 没有公网 IP，需要 Tunnel 才能让追踪像素被外部访问 |
| **Termux (Android 手机)** | ✅ | 手机网络没有公网 IP，脚本默认自动配置 |

### 启用 Tunnel (Linux)

```bash
# 一键部署 + Tunnel
bash scripts/setup-linux.sh --tunnel

# 查看实时公网地址
cat .tunnel_url.txt
```

Tunnel 脚本会自动：
1. 安装 `cloudflared`
2. 启动保活守护进程
3. 网络切换时自动重建隧道
4. 将公网 URL 写入 `.tunnel_url.txt`

### 查看当前 Tunnel 地址

```bash
cat .tunnel_url.txt
# 示例输出: https://example-name.trycloudflare.com
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
| `ENABLE_GEO` | `1` | IP 定位开关（1=开启 0=关闭） |
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
| IP 定位 | 免费三接口自动备份（无需 Key） | — |

---

## 🌍 IP 定位（默认开启）

无需任何配置和 Key，已读记录自动附带中文定位信息：

| 字段 | 说明 |
|------|------|
| country | 国家（如：中国） |
| region | 省份（如：上海市） |
| city | 城市（如：上海） |
| isp | 运营商（如：China Mobile） |

**三级接口自动备份**：ip-api.com（中文）→ ipwho.is（中文）→ ipinfo.io（英文兜底），支持 IPv4/IPv6，超时静默降级。

**关闭定位（Lite 模式）**：

```bash
ENABLE_GEO=0 python app.py
```

---

## 📄 License

MIT © [gaigebeckmanChristinaJames](https://github.com/gaigebeckmanChristinaJames)
