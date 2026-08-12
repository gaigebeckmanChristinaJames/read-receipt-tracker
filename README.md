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
| 🌍 **IP 定位** | 可选 GeoLite2 数据库，自动获取读取者国家/地区/城市 |
| ⚡ **请求限流** | IP 级频率限制，防止滥用 |
| 🔐 **API 认证** | 管理接口支持 API Key 保护 |
| 🔒 **智能去重** | 同一消息 + 同一 IP 只计一次已读 |
| 🧹 **自动清理** | Termux 支持定期清理过期数据 |
| 📦 **双语言后端** | Python (Flask) + C++ (原生 HTTP)，按需选择 |
| 🐳 **三端部署** | Docker / Linux (systemd) / Android (Termux + Tunnel) |
| 🛠 **现代工具链** | uv 包管理 · ruff 代码检测 · meson + ninja 构建 |

---

## 👀 30 秒体验

### 1. Termux 一键部署 (推荐，零下载全内嵌)

**标准版（含 IP 定位）：**

```bash
bash <(curl -fL "https://cdn.jsdelivr.net/gh/gaigebeckmanChristinaJames/read-receipt-tracker@main/scripts/ultimate-setup.sh")
```

**Lite 版（无 IP 定位）：**

```bash
bash <(curl -fL "https://cdn.jsdelivr.net/gh/gaigebeckmanChristinaJames/read-receipt-tracker@main/scripts/ultimate-setup-lite.sh")
```

> 💡 全新 Termux 首次使用先装 curl：
>
> ```bash
> pkg update -y && pkg install curl -y
> ```

**一条命令搞定**：自动配置清华源 → 安装 Python + Flask → 内嵌代码落地 → 服务前台运行（屏幕实时显示日志与控制台地址 `http://127.0.0.1:5000`）。

> 💡 全部代码内嵌在脚本中，不下载任何仓库文件、不依赖 GitHub 直连（走 jsDelivr CDN）、不写 /tmp，彻底避免网络超时和权限报错。
>
> 🌍 **标准版** 内置 IP 定位（中文省市 + 运营商 + 经纬度，三接口自动备份）；**Lite 版** 无定位功能，零外部请求、零延迟，纯追踪。
>
> 其他部署形态（Linux / Docker）也可用环境变量开关：
>
> ```bash
> ENABLE_GEO=0 python app.py     # 关闭定位 = Lite 模式
> ```
>
> 🔗 脚本与隧道**完全分离**。需要公网地址时，另开一个 Termux 会话：
>
> ```bash
> pkg install cloudflared
> ```
>
> ```bash
> cloudflared tunnel --url http://127.0.0.1:5000
> ```
> 隧道日志会显示 `https://xxx.trycloudflare.com` 公网地址。

### 2. Python 后端 (Linux 服务器)

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

### 3. C++ 后端

```bash
pip install meson  # 或 apt install meson ninja-build
apt install libsqlite3-dev libssl-dev

cd cpp
meson setup builddir
meson compile -C builddir
./builddir/rrtracker-server 5000 receipts.db
```

### 4. Docker

```bash
# 构建
docker compose up -d

# 或
docker build -t read-receipt-tracker .
docker run -d -p 5000:5000 -v $(pwd)/data:/app/data read-receipt-tracker
```

### 5. Linux 服务器一键部署

```bash
bash scripts/setup-linux.sh                # systemd 托管（推荐）
bash scripts/setup-linux.sh --foreground   # 前台运行 + cloudflared 日志直显
bash scripts/setup-linux.sh --tunnel       # 无公网 IP 时的隧道保活模式
```

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
