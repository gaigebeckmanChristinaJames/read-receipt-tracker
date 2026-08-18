# 已读服务器

作者：做梦

轻量级微信消息已读追踪服务器的 HChat / WeKit 插件移植版。

通过在发送的消息中嵌入 1x1 透明像素图片，接收方加载图片时触发回源请求，服务器按 IP 去重统计已读人数。消息 ID 由 `sha256(wxId + '\\0' + content + '\\0' + createTime)` 确定性生成，客户端与服务器共用同一算法，无需额外会话状态。

## 参考仓库

本插件 API、数据库结构、控制台均对齐上游轻量级已读追踪服务器实现：

- 仓库：https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker
- 上游为 Rust + axum + libsql 的独立服务器程序，支持本地部署和公网部署

## 功能

- 插件启动后自动在本地起 HTTP 服务，并通过 cloudflared 建立公网隧道
- 隧道就绪后自动将地址发送到微信文件传输助手
- 聊天中发送 `#已读服务器` 或 `#已读` 弹出悬浮仪表盘（状态 / 快捷操作 / 实时日志）
- Web 控制台：消息列表（左滑删除）、消息详情页（已读记录 + IP 地理位置）
- 同时兼容 **WeKit 内置已读追踪** 和 **HChat 已读追踪插件** 两种客户端

## API（与上游一致）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/register` | 注册消息，body `{wxId, content, createTime}`，返回 `{id, wxId, pixel_url, pixelUrl}` |
| GET | `/pixel?wxId=&id=` | 1x1 透明 GIF 埋点，记录读取者 IP / UA / 地理位置 |
| GET | `/count?wxId=&id=` | 返回 `{success, count, msg_id}`，按 IP 去重 |
| GET | `/api/messages` | 消息列表（含已读人数） |
| GET | `/api/reads/{id}` | 某条消息的已读记录 |
| POST | `/api/delete/{id}` | 删除单条消息及其已读记录 |
| POST | `/api/delete-all` | 清空全部 |
| GET | `/message/{id}` | 消息详情 HTML 页 |
| GET | `/` | 控制台 HTML |
| GET | `/health` | 健康检查 |

## 相比上游的改动

上游是 Rust + axum + libsql 的独立服务器程序，需要单独部署。本插件做了以下移植和适配：

1. **语言与运行时**：Rust → Java/BeanShell，直接运行在 HChat / WeKit 的脚本引擎中，无需额外部署
2. **数据库**：libsql → Android 内置 `SQLiteDatabase`，表结构与上游完全一致
3. **公网隧道**：上游需自行配置反向代理；本插件内置 cloudflared quick tunnel，启动后自动获取公网地址
4. **HTTP 服务器**：axum → 手写 `ServerSocket` + 线程池，兼容 `Expect: 100-continue`（OkHttp 默认行为）
5. **IP 地理位置**：上游不内置 GeoIP；本插件集成 ip-api.com 实时查询，记录国家 / 省份 / 城市 / 运营商 / 经纬度
6. **HChat 兼容**：上游为通用 HTTP 服务；本插件额外兼容 HChat 已读追踪插件的 `&amp;` 编码埋点 URL
7. **悬浮仪表盘**：上游仅有 Web 控制台；本插件增加微信内悬浮窗仪表盘，可查看状态、复制地址、重连隧道、实时日志
8. **埋点格式**：上游返回 PNG；本插件返回 GIF（与 WeKit 内置服务一致，微信兼容性更好）
9. **双客户端共存**：WeKit 和 HChat 客户端使用相同的表结构和 ID 算法，消息与已读记录天然互通，同一条消息重复注册自动去重

## 安装

### HChat
1. 导入 zip 包，或将整个插件文件夹放入 HChat 脚本插件目录
2. 打开插件开关
3. 在聊天中发送 `#已读服务器` 打开仪表盘

### WeKit
1. 将 `read_tracker.bsh` 放入 WeKit 的 `<模块数据>/scripts_java/` 目录
2. 在 WeKit 设置中启用「脚本引擎 (Java)」
3. 重启微信
4. 在聊天中发送 `#已读服务器` 打开仪表盘

### 客户端配置
- **WeKit**：已读追踪 → 服务器模式选「自定义」，地址填隧道地址
- **HChat 已读追踪插件**：设置 → 服务器地址填隧道地址

## 前提条件

- 插件 `lib/` 目录自带 `libcloudflared.so`，无需手动安装 cloudflared
- 端口 5000 未被占用（可在 `config.prop` 修改）
- 已读人数按 IP 去重，同一局域网下多设备可能计为一人

## 文件说明

- `main.java` — HChat 插件主脚本
- `read_tracker.bsh` — WeKit Java 脚本（内容与 main.java 相同）
- `info.prop` — HChat 插件元信息
- `config.prop` — 配置文件（端口）
- `lib/libcloudflared.so` — cloudflared 隧道二进制
- `track_data/` — 数据库目录（运行时生成）
- `log.txt` — 运行日志（运行时生成）
