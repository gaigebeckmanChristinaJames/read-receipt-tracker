# Changelog

本项目所有重要变更都记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 新增 CONTRIBUTING.md、CODE_OF_CONDUCT.md、SECURITY.md 社区文件
- 新增 Issue 模板和 Pull Request 模板
- 新增 Python 后端单元测试
- 新增 CI 代码检查工作流（ruff）

### Changed
- 优化 README 文档结构
- 更新 GitHub Profile Bio

### Fixed
- 修复 C++ 后端编译错误
- 修复管理后台页面空白问题

## [2.1.0] - 2026-08-13

### Added
- IP 地理位置定位（中文省市 + 运营商，三接口自动备份）
- ENABLE_GEO 环境变量开关（Lite 模式）
- Termux 一键部署脚本（标准版 + Lite 版）
- 批量查询已读状态 API `/batch-status`
- CSV 导出功能
- 消息详情页面

### Changed
- Python 后端迁移到 uv + ruff 工具链
- C++ 后端迁移到 meson + ninja 构建系统
- 数据库使用 WAL 模式提升并发性能

### Fixed
- 修复同一 IP 重复计数问题
- 修复 API Key 认证绕过漏洞
