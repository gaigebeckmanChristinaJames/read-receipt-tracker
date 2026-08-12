#!/usr/bin/env bash
# ================================================================
# read-receipt-tracker · Linux 服务器一键部署 v3.0
# 全程实时输出 + 超时保护 + 支持管道运行 (bash <(curl -s ...))
# 用法:
#   bash setup-linux.sh              # 普通部署 (systemd 托管)
#   bash setup-linux.sh --tunnel     # 无公网IP时启用 Cloudflare Tunnel
#   bash setup-linux.sh --foreground # 前台运行 (查看实时日志)
# ================================================================
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log() { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err() { echo -e "${RED}[✗]${NC} $1"; exit 1; }

RAW="https://raw.githubusercontent.com/gaigebeckmanChristinaJames/read-receipt-tracker/main"
INSTALL_DIR="$HOME/read-receipt-tracker"

# 判断是否管道运行 (bash <(curl -s ...))
case "$0" in
    /dev/fd/*|/proc/*)
        # 管道运行：没有真实脚本目录，源码自动下载
        DIR="$INSTALL_DIR"
        PIPED=true
        ;;
    *)
        DIR="$(cd "$(dirname "$0")/.." && pwd)"
        PIPED=false
        ;;
esac

MODE="$1"

echo "🚀 [1/6] 环境检测..."
echo "   运行模式: $([ "$PIPED" = true ] && echo '管道模式(自动下载源码)' || echo '本地目录模式')"
echo "   安装目录: $DIR"
command -v python3 >/dev/null 2>&1 || err "未找到 python3，请先安装"
log "Python: $(python3 --version)"

echo ""
echo "📦 [2/6] 安装 Flask (清华源优先)..."
python3 -m pip install flask -i https://pypi.tuna.tsinghua.edu.cn/simple 2>&1 | tail -1 || {
    echo "   清华源失败，尝试官方源..."
    python3 -m pip install flask 2>&1 | tail -1
}
log "Flask 就绪"

# 管道模式需要下载源码
if [ "$PIPED" = true ] || [ ! -f "$DIR/python/app/routes.py" ]; then
    echo ""
    echo "📥 [3/6] 下载服务源码..."
    mkdir -p "$DIR/python/app/templates"
    for f in __init__.py app.py database.py routes.py utils.py; do
        echo "   → $f"
        curl -fL "$RAW/python/app/$f" -o "$DIR/python/app/$f"
    done
    for t in index.html detail.html error.html; do
        echo "   → templates/$t"
        curl -fL "$RAW/python/app/templates/$t" -o "$DIR/python/app/templates/$t"
    done
    echo "   → run.py"
    curl -fL "$RAW/run.py" -o "$DIR/run.py"
    log "源码下载完成"
else
    log "源码已存在: $DIR/python/app"
fi

echo ""
echo "📥 [4/6] 检查 Cloudflare Tunnel..."
if [ "$MODE" = "--tunnel" ] || [ "$MODE" = "--foreground" ]; then
    if ! command -v cloudflared >/dev/null 2>&1; then
        echo "   安装 cloudflared..."
        curl -fL --max-time 120 https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 \
            -o "$HOME/.local/bin/cloudflared" 2>/dev/null && \
            chmod +x "$HOME/.local/bin/cloudflared" && \
            export PATH="$HOME/.local/bin:$PATH" || \
            warn "cloudflared 安装失败，稍后手动装"
    fi
    log "cloudflared: $(cloudflared --version 2>/dev/null || echo '待安装')"
fi

echo ""
echo "▶ [5/6] 启动服务..."

if [ "$MODE" = "--foreground" ]; then
    # 前台模式：Flask 后台 + cloudflared 前台
    cd "$DIR"
    pkill -f "cloudflared tunnel" 2>/dev/null || true
    nohup python3 run.py --host 0.0.0.0 --port 5000 > app.log 2>&1 &
    sleep 2
    if timeout 5 curl -sf http://127.0.0.1:5000/health >/dev/null 2>&1; then
        log "Flask 已启动: http://127.0.0.1:5000"
    fi
    echo ""
    echo "════════════════════════════════════════════"
    echo "  🖥  控制台地址: http://127.0.0.1:5000"
    echo "════════════════════════════════════════════"
    echo ""
    echo "▶ cloudflared 前台运行中 (隧道日志直接显示)"
    echo "▶ Ctrl+C 停止"
    echo ""
    exec cloudflared tunnel --url http://127.0.0.1:5000
elif [ "$MODE" = "--tunnel" ]; then
    # 隧道模式：都后台 + 保活脚本
    cd "$DIR"
    pkill -f "cloudflared tunnel" 2>/dev/null || true
    nohup python3 run.py --host 0.0.0.0 --port 5000 > app.log 2>&1 &

    cat << 'TUNSCRIPT' > "$DIR/keep-tunnel.sh"
#!/usr/bin/env bash
cd "$HOME/read-receipt-tracker"
LAST_IP=""
while true; do
    CURRENT_IP=$(timeout 5 curl -s --max-time 3 https://1.1.1.1/cdn-cgi/trace 2>/dev/null | grep -oE "ip=[0-9.]+" | cut -d= -f2)
    if [ -n "$CURRENT_IP" ] && [ "$CURRENT_IP" != "$LAST_IP" ]; then
        if [ -n "$LAST_IP" ]; then
            echo "[$(date)] 网络切换，重建隧道..." >> tunnel.log
            pkill -f "cloudflared tunnel" 2>/dev/null || true
            rm -f .tunnel_url.txt
        fi
        LAST_IP="$CURRENT_IP"
    fi
    if ! pgrep -f "cloudflared tunnel" >/dev/null 2>&1; then
        rm -f .tunnel_url.txt
        cloudflared tunnel --url http://127.0.0.1:5000 > tunnel.log 2>&1 < /dev/null &
    fi
    if [ ! -f .tunnel_url.txt ] && [ -f tunnel.log ]; then
        URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' tunnel.log 2>/dev/null | tail -1)
        [ -n "$URL" ] && echo "$URL" > .tunnel_url.txt
    fi
    sleep 3
done
TUNSCRIPT
    chmod +x "$DIR/keep-tunnel.sh"
    nohup "$DIR/keep-tunnel.sh" > /dev/null 2>&1 &

    echo "⏳ 等待隧道地址 (最多 20 秒)..."
    for i in $(seq 1 20); do
        if [ -f "$DIR/.tunnel_url.txt" ]; then
            URL=$(cat "$DIR/.tunnel_url.txt")
            [ -n "$URL" ] && break
        fi
        echo -n "."
        sleep 1
    done
    echo ""
    echo "════════════════════════════════════════════"
    echo "  ✅ 部署完成 (后台运行)"
    echo "  🖥  控制台: http://127.0.0.1:5000"
    [ -n "$URL" ] && echo "  🔗 隧道:   $URL"
    echo "════════════════════════════════════════════"
else
    # 默认：systemd 托管
    cd "$DIR"
    pkill -f "run.py" 2>/dev/null || true
    if [ -d "/etc/systemd/system" ]; then
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
        log "systemd 服务已启动"
        echo "   查看状态: systemctl status read-receipt-tracker"
    else
        warn "无 systemd，改用 nohup 后台运行"
        nohup python3 run.py --host 0.0.0.0 --port 5000 > app.log 2>&1 &
        log "服务已启动: http://127.0.0.1:5000"
    fi
fi

echo ""
echo "✅ 完成！常用命令:"
echo "   前台运行: bash scripts/setup-linux.sh --foreground"
echo "   隧道模式: bash scripts/setup-linux.sh --tunnel"
echo "   查看日志: tail -f app.log"
