# 📬 read-receipt-tracker

<p align="center">
  <b>轻量级消息已读追踪服务</b> — 像素埋点 · 管理后台 · 多端部署 · 可选 IP 定位
</p>

<p align="center">
  <img src="https://img.shields.io/badge/python-3.9+-blue.svg" alt="Python">
  <img src="https://img.shields.io/badge/C++-17-00599C.svg" alt="C++">
  <img src="https://img.shields.io/badge/flask-2.3+-green.svg" alt="Flask">
  <img src="https://img.shields.io/badge/license-MIT-green.svg" alt="License">
  <img src="https://img.shields.io/badge/docker-ready-2496ED.svg" alt="Docker">
  <a href="https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions/workflows/build.yml"><img src="https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions/workflows/build.yml/badge.svg" alt="Build"></a>
</p>

<p align="center">
  <a href="https://www.ifdian.net/a/zuomeng13"><img src="https://img.shields.io/badge/💖%20爱发电-支持作者-ff69b4?style=for-the-badge" alt="爱发电赞助"></a>
</p>

一个极简的 **消息已读回执追踪服务**：注册消息后得到一个 URL，将该 URL 作为 1×1 透明像素嵌入消息，对方打开时自动记录已读。提供 Web 管理面板查看统计和读取详情。

> 🎯 **定位**：为微信模块 **WeKit / WuYu / WAuxiliary / HChat** 提供已读回执后端服务。也适用于邮件营销已读率分析、消息回执追踪、网页埋点等场景。

---

## 📂 快速导航

### 📱 客户端
选择适合你微信模块的客户端方案，发送追踪消息并查看已读统计。

| 方案 | 适用模块 | 特点 | 下载 |
|------|----------|------|------|
| [WeKit APK](#wekit-版apk推荐) | WeKit | 内置已读追踪，开箱即用 | [Releases](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases) |
| [Java 插件版](#java-插件版支持-wekit--wauxiliary--wuyu--hchat) | WeKit / WA / WuYu / HChat | 无需编译 APK，服务端+客户端双插件 | [Releases](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases) |

### 🖥️ 服务器
选择一种方式部署已读追踪后端服务。

| 方案 | 平台 | 特点 |
|------|------|------|
| [Linux 服务器](#linux-服务器) | Linux | Python / C++ 双后端，systemd 托管 |
| [Windows](#windows) | Windows | Python 后端，简单易用 |
| [Java 插件版服务器](#java-插件版服务器) | 微信模块内 | 内置 HTTP + 隧道，无需额外服务器 |
| [WeKit 内置服务器](#wekit-内置服务器) | WeKit APK | 模块内直接启用 |
| [Termux / Android](#termux--android手机本地部署) | Android 手机 | 一键脚本，零下载全内嵌 |
| [Docker](#docker) | 跨平台 | 容器化部署 |

---

## ✨ 核心特性

| 特性 | 说明 |
|------|------|
| 🔌 **一行注册** | `POST /register` 传入消息内容，返回追踪链接 |
| 👁 **透明无感** | 1×1 透明 GIF，用户完全感知不到 |
| 📊 **管理后台** | Web 界面查看统计、消息列表、搜索、导出 CSV |
| 🌍 **IP 定位** | 自动获取读取者国家/省份/城市/运营商（三接口备份） |
| ⚡ **请求限流** | IP 级频率限制，防止滥用 |
| 🔐 **API 认证** | 管理接口支持 API Key 保护 |
| 🔒 **智能去重** | 同一消息 + 同一 IP 只计一次已读 |
| 📦 **双语言后端** | Python (Flask) + C++ (原生 HTTP) |

---

## 📱 客户端

<details>
<summary>**WeKit 版（APK，推荐）**</summary>

基于 [WeKit](https://github.com/Ujhhgtg/WeKit) 的微信 Xposed 模块，已修复 DEX 缓存更新问题，内置已读追踪功能，开箱即用。

- **上游仓库**: https://github.com/Ujhhgtg/WeKit
- **特性**: 微信内直接启用已读追踪，消息注册、已读统计、访客信息一体化
- **环境要求**: Android 9.0+ / Root + LSPosed（或 Zygisk）/ 微信 8.0.65 - 8.0.76
- **下载**: [Releases 页面](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases) 下载 `wekit-*.apk`，CI 自动构建
- **源码**: [`wekit-module/`](wekit-module/)

</details>

<details>
<summary>**Java 插件版（支持 WeKit / WAuxiliary / WuYu / HChat）**</summary>

轻量级 Java 插件，无需编译 APK，在支持 Java 脚本的微信模块中加载即可使用。包含**服务端**和**客户端**两个配套插件：

| 插件 | 作用 | 下载 | 源码 |
|------|------|------|------|
| 已读服务器（服务端） | 内置 HTTP 服务 + cloudflared 公网隧道 + Web 控制台 | `read-tracker-java-server.zip` | [`plugins/java-read-tracker/`](plugins/java-read-tracker/) |
| 已读追踪（客户端） | `#消息` 发送追踪卡片，`/已读` 查询人数，已读统计在浏览器控制台查看 | `read-tracker-java-client.zip` | [`plugins/read-tracker-client/`](plugins/read-tracker-client/) |

- **兼容模块**: WeKit、WAuxiliary (WA)、WuYu、HChat 等
- **使用**: 先装服务端并启动隧道，再装客户端并配置服务器地址
- **下载**: [Releases 页面](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases)

</details>

---

## 🖥️ 服务器部署

<details>
<summary>**Linux 服务器**</summary>

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

</details>

<details>
<summary>**Windows**</summary>

使用 Python 后端，安装 Python 3.9+ 后运行：
```powershell
pip install flask
python run.py
```
打开 `http://localhost:5000` 即可访问管理面板。需要公网访问时配合 cloudflared 或内网穿透工具。

</details>

<details>
<summary>**Java 插件版服务器**</summary>

无需单独部署服务器，直接在微信模块中加载「已读服务器」Java 插件即可，内置 HTTP 服务和 cloudflared 公网隧道，启动后自动获取公网地址。适合不想额外准备服务器的用户。

- 下载: [Releases](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases) → `read-tracker-java-server.zip`
- 支持模块: WeKit / WAuxiliary / WuYu / HChat

</details>

<details>
<summary>**WeKit 内置服务器**</summary>

使用 WeKit APK 的用户，模块内已内置已读追踪服务端能力，在 WeKit 设置中启用即可，无需额外部署。

</details>

<details>
<summary>**Termux / Android（手机本地部署）**</summary>

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

</details>

<details>
<summary>**Docker**</summary>

```bash
docker compose up -d
# 或
docker build -t read-receipt-tracker .
docker run -d -p 5000:5000 -v $(pwd)/data:/app/data read-receipt-tracker
```

</details>

> 💡 所有部署形态均可用环境变量 `ENABLE_GEO=0` 关闭 IP 定位（Lite 模式）。

---

## 🌐 公网访问 & Cloudflare Tunnel

<details>
<summary>**我是否需要 Tunnel？**</summary>

| 部署场景 | 需要 Tunnel？ | 说明 |
|----------|:---:|------|
| **VPS / 云服务器** | ❌ | 服务器自带公网 IP，直接访问 |
| **本地电脑** | ❌ | 本地测试用 `localhost:5000` |
| **树莓派 / 内网服务器** | ✅ | 没有公网 IP，需要 Tunnel |
| **Termux (Android)** | ✅ | 手机网络没有公网 IP |

</details>

<details>
<summary>**启用 Tunnel (Linux)**</summary>

```bash
bash scripts/setup-linux.sh --tunnel   # 一键部署 + Tunnel
cat .tunnel_url.txt                    # 查看实时公网地址
```

Tunnel 脚本自动：安装 cloudflared → 启动保活 → 网络切换重建 → 写入 `.tunnel_url.txt`

</details>

---

## 📡 API 文档

<details>
<summary>**注册消息**</summary>

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

</details>

<details>
<summary>**已读追踪像素**</summary>

```http
GET /pixel?wxId=user123&id=a1b2c3d4...
```
返回 1×1 透明 GIF，可嵌入 HTML 邮件或网页。

</details>

<details>
<summary>**查询已读数**</summary>

```http
GET /count?wxId=user123&id=a1b2c3d4...
```
```json
{ "count": 5, "msg_id": "a1b2c3d4..." }
```

</details>

<details>
<summary>**批量查询 / 健康检查**</summary>

```http
GET /batch-status?ids=id1,id2,id3
GET /health
```

</details>

---

## 📊 管理后台

| 页面 | 路由 | 说明 |
|------|------|------|
| 仪表盘 | `/` | 统计 + 消息列表 + 搜索 + CSV 导出 |
| 消息详情 | `/message/<id>` | 消息内容 + IP/地理位置/UA/时间 |
| 健康检查 | `/health` | 服务健康状态 |

管理接口（需 API Key）：`POST /api/delete/<id>`、`POST /api/delete-all`

---

## 🗂 项目结构

```
read-receipt-tracker/
├── python/                    # Python 后端 (Flask)
├── cpp/                       # C++ 后端 (meson + ninja)
├── scripts/                   # 部署脚本 (Termux / Linux)
├── plugins/
│   ├── java-read-tracker/     # Java 服务端插件
│   └── read-tracker-client/   # Java 客户端插件 (HChat/WA/WuYu)
├── wekit-module/              # WeKit 模块源码 (用于 CI 构建 APK)
├── docs/                      # 文档
├── .github/workflows/         # CI 自动构建 & 发布
├── run.py                     # Python 启动入口
├── Dockerfile / docker-compose.yml
└── README.md
```

---

## 🔧 配置

复制配置模板：`cp .env.example .env`

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DATABASE_PATH` | `receipts.db` | 数据库路径 |
| `API_KEY` | (空) | 管理后台认证密钥 |
| `ENABLE_GEO` | `1` | IP 定位开关 |
| `RATE_LIMIT_PER_MINUTE` | 60 | 请求频率限制 |
| `HOST` | `0.0.0.0` | 监听地址 |
| `PORT` | 5000 | 监听端口 |

---

## 🛠 技术栈

| 层级 | Python | C++ |
|------|--------|-----|
| 框架 | Flask 2.3+ | 自建 HTTP 服务 |
| 数据库 | SQLite (WAL) | SQLite3 C API |
| 包管理 | uv | meson + ninja |
| 部署 | Docker / systemd / Termux | 原生二进制 |

---

## 🌍 IP 定位（默认开启）

无需配置和 Key，已读记录自动附带中文定位：国家 / 省份 / 城市 / 运营商。

三级接口自动备份：ip-api.com → ipwho.is → ipinfo.io，超时静默降级。

关闭定位：`ENABLE_GEO=0 python app.py`

---

## 📄 License

MIT © [gaigebeckmanChristinaJames](https://github.com/gaigebeckmanChristinaJames)
