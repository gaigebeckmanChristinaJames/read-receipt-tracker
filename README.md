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
[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](#-license)

<br>

[**📦 立即下载**](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)
&nbsp;&nbsp;·&nbsp;&nbsp;
[**📖 查看文档**](#-简介)
&nbsp;&nbsp;·&nbsp;&nbsp;
[**💖 爱发电赞助**](https://www.ifdian.net/a/zuomeng13)

</div>

---

## 📋 目录

- [简介](#intro)
- [核心特性](#features)
- [快速开始](#quick-start)
- [下载](#download)
- [客户端方案](#clients)
- [服务器部署](#server)
- [公网访问](#tunnel)
- [API 文档](#api)
- [管理后台](#admin)
- [项目结构](#structure)
- [配置](#config)
- [技术栈](#tech)
- [常见问题](#faq)
- [贡献](#contributing)
- [许可证](#license)

---

<a id="intro"></a>
## 📖 简介

一个极简的 **消息已读回执追踪服务**：注册消息后得到一个 URL，将该 URL 作为 1×1 透明像素嵌入消息，对方打开时自动记录已读。提供 Web 管理面板查看统计和每条消息的读取详情。

> 🎯 **适用场景**
> - 为微信模块 **WeKit / WuYu / WAuxiliary / HChat** 提供已读回执后端
> - 邮件营销已读率分析
> - 消息回执追踪
> - 网页埋点统计

---

<a id="features"></a>
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

<a id="quick-start"></a>
## 🚀 快速开始

### 方式一：WeKit 模块（第三方项目，到作者仓库下载）

1. 打开 WeKit 作者仓库 [Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit) → [Releases](https://github.com/Ujhhgtg/WeKit/releases) 下载最新 APK（standard / legacy 两种入口）
2. 在 LSPosed 中启用模块并作用于微信
3. 重启微信，在 WeKit 设置中启用已读追踪
4. 发送消息时自动生成追踪链接


> 📌 **WeKit 是别人（Ujhhgtg）开发的第三方微信模块，不是本项目的组件。** 本仓库不构建、不分发、也不"官方"发布 WeKit 的任何版本；这里只提供已读追踪的服务端、独立 APP 与 Java 插件。模块本身请到作者仓库下载。
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

<a id="download"></a>
## 📦 下载

> 🔄 **CI 自动构建**：每次推送主分支即发布最新版本

<div align="center">

| 产物 | 说明 | 下载 |
|:---:|:---|:---:|
| 📱 **WeKit** | 第三方微信 Xposed 模块（作者 Ujhhgtg，与本项目独立） | [**前往作者仓库**](https://github.com/Ujhhgtg/WeKit) |
| 📱 **已读服务器 APP** | 独立 Android 应用，无需 Root/Xposed，内置服务器+隧道 | [**下载**](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) |
| 🖥️ **Java 服务端插件** | 内置 HTTP + cloudflared 隧道 + Web 控制台 | [**下载**](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) |
| 🔌 **Java 客户端插件** | `#消息` 发送追踪，`/已读` 查询人数 | [**下载**](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest) |

</div>

---

<a id="clients"></a>
## 📱 客户端方案

### 🟢 WeKit（第三方模块，作者 Ujhhgtg）

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

WeKit 是**别人开发的第三方微信 Xposed 模块**，与本项目没有从属关系：本仓库既不构建也不分发它的 APK，只提供与它对接的已读追踪服务端。

<div align="center">

[![📦 前往 WeKit 作者仓库](https://img.shields.io/badge/📦%20前往-WeKit%20作者仓库-2ea44f?style=for-the-badge)](https://github.com/Ujhhgtg/WeKit)

</div>

| 项目 | 说明 |
|:---|:---|
| **作者仓库** | https://github.com/Ujhhgtg/WeKit |
| **维护者** | Ujhhgtg（非本项目作者） |
| **特点** | 模块内可启用已读追踪，消息注册、已读统计、访客信息一体化 |
| **环境要求** | Android 9.0+ / Root + LSPosed（或 Zygisk）/ 微信 8.0.65 - 8.0.76 |
| **下载** | 作者仓库 Releases 页 |

</details>

### 🔵 WeKit + 自建服务器

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

用 WeKit（第三方模块）发送追踪消息，配合本项目提供的自建服务器接收已读数据。适合已有服务器或想在 Termux / Java 插件中运行服务端的用户。

- **特点**：原版 WeKit 不变，服务端独立部署，可选择 Linux / Windows / Termux / Java 插件等多种方式
- **模块下载**：[WeKit 作者 Releases](https://github.com/Ujhhgtg/WeKit/releases)（第三方项目）

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

<a id="server"></a>
## 🖥️ 服务器部署

### 先选形态：公开服务器 vs 私人服务器

| 对比项 | 🌐 公开服务器（别人提供/共享） | 🔒 私人服务器（自己部署，**推荐**） |
|:---|:---|:---|
| 数据归属 | 已读记录、IP、UA 存在别人机器上 | 全部数据只在你自己的机器/账号里 |
| 隐私 | 服务方能看全部埋点数据 | 只有你能看 |
| 稳定性 | 由对方决定，可能随时停服/限流 | 自己控制，想停就停 |
| 速度 | 共用带宽，高峰期拥堵 | 独享，响应稳定 |
| 权限 | 管理后台密钥可能共享 | 自己设 `API_KEY`，别人进不来 |
| 成本 | 免费但要看人脸色 | 零成本也能跑（Render 免费版 / 旧手机 Termux / 本地 Docker） |
| 适合谁 | 只想临时试一下 | 长期自用、在意隐私 |

> **结论：能自建就自建。** 这套服务是"已读追踪"，本质上收集的是你和联系人的消息阅读行为 + IP + 设备信息，只有放在自己手里才谈得上隐私。推荐顺序：**私人服务器（VPS / Render 免费版）＞ 手机本地（Termux / APP 版）＞ 公开共享服务器**。

#### ☁️ 零成本私人服务器：Render 免费版

<details>
<summary><b>📖 点击展开部署步骤（16 步）</b></summary>

<br>

**准备**：一个 GitHub 账号（Render 直接用 GitHub 登录，不需要谷歌账号）；仓库源码；其余没了——不需要梯子之外的额外东西。

1. 浏览器打开 <https://render.com>，右上角 **Get started**，选 **GitHub** 登录授权。
2. 先把仓库 **Fork** 到你自己的账号：<https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker> → 右上角 **Fork** → 确认。
3. Render 右上角 **+ New** → **Web Service**。
4. 选 **Build and deploy from a Git repository** → **Next**。
5. 在列表里选你刚 Fork 的 `read-receipt-tracker`（首次需点 **Connect account** 授权仓库访问）。
6. **Name** 填个自己认得出的，例如 `my-read-tracker`（它决定你的访问域名）。
7. **Language / Runtime** 选 **Docker**（仓库根目录自带 `Dockerfile`，不用手写构建命令）。
8. **Instance Type** 选 **Free**（$0）。
9. **Region** 选离你近的，例如 Singapore。
10. 展开 **Environment Variables**，至少加这三条：
    - `API_KEY` = 一长串随机字符（管理后台登录用，别人猜到就等于拿到你的数据）
    - `ENABLE_GEO` = `0`（Lite 模式，少一次外部请求、更快更干净）
    - `DATABASE_PATH` = `/tmp/receipts.db`（免费实例没有持久盘，指到 `/tmp` 才不会启动报错）
11. 点 **Deploy Web Service**，等构建 + 部署，一般 3–6 分钟。
12. 地址形如 `https://my-read-tracker.onrender.com`，先打开 `/health` 确认返回正常。
13. 把这个地址填进 WE（WeKit）客户端的服务器地址，点测试连接。
14. 给自己发一条已读回执消息，去管理后台（`/admin`，用 `API_KEY` 登录）看有没有记录。
15. 记录能查到就算跑通，剩下的交给日常使用。

**免费版必须知道的三个坑**

| 坑 | 表现 | 应对 |
|:---|:---|:---|
| 休眠 | 15 分钟没请求就睡，冷启动 30–60 秒 | 首次连接超时是正常的，重试一次；WE 端把超时调大 |
| 数据不持久 | 重新部署/重启后 SQLite 清空，已读记录全丢 | 只做试用；长期用请上付费 Disk，或改 VPS / Termux 本地部署 |
| 免费额度 | 每月 750 小时实例时长，单实例够用但别开多个 | 只留一个服务 |

</details>

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

### 📱 APP 版（独立 Android 应用，推荐）

<details>
<summary><b>📖 点击展开详情</b></summary>

<br>

独立的 Android APP，**无需 Root / Xposed**，安装后直接运行已读追踪服务器，内置 cloudflared 公网隧道和 Web 控制台。零第三方依赖，纯 Android 框架 API。

<div align="center">

[![📦 下载已读服务器 APP](https://img.shields.io/badge/📦%20下载-已读服务器APP-2ea44f?style=for-the-badge)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/releases/latest)

</div>

| 项目 | 说明 |
|:---|:---|
| **特点** | 零依赖、后台服务、通知栏常驻、内置 cloudflared 隧道、Web 控制台 |
| **环境要求** | Android 5.0+ (minSdk 21)，无需 Root |
| **源码** | [`app-server/`](app-server/) |

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

<a id="tunnel"></a>
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

<a id="api"></a>
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

<a id="admin"></a>
## 📊 管理后台

| 页面 | 路由 | 说明 |
|:---|:---|:---|
| **仪表盘** | `/` | 统计 + 消息列表 + 搜索 + CSV 导出 |
| **消息详情** | `/message/<id>` | 消息内容 + IP/地理位置/UA/时间 |

---

<a id="structure"></a>
## 🗂 项目结构

```
read-receipt-tracker/
├── python/                    # Python 后端 (Flask)
├── cpp/                       # C++ 后端 (meson + ninja)
├── app-server/                # 独立 Android APP 版已读服务器
├── scripts/                   # 部署脚本 (Termux / Linux)
├── plugins/
│   ├── java-read-tracker/     # Java 服务端插件
│   └── read-tracker-client/   # Java 客户端插件
├── docs/                      # 文档
├── .github/workflows/         # CI 自动构建 & 发布
├── ROADMAP.md                 # 开发路线图
└── run.py                     # Python 启动入口
```

---

<a id="config"></a>
## 🔧 配置

| 变量 | 默认值 | 说明 |
|:---|:---|:---|
| `DATABASE_PATH` | `receipts.db` | 数据库路径 |
| `API_KEY` | *(空)* | 管理后台认证密钥 |
| `ENABLE_GEO` | `1` | IP 定位开关 |
| `RATE_LIMIT_PER_MINUTE` | `60` | 请求频率限制 |
| `PORT` | `5000` | 监听端口 |

---

<a id="tech"></a>
## 🛠 技术栈

| 层级 | Python | C++ |
|:---|:---|:---|
| **框架** | Flask 2.3+ | 自建 HTTP 服务 |
| **数据库** | SQLite (WAL) | SQLite3 C API |
| **包管理** | uv | meson + ninja |
| **部署** | Docker / systemd / Termux | 原生二进制 |

---

<a id="faq"></a>
## ❓ 常见问题

<details>
<summary><b>WeKit 启动后一直提示"115个功能需要更新"怎么办？</b></summary>

<br>

这是 WeKit 模块自身的问题，与本项目无关。请到作者仓库 [Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit) 下载最新版覆盖安装，或到该作者的 Issue 区反馈。

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
1. **WeKit（第三方模块）**：可配合本项目服务器使用，模块本身请到作者仓库下载
2. **Java 插件版**：内置 cloudflared 隧道，自动获取公网地址
3. **自建服务器**：Linux/Windows/Termux/Docker 均可

</details>

<details>
<summary><b>IP 定位不准确怎么办？</b></summary>

<br>

IP 定位使用三级接口自动备份（ip-api.com → ipwho.is → ipinfo.io），如果都不准确可以用 `ENABLE_GEO=0` 关闭定位功能。

</details>

---

<a id="contributing"></a>
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

<a id="license"></a>
## 📄 License

**Apache License 2.0（OSI 开源）** © [gaigebeckmanChristinaJames](https://github.com/gaigebeckmanChristinaJames)

- 允许商用、修改、再分发（含闭源），无需额外授权，符合 OSI 开源定义。
- 使用或参考了本项目的代码与实现，请注明来源：
  「部分代码或实现参考自 read-receipt-tracker — https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker 」
- 修改过的文件需显著标注；分发时附带 LICENSE 与 NOTICE。
- 完整条款见 [LICENSE](LICENSE)，署名细则见 [NOTICE](NOTICE)。
