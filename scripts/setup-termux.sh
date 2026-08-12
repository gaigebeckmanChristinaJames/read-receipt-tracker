#!/data/data/com.termux/files/usr/bin/bash
# ================================================================
# read-receipt-tracker · Termux 真·一键部署
# 支持管道运行: bash <(curl -s ...)
# 脚本自动下载源码 → 安装依赖 → 启动服务 + Tunnel
# 可选: --cleanup N 天自动清理
# ================================================================
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log() { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err() { echo -e "${RED}[✗]${NC} $1"; exit 1; }

REPO="https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker"
RAW="https://raw.githubusercontent.com/gaigebeckmanChristinaJames/read-receipt-tracker/main"
INSTALL_DIR="$HOME/read-receipt-tracker"
APP_DIR="$INSTALL_DIR/python/app"

CLEANUP_DAYS=""
for arg in "$@"; do
    case "$arg" in
        --cleanup) CLEANUP_DAYS="$2"; shift 2 ;;
        --cleanup=*) CLEANUP_DAYS="${arg#*=}" ;;
    esac
done

echo "🚀 [1/8] 环境检测..."
if [ ! -d "/data/data/com.termux" ]; then
    err "此脚本仅支持 Termux 环境运行"
fi
termux-wake-lock 2>/dev/null || true

# Root 强化保活 (可选)
if command -v su >/dev/null 2>&1 && su -c "exit" >/dev/null 2>&1; then
    log "Root 探测成功，解除 Android 后台限制..."
    su -c "dumpsys deviceidle whitelist +com.termux" >/dev/null 2>&1 || true
    su -c "am set-standby-bucket com.termux active" >/dev/null 2>&1 || true
    su -c "cmd appops set com.termux RUN_IN_BACKGROUND allow" >/dev/null 2>&1 || true
fi

echo "📦 [2/8] 配置清华源..."
echo "deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main" > "$PREFIX/etc/apt/sources.list"
pkg update -y 2>&1 | tail -3 || true

echo "📦 [3/8] 安装依赖 (python/cloudflared/curl)..."
pkg install -y python curl cloudflared sqlite 2>/dev/null | tail -2 || {
    warn "部分包安装失败，尝试单个安装..."
    pkg install -y python || err "python 安装失败，请检查网络"
    pkg install -y curl 2>/dev/null || true
    pkg install -y cloudflared 2>/dev/null || true
    pkg install -y sqlite 2>/dev/null || true
}

echo "🐍 [4/8] 安装 Flask..."
pip install flask -i https://pypi.tuna.tsinghua.edu.cn/simple 2>&1 | tail -2 || \
    pip install flask 2>&1 | tail -2 || warn "Flask 安装失败，稍后 watchdog 会重试"

echo "📥 [5/8] 下载服务源码..."
mkdir -p "$APP_DIR/templates"
for f in __init__.py app.py database.py routes.py utils.py; do
    curl -fsSL "$RAW/python/app/$f" -o "$APP_DIR/$f" 2>/dev/null || warn "下载 $f 失败"
done
for t in index.html detail.html error.html; do
    curl -fsSL "$RAW/python/app/templates/$t" -o "$APP_DIR/templates/$t" 2>/dev/null || warn "下载模板 $t 失败"
done
curl -fsSL "$RAW/run.py" -o "$INSTALL_DIR/run.py" 2>/dev/null || true

# 验证关键文件
if [ ! -f "$APP_DIR/routes.py" ] || [ ! -f "$APP_DIR/database.py" ]; then
    err "源码下载失败，请检查 GitHub 连通性后重试"
fi
log "源码就绪: $APP_DIR"

echo "📝 [6/8] 编写守护进程..."
cat << 'WATCHDOG' > "$INSTALL_DIR/watchdog.sh"
#!/data/data/com.termux/files/usr/bin/bash
INSTALL_DIR="$HOME/read-receipt-tracker"
cd "$INSTALL_DIR"
LAST_IP=""

while true; do
    # 公网 IP 变化检测 (网络切换自动重建隧道)
    CURRENT_IP=$(timeout 5 curl -s --max-time 3 https://1.1.1.1/cdn-cgi/trace 2>/dev/null | grep -oE "ip=[0-9.]+" | cut -d= -f2)
    if [ -n "$CURRENT_IP" ] && [ "$CURRENT_IP" != "$LAST_IP" ]; then
        if [ -n "$LAST_IP" ]; then
            echo "[$(date)] 网络切换 $LAST_IP → $CURRENT_IP，重启隧道..." >> daemon.log
            pkill -f "cloudflared tunnel" 2>/dev/null || true
            rm -f current_url.txt tunnel.log
        fi
        LAST_IP="$CURRENT_IP"
    fi

    # 保活 Flask
    if ! timeout 5 curl -sf --max-time 2 http://127.0.0.1:5000/health >/dev/null 2>&1; then
        for PID in $(pgrep -f "python.*run\.py"); do kill "$PID" 2>/dev/null; done
        nohup python "$INSTALL_DIR/run.py" --host 0.0.0.0 --port 5000 > app.log 2>&1 &
        sleep 3
    fi

    # 保活 Cloudflared Tunnel
    if ! pgrep -f "cloudflared tunnel" >/dev/null 2>&1; then
        rm -f tunnel.log current_url.txt
        cloudflared tunnel --url http://127.0.0.1:5000 > tunnel.log 2>&1 < /dev/null &
    fi

    # 提取公网 URL
    if [ ! -f current_url.txt ] && [ -f tunnel.log ]; then
        URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' tunnel.log 2>/dev/null | tail -1)
        [ -n "$URL" ] && echo "$URL" > current_url.txt
    fi

    sleep 3
done
WATCHDOG
chmod +x "$INSTALL_DIR/watchdog.sh"

echo "🌐 [7/8] 启动 Tunnel + 前台运行服务..."
pkill -f "cloudflared tunnel" 2>/dev/null || true
rm -f current_url.txt tunnel.log
cloudflared tunnel --url http://127.0.0.1:5000 > tunnel.log 2>&1 < /dev/null &

echo ""; echo "⏳ [8/8] 等待隧道地址 (最多 25 秒)..."
TUNNEL_URL=""
for i in $(seq 1 25); do
    if [ -f "$INSTALL_DIR/current_url.txt" ] 2>/dev/null; then :; fi
    URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' tunnel.log 2>/dev/null | tail -1)
    if [ -n "$URL" ]; then
        echo "$URL" > current_url.txt
        TUNNEL_URL="$URL"
        break
    fi
    echo -n "."
    sleep 1
done

echo ""; echo ""
echo "════════════════════════════════════════════"
echo "  ✅ 部署完成！"
echo "  🖥  控制台地址: http://127.0.0.1:5000"
if [ -n "$TUNNEL_URL" ]; then
    echo "  🔗 隧道地址:   $TUNNEL_URL"
else
    echo "  🔗 隧道地址:   建立中... (稍后 cat current_url.txt 查看)"
fi
echo "════════════════════════════════════════════"
echo ""
echo "▶ 服务将在前台运行 (Ctrl+C 停止)"
echo ""

# 可选自动清理 (放在前台运行之前)
if [ -n "$CLEANUP_DAYS" ]; then
    echo "🧹 配置自动清理 (保留 ${CLEANUP_DAYS} 天)..."
    cat << CLEANSCRIPT > "$INSTALL_DIR/auto-cleanup.sh"
#!/data/data/com.termux/files/usr/bin/bash
DAYS=$CLEANUP_DAYS
DB="\$HOME/read-receipt-tracker/receipts.db"
LOG="\$HOME/read-receipt-tracker/cleanup.log"
log() { echo "[\$(date '+%Y-%m-%d %H:%M:%S')] \$1" >> "\$LOG"; }
if [ "\$1" = "--once" ]; then
    [ -f "\$DB" ] && {
        DR=\$(sqlite3 "\$DB" "DELETE FROM reads WHERE read_at < CAST(strftime('%s','now') AS INTEGER) - \$DAYS*86400; SELECT changes();")
        DM=\$(sqlite3 "\$DB" "DELETE FROM messages WHERE registered_at < CAST(strftime('%s','now') AS INTEGER) - \$DAYS*86400; SELECT changes();")
        echo "清理: 消息-\$DM 已读-\$DR"
    }
    exit 0
fi
while true; do
    [ -f "\$DB" ] && {
        DR=\$(sqlite3 "\$DB" "DELETE FROM reads WHERE read_at < CAST(strftime('%s','now') AS INTEGER) - \$DAYS*86400; SELECT changes();")
        DM=\$(sqlite3 "\$DB" "DELETE FROM messages WHERE registered_at < CAST(strftime('%s','now') AS INTEGER) - \$DAYS*86400; SELECT changes();")
        if [ "\$DR" != "0" ] || [ "\$DM" != "0" ]; then log "清理: 消息-\$DM 已读-\$DR"; fi
    }
    sleep 86400
done
CLEANSCRIPT
    chmod +x "$INSTALL_DIR/auto-cleanup.sh"
    pkill -f "auto-cleanup.sh" 2>/dev/null || true
    nohup "$INSTALL_DIR/auto-cleanup.sh" > /dev/null 2>&1 &
    log "自动清理已启动 (每 24h 执行)"
fi

# 前台运行 Flask 服务 (日志直接输出到屏幕)
cd "$INSTALL_DIR"
exec python run.py --host 0.0.0.0 --port 5000
