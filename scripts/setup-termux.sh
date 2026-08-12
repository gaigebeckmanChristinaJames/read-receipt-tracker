#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# read-receipt-tracker · Termux 一键部署脚本
# 适用场景: Android Termux 环境 + Cloudflare Tunnel 内网穿透
# ============================================================
set -e

echo "🚀 [1/7] 环境检测与保活配置..."

if [ ! -d "/data/data/com.termux" ]; then
    echo "❌ 此脚本仅支持 Termux 环境运行"
    exit 1
fi

# 保活锁
termux-wake-lock 2>/dev/null || true

# Root 强化保活 (可选)
if command -v su >/dev/null 2>&1 && su -c "exit" >/dev/null 2>&1; then
    echo "☢️  Root 探测成功，正在解除 Android 系统限制..."
    su -c "dumpsys deviceidle whitelist +com.termux" >/dev/null 2>&1 || true
    su -c "am set-standby-bucket com.termux active" >/dev/null 2>&1 || true
    su -c "cmd appops set com.termux RUN_IN_BACKGROUND allow" >/dev/null 2>&1 || true
fi

echo "📦 [2/7] 配置软件源…"
echo "deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main" > "$PREFIX/etc/apt/sources.list"
pkg update -y && pkg upgrade -y || true

echo "🐍 [3/7] 安装依赖…"
pkg install -y python wget cloudflared curl tur-repo

echo "📦 [4/7] 安装 Python 依赖…"
pip install flask -i https://pypi.tuna.tsinghua.edu.cn/simple

echo "📁 [5/7] 准备运行目录…"
INSTALL_DIR="$HOME/read-receipt-tracker"
mkdir -p "$INSTALL_DIR"

echo "📝 [6/7] 编写守护进程…"
cat << 'WATCHDOG' > "$INSTALL_DIR/watchdog.sh"
#!/data/data/com.termux/files/usr/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
LAST_IP=""

while true; do
    # 公网 IP 变化检测
    CURRENT_IP=$(curl -s --max-time 3 https://1.1.1.1/cdn-cgi/trace | grep -oE "ip=[0-9.]+" | cut -d= -f2)
    if [ -n "$CURRENT_IP" ] && [ "$CURRENT_IP" != "$LAST_IP" ]; then
        if [ -n "$LAST_IP" ]; then
            echo "[$(date)] 网络切换 $LAST_IP → $CURRENT_IP，重启隧道…" >> daemon.log
            pkill -f "cloudflared" 2>/dev/null || true
            rm -f current_url.txt tunnel.log
        fi
        LAST_IP="$CURRENT_IP"
    fi

    # 保活 Flask
    if ! curl -sf --max-time 2 http://127.0.0.1:5000/health | grep -q '"ok"'; then
        pkill -f "app.py" 2>/dev/null || true
        nohup python "$DIR/../app.py" > app.log 2>&1 &
    fi

    # 保活 Cloudflared 隧道
    if ! pgrep -f "cloudflared" >/dev/null 2>&1; then
        rm -f tunnel.log current_url.txt
        cloudflared tunnel --protocol http2 --url http://127.0.0.1:5000 > tunnel.log 2>&1 < /dev/null &
    fi

    # 提取公网 URL
    if [ ! -f current_url.txt ] && [ -f tunnel.log ]; then
        URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' tunnel.log | tail -1)
        [ -n "$URL" ] && echo "$URL" > current_url.txt
    fi

    sleep 3
done
WATCHDOG

chmod +x "$INSTALL_DIR/watchdog.sh"

echo "🌐 [7/7] 启动守护进程..."

# 清理旧进程
pkill -f "watchdog.sh" 2>/dev/null || true
pkill -f "cloudflared" 2>/dev/null || true

nohup "$INSTALL_DIR/watchdog.sh" > /dev/null 2>&1 &

# 等待公网 URL
echo ""
echo "⏳ 等待公网隧道就绪 (最多 20 秒)..."
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
            exit 0
        fi
    fi
    echo -n "."
    sleep 1
done

echo ""
echo "⚠️  自动提取超时，但服务已在后台运行。"
echo "👉 手动获取地址: cat $INSTALL_DIR/current_url.txt"
echo "👉 查看原始日志: tail -f $INSTALL_DIR/tunnel.log"
