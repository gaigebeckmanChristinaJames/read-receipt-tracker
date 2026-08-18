# WeKit - 微信增强模块 (含已读追踪)

> 基于 [WeKit](https://github.com/Ujhhgtg/WeKit) 的微信 Xposed 模块，新增已读追踪功能，支持消息已读状态监控、访客信息记录与公网隧道访问。

[![Build APK](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions/workflows/build.yml/badge.svg)](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

## 功能特性

### 已读追踪 (Read Receipt Tracker)
- **消息注册**: 为每条消息生成唯一追踪 ID，基于 SHA-256 算法
- **已读监控**: 通过透明追踪像素实时记录消息被阅读状态
- **访客追踪**: 记录访问者 IP 地址、地理位置（国家/地区/城市/运营商）、设备信息
- **公网隧道**: 内置 cloudflared 隧道，自动生成公网访问地址，无需额外服务器配置
- **Web 控制台**: 提供可视化界面，查看消息列表、已读统计、访客详情
- **本地存储**: 使用 SQLite 数据库，所有数据存储在本地设备
- **REST API**: 完整的 HTTP API，支持消息注册、查询、删除等操作

### 基础功能 (继承自 WeKit)
- 消息相关：防撤回、消息批量操作、语音转文字等
- 界面美化：主题自定义、气泡样式、导航栏替换等
- 群聊增强：群成员真实昵称、群聊分组、消息上下文菜单定制等
- 朋友圈：自动刷新、防删除、详情展示等
- 小程序：去广告、跳过启动页、调试功能等
- 系统级：平板模式、伪装环境、防检测等

## 下载安装

### CI 自动构建 (推荐)
每次推送到主分支都会自动构建 APK 并发布到 Releases。

> **首次启用 CI**：将 [`ci/build.yml`](ci/build.yml) 移动到 `.github/workflows/build.yml` 并推送，即可启用 GitHub Actions 自动构建。启用后，Releases 页面会出现 `CI Build` 发行版，包含 APK 和 Java 插件两个附件，可直接下载。

也可以在 [Actions](https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker/actions) 页面手动触发构建。

### 环境要求
- Android 9.0 (API 28) 及以上
- 已 root 设备，并安装 Xposed 框架（LSPosed / EdXposed 等）
- 或使用 Zygisk 模式（需 Magisk）
- 微信版本支持：8.0.65 - 8.0.76

## 独立已读服务器 (Java 插件版)

除了 WeKit 内置的已读追踪功能，本仓库还提供一个独立的 Java 插件版已读服务器，支持以 HChat 插件或 WeKit Java 脚本形式运行，无需编译 APK 即可使用。

- **位置**: [`server/read-tracker-java/`](server/read-tracker-java/)
- **作者**: 做梦
- **特性**: 内置 cloudflared 公网隧道、Web 控制台、悬浮仪表盘、IP 地理位置查询
- **兼容**: 同时支持 WeKit 内置已读追踪和 HChat 已读追踪插件两种客户端

### 快速使用
1. 将 `read_tracker.bsh` 放入 WeKit 的 `<模块数据>/scripts_java/` 目录
2. 在 WeKit 设置中启用「脚本引擎 (Java)」
3. 重启微信，发送 `#已读服务器` 打开仪表盘

详见 [Java 版服务器文档](server/read-tracker-java/README.md)。

## 已读追踪使用指南

### 快速开始
1. 在 WeKit 主界面找到「已读追踪」入口
2. 点击「启动服务」启动本地 HTTP 服务器
3. （可选）点击「启动隧道」开启公网访问
4. 通过 API 或模块内置功能注册追踪消息
5. 在 Web 控制台查看已读统计和访客信息

### API 接口

#### 注册消息
```http
POST /register
Content-Type: application/json

{
  "wxId": "your_wechat_id",
  "content": "消息内容",
  "createTime": 1234567890000
}
```

#### 查询已读计数
```http
GET /count?wxId=your_id&id=message_id
```

#### 追踪像素 (嵌入消息中)
```http
GET /pixel?wxId=your_id&id=message_id
```
返回 1x1 透明 GIF，访问时自动记录已读。

更多 API 详情请参考 [已读追踪文档](docs/read-receipt-guide.md)。

## 项目结构

```
├── app/                    # 主 Android 模块
│   ├── src/main/java/
│   │   ├── dev/ujhhgtg/wekit/
│   │   │   ├── features/   # 功能模块
│   │   │   ├── dexkit/     # DEX 查找与缓存
│   │   │   ├── readreceipts/ # 已读追踪核心
│   │   │   ├── service/    # 后台服务
│   │   │   └── ui/         # 界面
│   │   └── ...
│   └── src/main/jniLibs/   # 预编译 native 库
├── libs/                   # 依赖库
│   └── common/
│       ├── annotation-scanner/  # KSP 注解处理器
│       ├── bsh/             # BeanShell 解释器
│       ├── reflekt/         # 反射工具库
│       └── stubs/           # 微信 & Android 隐藏类桩
├── buildSrc/               # 自定义 Gradle 任务
├── xtask/                  # 构建编排 (cargo xtask)
├── wekit-zygisk/           # Zygisk 模块
├── docs/                   # 文档
└── .github/workflows/      # CI 配置
```

## 构建

### 环境要求
- JDK 21
- Android SDK (compileSdk 37, build-tools 37.0.0)
- Rust toolchain + Android NDK (用于编译 native 库)

### 构建命令
```bash
# Debug 构建 (使用与 release 相同的签名)
./x build

# Release 构建 (开启优化)
./x build --release

# 标准 APK + Zygisk 模块 ZIP
./x zygisk build
```

> `./x` 是 `cargo xtask` 的别名，负责编排 native 库编译与 APK 打包。
> 如果 `app/src/main/jniLibs/` 中已有预编译的 `.so` 文件，也可以直接运行：
> ```bash
> ./gradlew :app:assembleStandardDebug
> ```

## 文档

- [快速开始](docs/getting-started.md)
- [安装指南](docs/installation.md)
- [配置指南](docs/configuration.md)
- [已读追踪使用指南](docs/read-receipt-guide.md)
- [常见问题](docs/faq.md)
- [开发指南](docs/development.md)
- [Zygisk 模式](docs/zygisk.md)

## 致谢

- [WeKit 上游](https://github.com/Ujhhgtg/WeKit)
- [WAuxiliary](https://github.com/HdShare/WAuxiliary_Public)
- [QAuxiliary](https://github.com/cinit/QAuxiliary)
- [DexKit](https://github.com/LuckyPray/DexKit)
- [LibXposed](https://github.com/libxposed)

## 许可证

本项目基于 GPL-3.0 许可证开源，详见 [LICENSE](LICENSE)。

## 免责声明

本项目仅供学习交流使用，请勿用于非法用途。使用本模块所产生的一切后果由使用者自行承担。
