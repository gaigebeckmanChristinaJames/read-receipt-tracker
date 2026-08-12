# 📬 read-receipt-tracker

> 轻量级消息已读追踪服务 · Python / Flask · 一行命令部署

[![Python](https://img.shields.io/badge/python-3.9+-blue.svg)](https://www.python.org/)
[![Flask](https://img.shields.io/badge/flask-2.3+-green.svg)](https://flask.palletsprojects.com/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Docker](https://img.shields.io/badge/docker-ready-2496ED.svg)](Dockerfile)

一个极简的**已读回执追踪服务**，通过嵌入 1×1 透明像素来记录邮件/消息是否被打开。提供 Web 管理后台，支持本地、云服务器、Docker 和 Android Termux 四种部署方式。

---

## ✨ 特性

- 🔌 **一行注册** — `POST /register` 注册消息，返回追踪像素 URL
- 👁 **透明追踪** — `/pixel` 端点返回 1x1 透明 GIF，无感记录已读
- 📊 **管理面板** — 内置 Web 后台，查看消息列表、已读统计、每条消息的读取记录
- 🌐 **IP/UA 记录** — 自动记录读取者 IP、User-Agent 和时间戳
- 🐳 **Docker 支持** — 支持 Docker / Docker Compose 一键部署
- 📱 **Termux 方案** — 提供 Android 手机端一键部署脚本 + Cloudflare Tunnel 内网穿透
- 🔒 **去重** — 同一消息 + 同一 IP 只计一次已读

---

## 🚀 快速开始

### 1. 本地运行

```bash
# 克隆仓库
git clone https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker.git
cd read-receipt-tracker

# 安装依赖
pip install -r requirements.txt

# 启动 (默认监听 0.0.0.0:5000)
python run.py
```

打开浏览器访问 `http://localhost:5000` 即可看到管理面板。

### 2. Docker 部署

```bash
# 构建并启动
docker compose up -d

# 或者用 Docker 命令
docker build -t read-receipt-tracker .
docker run -d -p 5000:5000 -v $(pwd)/data:/app/data read-receipt-tracker
```

### 3. Termux (Android) 一键脚本

```bash
bash <(curl -s https://raw.githubusercontent.com/gaigebeckmanChristinaJames/read-receipt-tracker/main/scripts/setup-termux.sh)
```

脚本会自动配置 Python 环境、Flask 服务、保活守护进程、以及 Cloudflare Tunnel 内网穿透。

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

**响应:**

```json
{
    "success": true,
    "id": "a1b2c3d4...",
    "wxId": "user123",
    "pixel_url": "http://your-server:5000/pixel?wxId=user123&id=a1b2c3d4..."
}
```

### 已读追踪像素

```http
GET /pixel?wxId=user123&id=a1b2c3d4...
```

返回 1×1 透明 GIF（`Content-Type: image/gif`），可嵌入 HTML 邮件或网页。

### 查询已读数

```http
GET /count?wxId=user123&id=a1b2c3d4...
```

**响应:**

```json
{ "count": 5, "msg_id": "a1b2c3d4..." }
```

### 健康检查

```http
GET /health
```

---

## 📊 管理后台

| 页面 | 路由 | 说明 |
|------|------|------|
| 首页仪表盘 | `/` | 总消息数、总已读数、平均已读数、消息列表 + 搜索 |
| 消息详情 | `/message/<id>` | 消息内容 + 所有读取记录 (IP / UA / 时间) |
| 健康检查 | `/health` | 服务健康状态 |

管理接口：
- `POST /api/delete/<id>` — 删除单条消息
- `POST /api/delete-all` — 清空全部数据

---

## 🗂 项目结构

```
read-receipt-tracker/
├── app/
│   ├── __init__.py          # 包入口，版本号
│   ├── app.py               # Flask 应用工厂
│   ├── database.py          # SQLite 数据库层
│   ├── routes.py            # API 路由 + 管理后台
│   ├── utils.py             # 工具函数 (IP / ID 生成)
│   └── templates/
│       ├── index.html       # 管理后台 - 首页
│       └── detail.html      # 管理后台 - 消息详情
├── scripts/
│   └── setup-termux.sh      # Termux 一键部署脚本
├── run.py                   # 启动入口
├── Dockerfile
├── docker-compose.yml
├── requirements.txt
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🔧 消息 ID 生成算法

```
SHA-256(wxId + '\0' + content + '\0' + createTime)
```

- 相同的 `wxId` + `content` + `createTime` 会生成相同的 ID
- `INSERT OR IGNORE` 策略保证幂等性

---

## 🛠 技术栈

| 层级 | 技术 |
|------|------|
| Web 框架 | Flask 2.3+ |
| 数据库 | SQLite (WAL 模式) |
| 运行时 | Python 3.9+ |
| 容器化 | Docker / Docker Compose |
| 内网穿透 | Cloudflare Tunnel (Termux 方案) |

---

## 📄 License

MIT © [gaigebeckmanChristinaJames](https://github.com/gaigebeckmanChristinaJames)
