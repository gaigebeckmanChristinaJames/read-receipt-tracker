# Contributing to read-receipt-tracker

感谢你对本项目的关注！欢迎提交 Issue 和 Pull Request。

## 开发环境搭建

### Python 后端

```bash
# 使用 uv（推荐）
curl -LsSf https://astral.sh/uv/install.sh | sh
uv pip install --system flask ruff
python run.py
```

### C++ 后端

```bash
cd cpp
meson setup builddir
meson compile -C builddir
./builddir/rrtracker-server 5000 receipts.db
```

## 代码规范

- Python: 使用 `ruff check` 和 `ruff format`
- C++: 遵循项目现有风格，使用 `clang-format`
- 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范

## 提交 PR 流程

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交修改 (`git commit -m 'feat: add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启 Pull Request

## 报告 Bug

请使用 Issue 模板，包含：
- 复现步骤
- 预期行为
- 实际行为
- 环境信息（OS、Python 版本、部署方式）
