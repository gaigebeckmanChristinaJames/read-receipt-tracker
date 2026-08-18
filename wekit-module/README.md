# WeKit 修改版 — 已读追踪修复版

基于 [Ujhhgtg/WeKit](https://github.com/Ujhhgtg/WeKit) 最新 dev 分支，针对 [read-receipt-tracker](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker) 后端做了兼容性修复。

适用于微信的 Xposed 模块。

## 仓库结构

```
wekit-module/
├── app/                          # [第1层] Android 应用模块 (Xposed 模块主体)
│   ├── src/main/java/dev/ujhhgtg/wekit/
│   │   ├── features/items/chat/
│   │   │   ├── ReadReceipts.kt           # 已读追踪核心 (已修复: 非阻塞注册)
│   │   │   ├── ReadReceiptsConfiguration.kt  # 配置管理
│   │   │   └── ReadReceiptRecord.kt      # 记录模型 + 端点校验
│   │   └── ...
│   ├── proguard-rules.pro         # [第2层] 混淆规则 (fastjson2/okhttp/kotlin 保留)
│   └── build.gradle.kts           # 构建配置 (含 script-deps DEX 生成)
├── server/
│   └── read-tracker-java/         # [第3层] Java 版已读服务器插件
│       ├── main.java              # 服务器主逻辑 (HTTP + SQLite + 隧道)
│       ├── read_tracker.bsh       # BeanShell 入口
│       ├── config.prop            # 配置文件
│       ├── info.prop              # 插件元信息
│       └── lib/jsch.dex           # SSH 隧道依赖
├── contrib/
│   └── wekit-read-receipts-server/  # [第4层] Rust 版参考后端 (官方)
├── wekit-native/                  # [第5层] Rust 原生库 (内置服务器等)
├── xtask/                         # [第6层] 构建自动化 (cargo xtask)
├── buildSrc/                      # Gradle 自定义任务
│   └── GenerateScriptDepsDexTask.kt  # script-deps DEX 生成任务
├── .github/workflows/build.yml    # [第7层] CI 构建 + 发布
└── x                              # 构建入口 (cargo xtask 包装)
```

## 已读追踪架构分层

### 第1层: 消息发送层 (`ReadReceipts.kt` — `onEnable` hook)
- 拦截微信发送消息，注入追踪像素 URL 到 XML 卡片
- **修复点**: 先发送消息，再异步注册到服务器（原逻辑阻塞注册导致"注册失败"时消息无法发送）

### 第2层: HTTP 注册层 (`registerMessage`)
- `POST {endpoint}/register`，body: `{wxId, content, createTime}`
- 兼容 read-receipt-tracker (Python) 和 wekit-read-receipts-server (Rust)
- 非阻塞，失败仅记日志不阻断消息

### 第3层: 像素追踪层 (`/pixel` 端点)
- 收件人打开消息时，微信自动加载 XML 中的图片 URL
- 服务器记录访问者 IP、UA、地理位置
- 1x1 透明 GIF/PNG，不影响消息显示

### 第4层: 已读计数轮询层 (`fetchCount`)
- `GET {endpoint}/count?wxId=&id=` → `{count}`
- 在聊天界面定期轮询，更新"已读 X 人"显示

### 第5层: 后端服务层
- **read-receipt-tracker** (Python/Flask + C++): 本仓库主后端
- **read-tracker-java** (Java 插件): 可在手机端运行的轻量后端
- **wekit-read-receipts-server** (Rust): 官方参考后端

## 构建

```bash
# Standard 版 (libxposed 入口)
./x build --flavor standard --release

# Legacy 版 (传统 de.robv 入口)
./x build --flavor legacy --release

# 生成 script-deps DEX (Java 插件依赖)
./gradlew generateScriptDepsDex
```

## 修复内容

### 1. 已读追踪注册失败 (ReadReceipts.kt)
- **问题**: 最新 CI 版本将注册改为同步阻塞，服务器不可达时消息无法发送
- **修复**: 改为"先发送后异步注册"，注册失败仅记日志
- **原理**: 消息 ID 在本地 SHA-256 计算，像素 URL 已嵌入消息，注册仅用于服务器记录明文

### 2. Java 插件混淆规则 (proguard-rules.pro)
- **问题**: 之前因 fastjson2/okhttp 体积过大移除了 keep 规则，导致 R8 混淆后 Java 插件无法通过类名访问这些库
- **修复**: 恢复 `okhttp3.**`、`okio.**`、`com.alibaba.fastjson2.**`、`kotlin.**` 的完整 keep 规则
