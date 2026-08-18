<div align="center">

# 📬 read-receipt-tracker

### 轻量级消息已读追踪服务

**像素埋点 · 管理后台 · 多端部署 · 可选 IP 定位**

<br>

[![Build Status](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions/workflows/build.yml/badge.svg)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions/workflows/build.yml)
[![Downloads](https://img.shields.io/github/downloads/gaigebeckmanChristinaJames/read-receipt-tracker/total?color=green&label=Downloads)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)
[![Stars](https://img.shields.io/github/stars/gaigebeckmanChristinaJames/read-receipt-tracker?style=flat&color=yellow)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/stargazers)
[![Forks](https://img.shields.io/github/forks/gaigebeckmanChristinaJames/read-receipt-tracker?style=flat&color=blue)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/network/members)
[![Python](https://img.shields.io/badge/Python-3.9+-3776AB?logo=python&logoColor=white)]()
[![C++](https://img.shields.io/badge/C++-17-00599C?logo=cplusplus&logoColor=white)]()
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()

<br>

[**📦 立即下载**](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)
&nbsp;&nbsp;·&nbsp;&nbsp;
[**📖 查看文档**](#-简介)
&nbsp;&nbsp;·&nbsp;&nbsp;
[**💖 爱发电赞助**](https://www.ifdian.net/a/zuomeng13)

</div>

---

## 📋 目录

- [简介](#-简介)
- [核心特性](#-核心特性)
- [快速开始](#-快速开始)
- [下载](#-下载)
- [客户端方案](#-客户端方案)
- [服务器部署](#️-服务器部署)
- [公网访问](#-公网访问)
- [API 文档](#-api-文档)
- [管理后台](#-管理后台)
- [项目结构](#-项目结构)
- [配置](#-配置)
- [技术栈](#-技术栈)
- [常见问题](#-常见问题)
- [贡献](#-贡献)
- [许可证](#-许可证)

---

## 📖 简介

一个极简的 **消息已读回执追踪服务**：注册消息后得到一个 URL，将该 URL 作为 1×1 透明像素嵌入消息，对方打开时自动记录已读。提供 Web 管理面板查看统计和每条消息的读取详情。

> 🎯 **适用场景**
> - 为微信模块 **WeKit / WuYu / WAuxiliary / HChat** 提供已读回执后端
> - 邮件营销已读率分析
> - 消息回执追踪
> - 网页埋点统计

---

## ✨ 核心特性

<div align="center">

| 🔌 一行注册 | 👁 透明无感 | 📊 管理后台 |
|:---:|:---:|:---:|
| `POST /register` 传入消息内容，返回追踪链接 | 1×1 透明 GIF，用户完全感知不到 | Web 界面查看统计、消息列表、搜索、导出 CSV |

| 🌍 IP 定位 | ⚡ 请求限流 | 🔐 API 认证 |
|:---:|:---:|:---:|
| 自动获取读取者国家/省份/城市/运营商 | IP 级频率限制，防止滥用 | 管理接口支持 API Key 保护 |

| 🔒 智能去重 | 📦 双语言后端 | 🐳 多端部署 |
|:---:|:---:|:---:|
| 同一消息 + 同一 IP 只计一次已读 | Python (Flask) + C++ (原生 HTTP) | Docker / Linux / Termux / Java 插件 |

</div>

---

## 🚀 快速开始

### 方式一：WeKit APK（最简单，推荐）

1. 下载 [最新版 WeKit APK](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)
2. 在 LSPosed 中启用模块并作用于微信
3. 重启微信，在 WeKit 设置中启用已读追踪
4. 发送消息时自动生成追踪链接

### 方式二：Java 插件版

1. 下载 [Java 服务端插件 + 客户端插件](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)
2. 在微信模块中加载服务端插件，启动隧道获取公网地址
3. 加载客户端插件，配置服务器地址
4. 发送 `#消息内容` 即可追踪已读

### 方式三：自建服务器

```bash
git clone https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker.git
cd read-receipt-tracker
pip install flask
python run.py
```

访问 `http://localhost:5000` 打开管理面板。

---

## 📦 下载

> 🔄 **CI 自动构建**：每次推送主分支即发布最新版本

<div align="center">

| 产物 | 说明 | 下载 |
|:---:|:---|:---:|
| 📱 **WeKit APK** | 微信 Xposed 模块（已修复 DEX 缓存更新问题，内置已读追踪） | [**下载**](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) |
| 🖥️ **Java 服务端插件** | 内置 HTTP + cloudflared 隧道 + Web 控制台 | [**下载**](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) |
| 🔌 **Java 客户端插件** | `#消息` 发送追踪，`/已读` 查询人数 | [**下载**](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) |

</div>

---

## 📱 客户端方案

### 🟢 WeKit 无服务器版（修改版 APK，推荐）

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

基于 [WeKit](https://github.com/Ujhhgtg/WeKit) 的微信 Xposed 模块，已修复 DEX 缓存更新问题，**内置已读追踪服务端**，无需额外部署服务器，开箱即用。

<div align="center">

[![📦 下载 WeKit APK](https://img.shields.io/badge/📦%20下载-WeKit%20APK-2ea44f?style=for-the-badge)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)

</div>

| 项目 | 说明 |
|:---|:---|
| **上游仓库** | https://github.com/Ujhhgtg/WeKit |
| **特点** | 模块内直接启用已读追踪，消息注册、已读统计、访客信息一体化 |
| **环境要求** | Android 9.0+ / Root + LSPosed（或 Zygisk）/ 微信 8.0.65 - 8.0.76 |
| **源码** | [`wekit-module/`](wekit-module/) |

</details>

### 🔵 WeKit 有服务器版（原版）

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

使用原版 [WeKit](https://github.com/Ujhhgtg/WeKit)，配合本项目提供的自建服务器使用。适合已有服务器或想在 Termux/Java 插件中运行服务端的用户。

- **特点**：原版 WeKit 不变，服务端独立部署，可选择 Linux / Windows / Termux / Java 插件等多种方式
- **下载**：[WeKit 原版 Release](https://github.com/Ujhhgtg/WeKit/releases)

</details>

### 🟣 Java 插件版（支持 WeKit / WAuxiliary / WuYu / HChat）

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

轻量级 Java 插件，无需编译 APK，在支持 Java 脚本的微信模块中加载即可使用。包含**服务端**和**客户端**两个配套插件：

| 插件 | 作用 | 下载 |
|:---|:---|:---:|
| **已读服务器（服务端）** | 内置 HTTP 服务 + cloudflared 公网隧道 + Web 控制台 | [read-tracker-java-server.zip](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) |
| **已读追踪（客户端）** | `#消息` 发送追踪卡片，`/已读` 查询人数，已读统计在浏览器控制台查看 | [read-tracker-java-client.zip](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) |

<div align="center">

[![📦 下载 Java 插件包](https://img.shields.io/badge/📦%20下载-Java插件包-2ea44f?style=for-the-badge)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)

</div>

> 💡 **兼容模块**：WeKit、WAuxiliary (WA)、WuYu、HChat 等
>
> 📝 **使用流程**：先装服务端并启动隧道 → 再装客户端并配置服务器地址

**源码**：[`plugins/java-read-tracker/`](plugins/java-read-tracker/) · [`plugins/read-tracker-client/`](plugins/read-tracker-client/)

</details>

---

## 🖥️ 服务器部署

### 🐧 Linux 服务器

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

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

### 🪟 Windows

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

```powershell
pip install flask
python run.py
```

打开 `http://localhost:5000` 即可访问管理面板。

</details>

### 📱 Termux / Android（手机本地部署）

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

**标准版（含 IP 定位）：**
```bash
pkg update -y && pkg install curl -y
bash <(curl -fL "https://cdn.jsdelivr.net/gh/gaigebeckmanChristinaJames/read-receipt-tracker@main/scripts/ultimate-setup.sh")
```

**Lite 版（无 IP 定位）：**
```bash
bash <(curl -fL "https://cdn.jsdelivr.net/gh/gaigebeckmanChristinaJames/read-receipt-tracker@main/scripts/ultimate-setup-lite.sh")
```

需要公网地址时另开会话：
```bash
pkg install cloudflared
cloudflared tunnel --protocol http2 --url http://127.0.0.1:5000
```

</details>

### ☕ Java 插件版服务器

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

无需单独部署服务器，直接在微信模块中加载「已读服务器」Java 插件即可，内置 HTTP 服务和 cloudflared 公网隧道。

<div align="center">

[![📦 下载 Java 服务端插件](https://img.shields.io/badge/📦%20下载-Java服务端插件-2ea44f?style=for-the-badge)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)

</div>

- **文件名**：`read-tracker-java-server.zip`
- **支持模块**：WeKit / WAuxiliary / WuYu / HChat

</details>

### 🐳 Docker

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

```bash
docker compose up -d
# 或
docker build -t read-receipt-tracker .
docker run -d -p 5000:5000 -v $(pwd)/data:/app/data read-receipt-tracker
```

</details>

> 💡 **提示**：所有部署形态均可用环境变量 `ENABLE_GEO=0` 关闭 IP 定位（Lite 模式）。

---

## 🌐 公网访问

| 部署场景 | 需要 Tunnel？ | 说明 |
|:---|:---:|:---|
| VPS / 云服务器 | ❌ | 自带公网 IP，直接访问 |
| 本地电脑 | ❌ | `localhost:5000` 即可 |
| 树莓派 / 内网服务器 | ✅ | 需要 Tunnel |
| Termux (Android) | ✅ | 脚本默认自动配置 |

**Linux 一键启用 Tunnel：**
```bash
bash scripts/setup-linux.sh --tunnel
cat .tunnel_url.txt   # 查看公网地址
```

---

## 📡 API 文档

<details>
<summary><b>📝 注册消息</b></summary>

<br>

```http
POST /register
Content-Type: application/json

{ "wxId": "user123", "content": "你好", "createTime": 1700000000000 }
```

**响应**：返回 `pixel_url`，嵌入消息即可追踪。

</details>

<details>
<summary><b>👁 已读追踪像素</b></summary>

<br>

```http
GET /pixel?wxId=user123&id=xxx
```

返回 1×1 透明 GIF。

</details>

<details>
<summary><b>🔢 查询已读数</b></summary>

<br>

```http
GET /count?wxId=user123&id=xxx
```

```json
{ "count": 5, "msg_id": "xxx" }
```

</details>

<details>
<summary><b>📋 批量查询 / 健康检查</b></summary>

<br>

```http
GET /batch-status?ids=id1,id2,id3
GET /health
```

</details>

---

## 📊 管理后台

| 页面 | 路由 | 说明 |
|:---|:---|:---|
| **仪表盘** | `/` | 统计 + 消息列表 + 搜索 + CSV 导出 |
| **消息详情** | `/message/<id>` | 消息内容 + IP/地理位置/UA/时间 |

---

## 🗂 项目结构

```
read-receipt-tracker/
├── python/                    # Python 后端 (Flask)
├── cpp/                       # C++ 后端 (meson + ninja)
├── scripts/                   # 部署脚本 (Termux / Linux)
├── plugins/
│   ├── java-read-tracker/     # Java 服务端插件
│   └── read-tracker-client/   # Java 客户端插件
├── wekit-module/              # WeKit 模块源码 (CI 构建 APK)
├── docs/                      # 文档
├── .github/workflows/         # CI 自动构建 & 发布
├── ROADMAP.md                 # 开发路线图
└── run.py                     # Python 启动入口
```

---

## 🔧 配置

| 变量 | 默认值 | 说明 |
|:---|:---|:---|
| `DATABASE_PATH` | `receipts.db` | 数据库路径 |
| `API_KEY` | *(空)* | 管理后台认证密钥 |
| `ENABLE_GEO` | `1` | IP 定位开关 |
| `RATE_LIMIT_PER_MINUTE` | `60` | 请求频率限制 |
| `PORT` | `5000` | 监听端口 |

---

## 🛠 技术栈

| 层级 | Python | C++ |
|:---|:---|:---|
| **框架** | Flask 2.3+ | 自建 HTTP 服务 |
| **数据库** | SQLite (WAL) | SQLite3 C API |
| **包管理** | uv | meson + ninja |
| **部署** | Docker / systemd / Termux | 原生二进制 |

---

## ❓ 常见问题

<details>
<summary><b>WeKit 启动后一直提示"115个功能需要更新"怎么办？</b></summary>

<br>

这是 WeKit 的 DEX 缓存问题。本项目提供的修改版 APK 已修复此问题，直接下载 [最新版](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) 安装即可。

</details>

<details>
<summary><b>Java 插件版怎么查看已读数量？</b></summary>

<br>

Java 客户端插件的已读统计需要在**浏览器控制台**中查看。发送 `#消息` 后，打开管理面板的浏览器控制台即可看到已读人数。

</details>

<details>
<summary><b>必须要有公网服务器吗？</b></summary>

<br>

不一定。有三种选择：
1. **WeKit 修改版 APK**：内置服务端，完全不需要服务器
2. **Java 插件版**：内置 cloudflared 隧道，自动获取公网地址
3. **自建服务器**：Linux/Windows/Termux/Docker 均可

</details>

<details>
<summary><b>IP 定位不准确怎么办？</b></summary>

<br>

IP 定位使用三级接口自动备份（ip-api.com → ipwho.is → ipinfo.io），如果都不准确可以用 `ENABLE_GEO=0` 关闭定位功能。

</details>

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

- 📋 开发计划见 [ROADMAP.md](ROADMAP.md)
- 🐛 发现问题请提 [Issue](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/issues)
- 💡 有新想法欢迎 [讨论](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/discussions)

---

<div align="center">

### 如果这个项目对你有帮助

**请点个 ⭐ Star 支持一下！**

<br>

[![💖 爱发电赞助](https://img.shields.io/badge/💖%20爱发电-支持作者-ff69b4?style=for-the-badge)](https://www.ifdian.net/a/zuomeng13)
&nbsp;&nbsp;
[![📦 下载最新版](https://img.shields.io/badge/📦%20下载-最新版本-2ea44f?style=for-the-badge)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)

</div>

---

## 📄 License

MIT © [gaigebeckmanChristinaJames](https://github.com/gaigebeckmanChristinaJames)
