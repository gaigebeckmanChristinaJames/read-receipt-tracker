#!/usr/bin/env bash
# ================================================================
# read-receipt-tracker · Linux 服务器一键部署脚本 v2.1
# 自动安装 uv + ruff + meson + ninja
# ================================================================
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log() { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }

DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$DIR"

echo "🚀 read-receipt-tracker Linux 部署"

# --- uv ---
if ! command -v uv &>/dev/null; then
    log "安装 uv..."
    curl -LsSf https://astral.sh/uv/install.sh | sh
    export PATH="$HOME/.local/bin:$PATH"
fi
log "uv: $(uv --version 2>/dev/null || echo 'installed')"

# --- ruff ---
if ! command -v ruff &>/dev/null; then
    log "安装 ruff..."
    uv pip install --system ruff 2>/dev/null || pip3 install ruff
fi

# --- Flask ---
log "安装 Python 依赖..."
uv pip install --system flask 2>/dev/null || pip3 install flask

# --- meson + ninja (用于 C++) ---
if [ -f "cpp/meson.build" ]; then
    which meson 2>/dev/null || pip3 install meson
    which ninja 2>/dev/null || { apt-get install -y ninja-build 2>/dev/null || true; }
    log "编译 C++ 后端..."
    cd cpp && meson setup builddir && meson compile -C builddir && log "C++ 后端编译成功" || warn "C++ 编译跳过"
    cd "$DIR"
fi

# --- systemd 服务 ---
if [ -d "/etc/systemd/system" ] && [ "$1" != "--no-systemd" ]; then
    log "注册 systemd 服务..."
    cat << EOF | sudo tee /etc/systemd/system/read-receipt-tracker.service > /dev/null
[Unit]
Description=read-receipt-tracker
After=network.target

[Service]
Type=simple
WorkingDirectory=$DIR
ExecStart=$(which python3) $DIR/run.py --host 0.0.0.0 --port 5000
Restart=always
RestartSec=5
User=$(whoami)

[Install]
WantedBy=multi-user.target
EOF
    sudo systemctl daemon-reload
    sudo systemctl enable --now read-receipt-tracker
    log "systemd 服务已启动: systemctl status read-receipt-tracker"
fi

log "部署完成！"
echo ""
echo "💡 快速命令:"
echo "   启动:   python3 run.py"
echo "   生产:   gunicorn -w 4 -b 0.0.0.0:5000 'python.app:create_app()'"
echo "   检查:   ruff check python/"
echo "   编译:   cd cpp && meson compile -C builddir"
