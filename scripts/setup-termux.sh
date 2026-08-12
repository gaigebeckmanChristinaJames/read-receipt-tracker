#!/data/data/com.termux/files/usr/bin/bash
# ================================================================
# read-receipt-tracker · Termux 真·一键部署
# 全程实时输出，不静默、不退后台
# 支持管道运行: bash <(curl -s ...)
# 可选: --cleanup N 天自动清理
# ================================================================
set -e

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log() { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err() { echo -e "${RED}[✗]${NC} $1"; exit 1; }

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
termux-wake-lock
echo "   唤醒锁已获取"

if command -v su >/dev/null 2>&1 && su -c "exit" >/dev/null 2>&1; then
    log "Root 探测成功，解除后台限制..."
    su -c "dumpsys deviceidle whitelist +com.termux" >/dev/null 2>&1
    su -c "am set-standby-bucket com.termux active" >/dev/null 2>&1
    su -c "cmd appops set com.termux RUN_IN_BACKGROUND allow" >/dev/null 2>&1
else
    echo "   无 Root，使用基础保活"
fi

echo ""
echo "📦 [2/8] 配置清华源..."
echo "deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main" > "$PREFIX/etc/apt/sources.list"
echo "   已写入清华源，开始更新软件列表..."
pkg update -y
echo ""
echo "   更新完成，开始安装依赖..."

echo ""
echo "📦 [3/8] 安装依赖..."
echo "   → 安装 python..."
pkg install -y python
echo "   → 安装 curl..."
pkg install -y curl
echo "   → 安装 cloudflared..."
pkg install -y cloudflared || echo "   cloudflared 装不上也不影响核心服务"
echo "   → 安装 sqlite..."
pkg install -y sqlite || true
log "依赖安装完成"

echo ""
echo "🐍 [4/8] 安装 Flask..."
pip install flask -i https://pypi.tuna.tsinghua.edu.cn/simple || {
    echo "   清华源失败，尝试官方源..."
    pip install flask
}
log "Flask 就绪"

echo ""
echo "📥 [5/8] 下载服务源码..."
mkdir -p "$APP_DIR/templates"
for f in __init__.py app.py database.py routes.py utils.py; do
    echo "   → 下载 $f"
    curl -fL "$RAW/python/app/$f" -o "$APP_DIR/$f"
done
for t in index.html detail.html error.html; do
    echo "   → 下载模板 $t"
    curl -fL "$RAW/python/app/templates/$t" -o "$APP_DIR/templates/$t"
done
echo "   → 下载 run.py"
curl -fL "$RAW/run.py" -o "$INSTALL_DIR/run.py"
log "源码下载完成"

if [ ! -f "$APP_DIR/routes.py" ]; then
    err "关键文件缺失，请重试"
fi

echo ""
echo "🌐 [6/8] 启动服务..."
pkill -f "cloudflared tunnel" 2>/dev/null || true
for PID in $(pgrep -f "python.*run\.py"); do kill "$PID" 2>/dev/null; done
cd "$INSTALL_DIR"

# Flask 后台运行 (日志写 app.log)
nohup python run.py --host 0.0.0.0 --port 5000 > app.log 2>&1 &
sleep 2
if timeout 5 curl -sf http://127.0.0.1:5000/health >/dev/null 2>&1; then
    log "Flask 服务已启动: http://127.0.0.1:5000"
else
    warn "Flask 可能还在启动中，日志: tail -f app.log"
fi

echo ""
echo "════════════════════════════════════════════"
echo "  🖥  控制台地址: http://127.0.0.1:5000"
echo "════════════════════════════════════════════"
echo ""
echo "▶ 正在前台启动 Cloudflare Tunnel (日志直接显示)..."
echo "▶ 看到 https://xxx.trycloudflare.com 即隧道地址"
echo "▶ 按 Ctrl+C 停止"
echo ""

# 可选自动清理
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
    nohup "$INSTALL_DIR/auto-cleanup.sh" > /dev/null 2>&1 &
    log "自动清理已启动"
fi

# cloudflared 前台运行 (日志直接显示在屏幕上，像截图那样)
exec cloudflared tunnel --url http://127.0.0.1:5000
