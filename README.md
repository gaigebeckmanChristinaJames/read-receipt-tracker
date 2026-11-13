# read-receipt-tracker

**1×1 透明像素埋点，记录消息已读**

[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/Python-3.9+-3776AB?logo=python&logoColor=white)](python/)
[![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)](Dockerfile)
[![CI](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions/workflows/build.yml/badge.svg)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions/workflows/build.yml)

![首页截图](docs/images/landing.png)

<TODO: 截图：管理后台仪表盘，展示统计卡与消息列表>

---

<a id="what"></a>
## 这是什么

注册一条消息 → 拿到一个像素链接 → 对方打开时记一次已读。

| 步骤 | 做什么 |
|:---|:---|
| 1 | 调用 `POST /register`，传入 `wxId` 与消息内容 |
| 2 | 响应里的 `pixel_url` 就是追踪链接（`/pixel?wxId=…&id=…`） |
| 3 | 把它当图片嵌入消息 / 邮件 / 网页，对方加载时返回透明 GIF |
| 4 | 服务端记录时间、IP、UA 与可选归属地；后台查看统计 |

同一 `msg_id` + 同一 IP 只记一次（数据库约束 `UNIQUE(msg_id, ip_address)`）。

**30 秒验证**

```bash
git clone https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker.git && cd read-receipt-tracker
echo "API_KEY=<一串你自己的随机字符>" > .env
docker compose up -d
```

容器启动后执行，预期返回健康状态：

```bash
curl -s http://localhost:5000/health
# {"service":"read-receipt-tracker","status":"ok"}
```

预期看到：终端打印上面那行 JSON；浏览器打开 `http://localhost:5000` 进入登录页，用 `API_KEY` 登录。

---

<a id="choose"></a>
## 我该选哪条路

先看要不要 Root、要不要自己的服务器：

| 方案 | 要 Root / 模块 | 要服务器 | 适合 |
|:---|:---:|:---:|:---|
| WeKit（第三方模块） | 要 | 不要 | 已用 LSPosed，想少折腾 |
| 独立 APP 版 | 不要 | 不要 | 只有一台手机，装完即用 |
| Java 插件 | 要 | 可选 | 已有微信模块，想统一管理 |
| 自建服务端 | 不要 | 要 | 想自己控数据、长期用 |
| Docker / Termux | 不要 | 要 | 有 NAS、VPS 或旧手机 |

<details>
<summary><b>三种客户端</b></summary>

<br>

| 客户端 | 说明 | 下载 |
|:---|:---|:---|
| WeKit | 第三方微信 Xposed 模块，作者 Ujhhgtg，与本项目无隶属关系 | [作者仓库](https://github.com/Ujhhgtg/WeKit/releases) |
| 独立 APP | 无需 Root / Xposed，内置 HTTP 服务与 cloudflared 隧道 | 本仓库 Releases |
| Java 插件 | 服务端 + 客户端两个插件，兼容 WeKit / WAuxiliary / WuYu / HChat | 本仓库 Releases |

</details>

<details>
<summary><b>五种部署</b></summary>

<br>

| 方式 | 说明 | 入口 |
|:---|:---|:---|
| Docker Compose | 推荐，数据落 `./data` | `docker compose up -d` |
| Linux + systemd | 适合 VPS，常驻进程 | `scripts/setup-linux.sh` |
| Windows | 直接跑 `python run.py` | 见「部署与运维」 |
| Termux | 安卓手机本地跑 | `scripts/ultimate-setup.sh` |
| Render 免费版 | 零成本公网，会休眠 | 见「部署与运维」 |

</details>

---

<a id="download"></a>
## 下载与版本

| 产物 | 说明 | 版本 |
|:---|:---|:---|
| `app-debug.apk` | 独立 APP（minSdk 21） | `<VERSION>` |
| `app-standard-debug.apk` | WeKit 标准入口版 | `<VERSION>` |
| `app-legacy-debug.apk` | WeKit legacy 入口版 | `<VERSION>` |
| `read-tracker-java-server.zip` | Java 服务端插件 | `<VERSION>` |
| `read-tracker-java-client.zip` | Java 客户端插件 | `<VERSION>` |

- **稳定版**：形如 `v2.1.0` 的 tag（当前 `<VERSION>`，发布于 `<DATE>`）
- **CI 构建**：形如 `ci-YYYYMMDD-HHMM` 的 tag，每次推送主分支自动生成，内容随时变化，不建议长期依赖

校验信息（与发布页 Assets 对照）：

| 项 | 值 |
|:---|:---|
| SHA256 | `<SHA256>` |
| APK 签名证书 SHA256 | `<SIGNING_CERT_SHA256>` |

```bash
sha256sum app-debug.apk   # 预期与表中 SHA256 一致
```

---

<a id="api"></a>
## API 摘要

| 接口 | 鉴权 | 用途 |
|:---|:---|:---|
| `POST /register` | 无 | 注册消息，返回 `pixel_url` |
| `GET /pixel`、`GET /pixel.gif` | 无 | 返回透明 GIF 并记一次已读 |
| `GET /count` | 无 | 查某条消息的已读人数与明细 |
| `GET /health` | 无 | 健康检查 |
| `GET /batch-status` | 需要 | 批量查已读人数 |

<details>
<summary><b>注册与查询示例</b></summary>

<br>

```http
POST /register
Content-Type: application/json

{"wxId":"user123","content":"你好","createTime":1700000000000}
```

```json
{"success":true,"id":"<msg_id>","wxId":"user123",
 "pixel_url":"http://localhost:5000/pixel?wxId=user123&id=<msg_id>"}
```

查询：

```http
GET /count?wxId=user123&id=<msg_id>
```

返回 `count`（按 IP 去重）与 `reads[]`（IP、归属地、UA、时间）。

批量（逗号分隔，数量受 URL 长度限制）：

```http
GET /batch-status?ids=id1,id2
X-API-Key: <API_KEY>
```

字段约束与错误：`wxId` 必填；`content` ≤ 50000 字符；`createTime` 为毫秒时间戳，省略则取当前时间。缺 `wxId` → `400`，JSON 非法 → `400 Invalid JSON`，超频 → `429`，后台未鉴权 → `401`。

</details>

<details>
<summary><b>后台与数据接口</b></summary>

<br>

| 路由 | 说明 |
|:---|:---|
| `/` | 仪表盘：统计、消息列表、搜索、CSV 导出（前端生成） |
| `/message/<mid>` | 单条详情：内容、IP、归属地、UA、时间 |
| `/api/messages` | JSON 列表，固定返回最近 100 条，**无分页参数** |
| `/api/reads/<mid>` | JSON 已读明细 |
| `POST /api/delete/<mid>` | 删除单条及其已读记录 |
| `POST /api/delete-all` | 清空全部 |

鉴权二选一：登录后的会话 Cookie，或请求头 `X-API-Key: <API_KEY>`（也支持 `?api_key=`）。未配置 `API_KEY` 时后台是**开放**的，公网部署务必设置。

</details>

---

<a id="ops"></a>
## 部署与运维

| 变量 | 默认 | 说明 |
|:---|:---|:---|
| `DATABASE_PATH` | `receipts.db` | SQLite 文件路径 |
| `API_KEY` | 空 | 后台密码 / API Key，空则后台开放 |
| `ENABLE_GEO` | `1` | `0` 关闭归属地查询 |
| `RATE_LIMIT_PER_MINUTE` | `60` | 每 IP 每分钟上限，`0` 关闭 |
| `PORT` | `5000` | 监听端口 |

<details>
<summary><b>卷权限、时区与非 root 运行</b></summary>

<br>

容器内以非 root 用户 `appuser` 运行，挂载目录需可写：

```bash
mkdir -p data && sudo chown -R 1000:1000 data
TZ=Asia/Shanghai docker compose up -d
```

`docker-compose.yml` 已带 `restart: unless-stopped` 与健康检查；非容器部署把同样的变量写进 `.env` 或 `export`。

</details>

<details>
<summary><b>Termux 脚本：先下载、再校验、后执行</b></summary>

<br>

不要直接 `bash <(curl …)`，改为三步：

```bash
curl -fLO https://cdn.jsdelivr.net/gh/gaigebeckmanChristinaJames/read-receipt-tracker@main/scripts/ultimate-setup.sh
less ultimate-setup.sh     # 预期：能看清脚本内容
bash ultimate-setup.sh     # 预期：输出安装步骤与启动日志
```

</details>

<details>
<summary><b>反向代理 + HTTPS（Caddy）</summary>

<br>

```caddyfile
track.example.com {
    reverse_proxy 127.0.0.1:5000
}
```

`pixel_url` 使用请求的 Host，反代后自动变为 https 域名。前面若还有一层代理，确认透传 `X-Forwarded-For`，否则记录的都是代理 IP。

</details>

<details>
<summary><b>自检</b></summary>

<br>

| 检查 | 命令 | 预期 |
|:---|:---|:---|
| 进程 | `docker compose ps` | `Up (healthy)` |
| 健康 | `curl -s localhost:5000/health` | `{"status":"ok",...}` |
| 日志 | `docker compose logs -f app` | 启动行含 db 路径与 geoip 开关 |
| 数据 | `sqlite3 data/receipts.db "select count(*) from reads;"` | 随打开次数增长 |

</details>

<details>
<summary><b>备份、清理与体积</b></summary>

<br>

- 备份：停容器后复制 `data/receipts.db`（有 WAL 时连同 `-wal`、`-shm`），或 `sqlite3 data/receipts.db ".backup data/backup.db"`
- 清理：后台「删除单条 / 清空全部」，或 `POST /api/delete-all`
- 体积：单条已读约 200–400 字节（IP、UA、归属地、时间），1 万条约 2–4 MB；长期运行建议按季度归档

</details>

<details>
<summary><b>Render 免费版要点</b></summary>

<br>

- 运行时选 Docker（仓库根目录自带 `Dockerfile`）
- 环境变量至少设 `API_KEY`、`ENABLE_GEO=0`、`DATABASE_PATH=/tmp/receipts.db`
- 免费实例无持久盘，重新部署即清库；15 分钟无请求会休眠，冷启动 30–60 秒

</details>

---

<a id="compat"></a>
## 兼容矩阵

| 客户端 | 微信版本 | Android | 备注 |
|:---|:---|:---|:---|
| WeKit（第三方） | 8.0.65 – 8.0.76 | 9.0+，需 Root + LSPosed/Zygisk | 版本由作者维护，超范围不保证 |
| Java 插件 | 跟随宿主模块 | 跟随宿主模块 | 支持 WeKit / WAuxiliary / WuYu / HChat |
| 独立 APP | 不依赖微信 | 5.0+（minSdk 21） | 无需 Root |
| 服务端 | — | — | Python 3.9+ / Docker / Termux |

**不支持**：微信 8.0.76 以上（未验证）、iOS、无 Root 且不用 APP 版的 Xposed 场景；C++ 后端未覆盖全部新接口，以 `cpp/` 实现为准。

---

<a id="notice"></a>
## 使用须知（合规与隐私）

> 这一节建议在部署前读完。

- **用途边界**：只用于你确有权追踪的消息，例如自己的通知、自己发的邮件、自己团队的内容。不得用于窥探他人隐私、跟踪他人行踪或规避平台规则，使用风险自负。
- **记录的数据**：打开时间、IP、User-Agent；默认还会记录由 IP 推导的国家、省、市、运营商、经纬度。
- **第三方外发**：开启定位时，访客 IP 会依次发给 `ip-api.com`、`ipwho.is`、`ipinfo.io` 查询归属地；`ENABLE_GEO=0` 时不发起任何外部请求。
- **保留与删除**：数据只在你的 SQLite 里，没有自动过期策略，删除方式见「备份、清理与体积」。
- **部署位置决定数据归属**：放在别人提供的公开服务器上，等于把访客 IP 交给对方，建议自建。
- **合规提醒**：不同地区对 IP 是否属于个人信息、埋点是否需要告知有不同要求，请自行确认你的告知与留存义务。

---

<a id="limits"></a>
## 局限与已知问题

| 现象 | 原因 | 处理 |
|:---|:---|:---|
| 对方看了但没记录 | 客户端默认不加载外链图片 | 属预期漏报，无法彻底解决 |
| 已读数偏高 | 图片代理、安全扫描、预览抓取会预取 | 结合 UA 与 IP 甄别 |
| 换网络后重复计数 | 去重键是 `msg_id + IP` | 已知局限，见 ROADMAP |
| 群聊已读不准 | 成员多、IP 复用 | 只作趋势参考 |
| 撤回 / 图片消息 | 追踪依赖注册的文本消息 | 不支持 |
| 同局域网多人算一人 | NAT 后 IP 相同 | 已知局限 |

---

<a id="faq"></a>
## FAQ

<details>
<summary><b>这个项目收费吗？有"特别版"吗？</b></summary>

<br>

不收费，也没有付费版、增强版或网盘分流渠道。代码以 Apache-2.0 发布，构建产物只发布在 GitHub Releases；其他地方出现的收费版本与本项目无关。

</details>

<details>
<summary><b>对方明明看了却没有记录？</b></summary>

<br>

先确认图片真的被加载：不少客户端默认不加载外链图片。再确认 `pixel_url` 是公网可访问的域名，而不是 `localhost`。

</details>

<details>
<summary><b>后台打不开或一直跳登录？</b></summary>

<br>

检查是否设置了 `API_KEY`，以及反向代理是否透传 Cookie；HTTPS 下注意 `SameSite` 与域名一致。

</details>

<details>
<summary><b>能同时跑 Python 和 C++ 两个后端吗？</b></summary>

<br>

可以，二者共用同一套 SQLite 结构，但同一时间只应有一个进程写库。

</details>

---

<a id="term"></a>
### 术语

- **像素埋点 / 追踪链接**：一个返回 1×1 透明图的 URL，被加载即视为"已读"。
- **已读回执**：记录谁在什么时候打开了这条消息，本项目以一次像素加载为单位。
- **模块**：运行在微信进程中的 Xposed 插件（如 WeKit），负责把追踪链接插入消息。

---

<a id="contributing"></a>
## 贡献与许可

- 提交前跑 `pytest` 与 `ruff check`，见 [CONTRIBUTING.md](CONTRIBUTING.md)
- 安全问题按 [SECURITY.md](SECURITY.md) 私下报告，不要开公开 Issue
- 行为准则见 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- 上游与致谢：WeKit 作者 [Ujhhgtg](https://github.com/Ujhhgtg/WeKit)；本项目与 WeKit 相互独立

许可：**Apache License 2.0**，可商用、可修改、可再分发；使用或参考本项目代码，请在 README、关于页或源文件头注明来源与仓库链接，详见 [LICENSE](LICENSE) 与 [NOTICE](NOTICE)。

### 待补充

- `<VERSION>` / `<DATE>`：稳定版 tag 与发布日期
- `<SHA256>`：各构建产物的校验值
- `<SIGNING_CERT_SHA256>`：APK 签名证书指纹
- 三张截图：`docs/images/landing.png`、`docs/images/admin.png`、`docs/images/app.png`
