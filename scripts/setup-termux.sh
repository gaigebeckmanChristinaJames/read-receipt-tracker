#!/data/data/com.termux/files/usr/bin/bash
# ================================================================
# read-receipt-tracker · Termux 一键部署脚本 v2.1
# 自动安装 uv + ruff + meson + ninja + 编译依赖
# 配置 Flask 服务 + Cloudflare Tunnel 内网穿透
# 可选: 自动清理过期聊天记录 (--cleanup N)
# ================================================================
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
INSTALL_DIR="$HOME/read-receipt-tracker"
DIR="$(cd "$(dirname "$0")" && pwd)"

# 可选参数: --cleanup 天数
CLEANUP_DAYS=""
for arg in "$@"; do
    case "$arg" in
        --cleanup) CLEANUP_DAYS="$2"; shift 2 ;;
        --cleanup=*) CLEANUP_DAYS="${arg#*=}" ;;
    esac
done

log() { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err() { echo -e "${RED}[✗]${NC} $1"; exit 1; }

echo "🚀 [1/9] 环境检测与保活配置..."

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

echo "📦 [2/9] 配置软件源…"
echo "deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main" > "$PREFIX/etc/apt/sources.list"
pkg update -y -q && pkg upgrade -y -q || true

echo "📦 [3/9] 安装系统依赖…"
pkg install -y python wget curl cloudflared tur-repo clang make cmake ninja pkg-config sqlite openssl rust binutils 2>/dev/null || true
pkg install -y python wget curl cloudflared clang ninja pkg-config sqlite rust 2>/dev/null || true

echo "🐍 [4/9] 安装 uv (Python 包管理器)…"
if ! command -v uv >/dev/null 2>&1; then
    # 方案1: 官方一键脚本
    if curl -LsSf https://astral.sh/uv/install.sh | sh 2>/dev/null; then
        export PATH="$HOME/.local/bin:$PATH"
        log "uv 安装完成 (官方脚本): $(uv --version 2>/dev/null)"
    # 方案2: pip 安装
    elif pip install uv -i https://pypi.tuna.tsinghua.edu.cn/simple 2>/dev/null; then
        log "uv 安装完成 (pip): $(uv --version 2>/dev/null)"
    # 方案3: cargo 源码编译
    elif command -v cargo >/dev/null 2>&1; then
        echo "   🔨 cargo 源码编译 uv (需要几分钟)…"
        cargo install uv 2>/dev/null && {
            export PATH="$HOME/.cargo/bin:$PATH"
            log "uv 安装完成 (cargo): $(uv --version 2>/dev/null)"
        } || warn "cargo 编译失败，跳过 uv"
    else
        warn "uv 安装失败 (脚本/pip/cargo 均不可用)，回退到 pip"
    fi
else
    log "uv 已安装: $(uv --version)"
fi

echo "🐍 [5/9] 安装 Python 依赖…"
cd "$DIR"
if command -v uv >/dev/null 2>&1; then
    uv pip install --system flask 2>/dev/null || pip install flask -i https://pypi.tuna.tsinghua.edu.cn/simple
else
    pip install flask -i https://pypi.tuna.tsinghua.edu.cn/simple
fi

# 可选：ruff 代码检查
echo "🧹 [6/9] 安装 ruff (代码检查)…"
if command -v uv >/dev/null 2>&1; then
    uv pip install --system ruff 2>/dev/null || true
else
    pip install ruff -i https://pypi.tuna.tsinghua.edu.cn/simple 2>/dev/null || true
fi

echo "🔧 [7/9] 编译 C++ 后端…"
if [ -f "$DIR/cpp/meson.build" ]; then
    which meson 2>/dev/null || pkg install -y meson 2>/dev/null || pip install meson
    cd "$DIR/cpp"
    meson setup builddir 2>/dev/null && meson compile -C builddir 2>/dev/null && log "C++ 后端编译成功" || warn "C++ 编译跳过（非必需）"
    cd "$DIR"
fi

echo "📁 [8/9] 准备运行目录…"
mkdir -p "$INSTALL_DIR"

echo "📝 [9/9] 编写守护进程 + Cloudflare Tunnel…"
cat << 'WATCHDOG' > "$INSTALL_DIR/watchdog.sh"
#!/data/data/com.termux/files/usr/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR" ; LAST_IP=""

while true; do
    # 公网 IP 变化检测
    CURRENT_IP=$(curl -s --max-time 3 https://1.1.1.1/cdn-cgi/trace 2>/dev/null | grep -oE "ip=[0-9.]+" | cut -d= -f2)
    if [ -n "$CURRENT_IP" ] && [ "$CURRENT_IP" != "$LAST_IP" ]; then
        if [ -n "$LAST_IP" ]; then
            echo "[$(date)] 网络切换 $LAST_IP → $CURRENT_IP，重启隧道…" >> daemon.log
            pkill -f "cloudflared" 2>/dev/null || true
            rm -f current_url.txt tunnel.log
        fi
        LAST_IP="$CURRENT_IP"
    fi

    # 保活 Flask
    if ! curl -sf --max-time 2 http://127.0.0.1:5000/health >/dev/null 2>&1; then
        pkill -f "app.py" 2>/dev/null || true
        nohup python "$DIR/../python/app/app.py" > app.log 2>&1 &
        sleep 2
    fi

    # 保活 Cloudflared
    if ! pgrep -f "cloudflared" >/dev/null 2>&1; then
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

echo "🌐 启动守护进程…"
pkill -f "watchdog.sh" 2>/dev/null || true
pkill -f "cloudflared" 2>/dev/null || true

nohup "$INSTALL_DIR/watchdog.sh" > /dev/null 2>&1 &

# 等待公网 URL
echo ""; echo "⏳ 等待公网隧道就绪 (最多 20 秒)..."
for i in $(seq 1 20); do
    if [ -f "$INSTALL_DIR/current_url.txt" ]; then
        URL=$(cat "$INSTALL_DIR/current_url.txt")
        if [ -n "$URL" ]; then
            echo ""
            echo "════════════════════════════════════════"
            echo "  ✅ 部署成功！"
            echo "  🔗 公网地址: $URL"
            echo "════════════════════════════════════════"
            echo ""
            echo "💡 常用命令:"
            echo "   查看地址:  cat $INSTALL_DIR/current_url.txt"
            echo "   查看日志:  tail -f $INSTALL_DIR/daemon.log"
            echo "   停止服务:  pkill -f watchdog.sh && pkill -f app.py && pkill cloudflared"
            echo "   代码检查:  cd ~/read-receipt-tracker && ruff check python/"
            exit 0
        fi
    fi
    echo -n "."
    sleep 1
done

echo ""
warn "自动提取超时，但服务已在后台运行"
echo "👉 手动获取地址: cat $INSTALL_DIR/current_url.txt"
echo "👉 查看原始日志: tail -f $INSTALL_DIR/tunnel.log"

# ================================================================
#  可选: 自动清理过期聊天记录
# ================================================================
if [ -n "$CLEANUP_DAYS" ]; then
    echo ""
    echo "🧹 [额外] 配置自动清理: 每 ${CLEANUP_DAYS} 天清除旧记录"

    cat << 'CLEANSCRIPT' > "$INSTALL_DIR/auto-cleanup.sh"
#!/data/data/com.termux/files/usr/bin/bash
# ================================================================
# read-receipt-tracker · 自动清理过期记录
# 定期删除超过指定天数的消息和已读记录
# 配置方法: 修改 CLEANUP_DAYS 变量
# 启用: bash setup-termux.sh --cleanup 30
# 禁用: pkill -f auto-cleanup.sh
# 手动运行: bash auto-cleanup.sh --once
# ================================================================

CLEANUP_DAYS=__DAYS__
DB_PATH="${DATABASE_PATH:-$HOME/read-receipt-tracker/receipts.db}"
LOG_FILE="$HOME/read-receipt-tracker/cleanup.log"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$LOG_FILE"; }

if [ "$1" = "--once" ]; then
    # 手动执行一次
    if [ -f "$DB_PATH" ]; then
        DEL_READS=$(sqlite3 "$DB_PATH" \
            "DELETE FROM reads WHERE read_at < CAST(strftime('%s','now') AS INTEGER) - ${CLEANUP_DAYS}*86400; SELECT changes();")
        DEL_MSGS=$(sqlite3 "$DB_PATH" \
            "DELETE FROM messages WHERE registered_at < CAST(strftime('%s','now') AS INTEGER) - ${CLEANUP_DAYS}*86400; SELECT changes();")
        ORPHANS=$(sqlite3 "$DB_PATH" \
            "DELETE FROM reads WHERE msg_id NOT IN (SELECT id FROM messages); SELECT changes();")
        echo "清理完成: 消息+${DEL_MSGS}条, 已读+${DEL_READS}条, 孤儿+${ORPHANS}条"
        log "手动清理: 消息-${DEL_MSGS}, 已读-${DEL_READS}, 孤儿-${ORPHANS}"
    fi
    exit 0
fi

while true; do
    if [ -f "$DB_PATH" ]; then
        DEL_READS=$(sqlite3 "$DB_PATH" \
            "DELETE FROM reads WHERE read_at < CAST(strftime('%s','now') AS INTEGER) - ${CLEANUP_DAYS}*86400; SELECT changes();")
        DEL_MSGS=$(sqlite3 "$DB_PATH" \
            "DELETE FROM messages WHERE registered_at < CAST(strftime('%s','now') AS INTEGER) - ${CLEANUP_DAYS}*86400; SELECT changes();")
        ORPHANS=$(sqlite3 "$DB_PATH" \
            "DELETE FROM reads WHERE msg_id NOT IN (SELECT id FROM messages); SELECT changes();")

        if [ "$DEL_READS" != "0" ] || [ "$DEL_MSGS" != "0" ] || [ "$ORPHANS" != "0" ]; then
            log "清理: 消息-${DEL_MSGS}, 已读-${DEL_READS}, 孤儿-${ORPHANS}"
        fi
    fi
    # 每 24 小时执行一次
    sleep 86400
done
CLEANSCRIPT

    sed -i "s/__DAYS__/${CLEANUP_DAYS}/" "$INSTALL_DIR/auto-cleanup.sh"
    chmod +x "$INSTALL_DIR/auto-cleanup.sh"

    pkill -f "auto-cleanup.sh" 2>/dev/null || true
    nohup "$INSTALL_DIR/auto-cleanup.sh" > /dev/null 2>&1 &

    log "自动清理已配置: 保留最近 ${CLEANUP_DAYS} 天，每 24h 执行一次"
    echo "   📋 清理日志: tail -f $INSTALL_DIR/cleanup.log"
    echo "   ⚡ 手动执行: bash $INSTALL_DIR/auto-cleanup.sh --once"
    echo "   ⏸  停止清理: pkill -f auto-cleanup.sh"
fi

if [ -z "$CLEANUP_DAYS" ]; then
    echo ""
    warn "提示: 可启用自动清理过期消息"
    echo "   用法: bash setup-termux.sh --cleanup 30  (仅保留最近30天)"
    echo "         bash setup-termux.sh --cleanup 7   (仅保留最近7天)"
fi

exit 0
