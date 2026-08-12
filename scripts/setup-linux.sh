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

# --- Cloudflare Tunnel (可选内网穿透) ---
# 仅在以下场景需要: 本地电脑/树莓派/无公网IP的服务器
# 有公网 IP 的 VPS/云服务器 不需要!
install_tunnel() {
    if ! command -v cloudflared &>/dev/null; then
        log "安装 cloudflared..."
        curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o /usr/local/bin/cloudflared 2>/dev/null && \
            chmod +x /usr/local/bin/cloudflared && log "cloudflared 安装完成" || \
            warn "cloudflared 安装失败（可忽略，仅无公网IP时需要）"
    fi
}

TUNNEL_FLAG="$1"
if [ "$TUNNEL_FLAG" = "--tunnel" ]; then
    log "启用 Cloudflare Tunnel 内网穿透..."
    install_tunnel

    # 编写 tunnel 保活脚本
    mkdir -p "$DIR/scripts"
    cat << 'TUNSCRIPT' > "$DIR/scripts/keep-tunnel.sh"
#!/usr/bin/env bash
# Cloudflare Tunnel 保活脚本
# 适用场景: 本地开发机 / 树莓派 / 无公网IP 的服务器
# 有公网 IP 的 VPS/云服务器 不需要此脚本!

cd "$(dirname "$0")/.."
LAST_IP=""

while true; do
    CURRENT_IP=$(curl -s --max-time 3 https://1.1.1.1/cdn-cgi/trace 2>/dev/null | grep -oE "ip=[0-9.]+" | cut -d= -f2)
    if [ -n "$CURRENT_IP" ] && [ "$CURRENT_IP" != "$LAST_IP" ]; then
        if [ -n "$LAST_IP" ]; then
            echo "[$(date)] 网络切换 $LAST_IP → $CURRENT_IP，重启隧道…" >> tunnel.log
            pkill -f "cloudflared tunnel" 2>/dev/null || true
            rm -f .tunnel_url.txt
        fi
        LAST_IP="$CURRENT_IP"
    fi
    if ! pgrep -f "cloudflared tunnel" >/dev/null 2>&1; then
        rm -f .tunnel_url.txt
        cloudflared tunnel --url http://127.0.0.1:5000 > tunnel.log 2>&1 &
        sleep 5
    fi
    if [ ! -f .tunnel_url.txt ] && [ -f tunnel.log ]; then
        URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' tunnel.log 2>/dev/null | tail -1)
        [ -n "$URL" ] && echo "$URL" > .tunnel_url.txt && echo "[$(date)] Tunnel URL: $URL" >> tunnel.log
    fi
    sleep 3
done
TUNSCRIPT
    chmod +x "$DIR/scripts/keep-tunnel.sh"

    # 启动 tunnel
    pkill -f "keep-tunnel.sh" 2>/dev/null || true
    nohup "$DIR/scripts/keep-tunnel.sh" > /dev/null 2>&1 &

    # 等待公网 URL
    echo ""
    echo "⏳ 等待 Tunnel 就绪 (最多 15 秒)..."
    for i in $(seq 1 15); do
        if [ -f "$DIR/.tunnel_url.txt" ]; then
            URL=$(cat "$DIR/.tunnel_url.txt")
            if [ -n "$URL" ]; then
                log "Tunnel 公网地址: $URL"
                break
            fi
        fi
        sleep 1
    done
fi

# --- systemd 服务 ---
if [ -d "/etc/systemd/system" ] && [ "$1" != "--no-systemd" ] && [ "$TUNNEL_FLAG" != "--tunnel" ]; then
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
if [ "$TUNNEL_FLAG" = "--tunnel" ]; then
    echo "   隧道:   cat .tunnel_url.txt  # 查看公网地址"
fi
echo ""
if [ "$TUNNEL_FLAG" != "--tunnel" ]; then
    echo "💡 提示: 如果你的服务器没有公网 IP (本地电脑/树莓派等)"
    echo "   请使用: bash scripts/setup-linux.sh --tunnel"
fi
