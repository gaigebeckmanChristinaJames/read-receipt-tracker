# Security Policy

## 报告安全漏洞

如果你发现了安全漏洞，请**不要**公开创建 Issue。请通过以下方式私密联系维护者：

- GitHub Security Advisories（推荐）
- 邮件：gaigebeckmanChristinaJames@users.noreply.github.com

我们会在 48 小时内确认收到报告，并在合理时间内提供修复方案。

## 支持的版本

| 版本 | 支持状态 |
|------|----------|
| main 分支 | ✅ 完全支持 |
| 最新 Release | ✅ 安全更新 |
| 旧版本 | ❌ 不再支持 |

## 安全最佳实践

- 部署时务必设置 `API_KEY` 环境变量保护管理接口
- 不要将 `.env` 文件提交到版本控制
- 定期更新依赖包
- 使用 HTTPS 反向代理（如 Nginx/Caddy）暴露服务
- 生产环境建议启用 `RATE_LIMIT_PER_MINUTE` 限流
