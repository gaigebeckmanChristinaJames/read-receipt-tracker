# 🔧 构建工具链教程

本项目使用 **astral 三件套** (uv + ruff + 静态检查) 管理 Python 部分，**meson + ninja** 管理 C++ 部分。

---

## astral 三件套

### uv — Python 包管理器

`uv` 是 Ruff 团队出品的超快 Python 包管理和虚拟环境工具 (Rust 编写)。

```bash
# 安装
curl -LsSf https://astral.sh/uv/install.sh | sh

# 安装包 (系统级)
uv pip install --system flask

# 创建虚拟环境
uv venv
source .venv/bin/activate

# 安装依赖
uv pip install flask
```

### ruff — 代码检测 + 格式化

```bash
# 安装
uv pip install --system ruff
# 或
pip install ruff

# 代码检查
ruff check python/              # 检查
ruff check python/ --fix        # 自动修复

# 代码格式化
ruff format python/

# VS Code 集成：安装 Ruff 扩展即可
```

### 静态检查配置

配置在 `pyproject.toml` → `[tool.ruff]`：

- E/W: pycodestyle 规则
- F: pyflakes
- I: isort (导入排序)
- N: 命名规范
- UP: pyupgrade 提示
- B: flake8-bugbear
- SIM: 简化建议

---

## C++ 构建链 (meson + ninja)

### meson.build 配置

根文件：`cpp/meson.build`

```meson
project('rrtracker', 'cpp', version: '2.1.0')

executable('rrtracker-server',
  sources: files('src/main.cpp', 'src/tracker.cpp', 'src/database.cpp'),
  dependencies: [sqlite3_dep],
)
```

### 常用命令

| 命令 | 说明 |
|------|------|
| `meson setup builddir` | 初始化构建目录 |
| `meson compile -C builddir` | 编译 |
| `meson test -C builddir` | 运行测试 |
| `meson install -C builddir` | 安装到系统 |
| `ninja -C builddir -j4` | 并行编译 (4 核) |

---

## 一键安装所有工具

```bash
# Termux
bash scripts/setup-termux.sh

# Linux 服务器
bash scripts/setup-linux.sh
```

两个脚本都会自动安装 uv、ruff、meson、ninja、Flask 等全部依赖。
