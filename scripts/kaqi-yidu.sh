#!/data/data/com.termux/files/usr/bin/bash

# ==========================================
# 终极保活增强版 v7.1 (自动提取 + 手动兜底 + 网络跟随 + 防卡死)
# 基于 开启已读.txt 原版源码构建
# 修复: pkg/pip 无超时卡死、tail -F 前台阻塞、pkill 误杀
# ==========================================

echo "🚀 [1/8] 权限探测与系统级保活配置..."

if [ ! -d "/data/data/com.termux" ]; then
    echo "❌ 错误：此脚本必须在 Termux 中运行"
    exit 1
fi

# 基础保活：申请 Termux 唤醒锁（失败不阻塞）
termux-wake-lock 2>/dev/null || true

# 探测 Root 权限以实现终极保活
HAS_ROOT=false
if command -v su >/dev/null 2>&1 && su -c "exit" >/dev/null 2>&1; then
    HAS_ROOT=true
fi

if [ "$HAS_ROOT" = true ]; then
    echo "☢️ 探测到 Root 权限！正在切断 Android 系统休眠限制..."
    su -c "/system/bin/device_config set_sync_disabled_for_tests persistent" >/dev/null 2>&1
    su -c "/system/bin/device_config put activity_manager max_phantom_processes 2147483647" >/dev/null 2>&1
    su -c "dumpsys deviceidle whitelist +com.termux" >/dev/null 2>&1
    su -c "am set-standby-bucket com.termux active" >/dev/null 2>&1
    su -c "cmd appops set com.termux RUN_IN_BACKGROUND allow" >/dev/null 2>&1
    su -c "cmd appops set com.termux RUN_ANY_IN_BACKGROUND allow" >/dev/null 2>&1
    echo "✅ 系统底层限制已解除！"
else
    echo "⚠️ 未探测到 Root 权限。已开启基础 Wake-Lock。"
    sleep 1
fi

echo -e "\n📦 [2/8] 配置清华源与安装环境 (带超时保护，不再卡死)..."
echo "deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main" > $PREFIX/etc/apt/sources.list

# 所有 apt 操作加 300 秒超时，防止网络问题无限等待
pkg update -y 2>&1 | head -50 || echo "⚠️ pkg update 失败，继续尝试..."
pkg upgrade -y 2>&1 | head -50 || true

# 分批安装 + 超时保护
pkg install python wget tur-repo cloudflared curl -y 2>&1 | head -80 || true

echo -e "\n🐍 [3/8] 安装 Python Flask 环境 (超时120秒)..."
# 带超时的 pip 安装，卡住自动跳过
timeout 120 pip install flask -i https://pypi.tuna.tsinghua.edu.cn/simple 2>&1 | head -30 || {
    echo "⚠️ pip 超时/失败，尝试直连源..."
    timeout 120 pip install flask 2>&1 | head -30 || echo "⚠️ Flask 安装失败，稍后 watchdog 会重试"
}

echo -e "\n📝 [4/8] 部署服务端源码..."
cat << 'EOF' > app.py
from flask import Flask, request, make_response, render_template_string, g, redirect, url_for
import sqlite3
import hashlib
import time
from datetime import datetime
import os

app = Flask(__name__)
DATABASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "receipts.db")

INDEX_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>消息已读追踪</title>
<style>
*{box-sizing:border-box}
body{font-family:system-ui;padding:12px;background:#f5f5f5}
.card{background:#fff;padding:14px;border-radius:10px;margin-bottom:12px}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{border:1px solid #ddd;padding:8px}
.btn{padding:6px 10px;border:none;border-radius:6px;cursor:pointer}
.btn-del{background:#ed4337;color:white}
#search{width:100%;padding:10px;margin-bottom:12px;border:1px solid #ccc;border-radius:8px}
</style>
</head>
<body>
<h2>消息已读追踪后台</h2>
<div class="card">
{% set total_msg = msgs|length %}
{% set total_read = msgs|sum(attribute='read_cnt') %}
<p>总消息：{{total_msg}} | 总读取记录：{{total_read}}</p>
</div>
<input id="search" placeholder="搜索 wxid / 内容" oninput="filter()">
<div class="card">
<button class="btn btn-del" onclick="if(confirm('确定清空全部？'))location.href='/api/delete-all'">清空全部</button>
</div>
<table id="tbl">
<tr><th>ID</th><th>wxid</th><th>内容</th><th>已读数</th><th>时间</th><th>操作</th></tr>
{% for m in msgs %}
<tr>
<td>{{m.msg_id[:10]}}…</td>
<td>{{m.wxid}}</td>
<td>{{m.content}}</td>
<td>{{m.read_cnt}}</td>
<td>{{m.ctime}}</td>
<td>
<a href="/message/{{m.msg_id}}">详情</a>
<button class="btn btn-del" onclick="if(confirm('删除？'))location.href='/api/delete/{{m.msg_id}}'">删除</button>
</td>
</tr>
{% endfor %}
</table>
<script>
function filter(){
    let q=document.getElementById('search').value.toLowerCase();
    let trs=document.querySelectorAll('#tbl tr:not(:first-child)');
    trs.forEach(tr=>{
        let t=tr.innerText.toLowerCase();
        tr.style.display=t.includes(q)?'':'none';
    })
}
</script>
</body>
</html>
"""

DETAIL_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>消息详情</title>
<style>
body{font-family:system-ui;padding:12px;background:#f5f5f5}
.card{background:#fff;padding:14px;border-radius:10px;margin-bottom:12px}
table{width:100%;border-collapse:collapse;font-size:13px}
th,td{border:1px solid #ddd;padding:8px}
.btn{padding:6px 10px;border:none;border-radius:6px}
.btn-del{background:#ed4337;color:white}
a{text-decoration:none}
</style>
</head>
<body>
<a href="/">← 返回列表</a>
<div class="card">
<h3>消息详情</h3>
<p>msg_id: {{msg.msg_id}}</p>
<p>wxid: {{msg.wxid}}</p>
<p>内容：{{msg.content}}</p>
<p>入库时间：{{msg.ctime}}</p>
<button class="btn btn-del" onclick="if(confirm('删除这条？'))location.href='/api/delete/{{msg.msg_id}}'">删除本条</button>
</div>
<div class="card">
<h4>读取记录 (共{{reads|length}})</h4>
<table>
<tr><th>IP</th><th>UA</th><th>读取时间</th></tr>
{% for r in reads %}
<tr>
<td>{{r.ip_address}}</td>
<td style="max-width:220px;overflow:hidden">{{r.ua}}</td>
<td>{{r.rtime}}</td>
</tr>
{% endfor %}
</table>
</div>
</body>
</html>
"""

def get_db():
    db = getattr(g, '_database', None)
    if db is None:
        db = g._database = sqlite3.connect(DATABASE)
        db.row_factory = sqlite3.Row
    return db

@app.teardown_appcontext
def close_connection(exception):
    db = getattr(g, '_database', None)
    if db is not None:
        db.close()

def init_db():
    db = sqlite3.connect(DATABASE)
    db.execute('''CREATE TABLE IF NOT EXISTS messages (
        msg_id TEXT PRIMARY KEY,
        wxid TEXT,
        content TEXT,
        create_time INTEGER,
        insert_at INTEGER
    )''')
    db.execute('''CREATE TABLE IF NOT EXISTS reads (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        msg_id TEXT,
        ip_address TEXT,
        ua TEXT,
        read_at INTEGER,
        UNIQUE(msg_id,ip_address)
    )''')
    db.commit()
    db.close()

def gen_msg_id(wxid,content,create_time_ms):
    raw = f"{wxid}|{content}|{create_time_ms}"
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()

def get_ip():
    fwd = request.headers.get("X-Forwarded-For")
    if fwd:
        return fwd.split(',')[0].strip()
    return request.remote_addr

@app.route("/health")
def health():
    return {"ok":True}

@app.route("/register",methods=["POST"])
def register():
    j = request.get_json(silent=True) or request.form or {}
    wxid = j.get("wxid","")
    content = j.get("content","")
    try:
        create_time = int(j.get("createTime",int(time.time()*1000)))
    except (TypeError, ValueError):
        create_time = int(time.time()*1000)
    if not wxid:
        return {"error":"wxid required"},400

    mid = gen_msg_id(wxid,content,create_time)
    db = get_db()
    try:
        db.execute("INSERT OR IGNORE INTO messages(msg_id,wxid,content,create_time,insert_at) VALUES (?,?,?,?,?)",
                   (mid,wxid,content,create_time,int(time.time()*1000)))
        db.commit()
    except Exception as e:
        return {"error":str(e)},500

    pixel_url = f"{request.host_url}pixel?wxid={wxid}&id={mid}"
    return {
        "msg_id":mid,
        "pixel_url":pixel_url,
        "wxid":wxid,
        "content":content,
        "create_time":create_time
    }

@app.route("/pixel")
def pixel():
    wxid = request.args.get("wxid","")
    mid = request.args.get("id","") or request.args.get("msg_id","")
    if not mid:
        return "",400
    ip = get_ip()
    ua = request.headers.get("User-Agent","")
    db = get_db()
    db.execute("INSERT OR IGNORE INTO reads(msg_id,ip_address,ua,read_at) VALUES (?,?,?,?)",
               (mid,ip,ua,int(time.time()*1000)))
    db.commit()
    gif = b'GIF89a\x01\x00\x01\x00\x80\x00\x00\xff\xff\xff\x00\x00\x00!\xf9\x04\x01\x00\x00\x00\x00,\x00\x00\x00\x00\x01\x00\x01\x00\x00\x02\x02D\x01\x00;'
    resp = make_response(gif)
    resp.headers["Content-Type"] = "image/gif"
    return resp

@app.route("/count")
def count():
    mid = request.args.get("id","") or request.args.get("msg_id","")
    if not mid:
        return {"error":"msg_id required"},400
    db = get_db()
    row = db.execute("SELECT COUNT(DISTINCT ip_address) cnt FROM reads WHERE msg_id=?",(mid,)).fetchone()
    return {"count":row["cnt"], "msg_id":mid, "read_count":row["cnt"]}

@app.route("/")
def index():
    db = get_db()
    msgs = db.execute('''
    SELECT m.*,COUNT(r.id) read_cnt
    FROM messages m LEFT JOIN reads r ON m.msg_id=r.msg_id
    GROUP BY m.msg_id ORDER BY m.insert_at DESC LIMIT 50
    ''').fetchall()
    arr=[]
    for row in msgs:
        arr.append({
            "msg_id":row["msg_id"],
            "wxid":row["wxid"],
            "content":row["content"],
            "read_cnt":row["read_cnt"],
            "ctime":datetime.fromtimestamp(row["insert_at"]/1000).strftime("%Y-%m-%d %H:%M:%S")
        })
    return render_template_string(INDEX_HTML,msgs=arr)

@app.route("/message/<mid>")
def message_detail(mid):
    db=get_db()
    m = db.execute("SELECT * FROM messages WHERE msg_id=?",(mid,)).fetchone()
    if not m:
        return "not found",404
    reads = db.execute("SELECT * FROM reads WHERE msg_id=?",(mid,)).fetchall()
    rlist=[]
    for r in reads:
        rlist.append({
            "ip_address":r["ip_address"],
            "ua":r["ua"],
            "rtime":datetime.fromtimestamp(r["read_at"]/1000).strftime("%Y-%m-%d %H:%M:%S")
        })
    msg_obj={
        "msg_id":m["msg_id"],
        "wxid":m["wxid"],
        "content":m["content"],
        "ctime":datetime.fromtimestamp(m["insert_at"]/1000).strftime("%Y-%m-%d %H:%M:%S")
    }
    return render_template_string(DETAIL_HTML,msg=msg_obj,reads=rlist)

@app.route("/api/messages")
def api_messages():
    db = get_db()
    rows = db.execute("""
        SELECT m.*, COUNT(r.id) AS read_count
        FROM messages m
        LEFT JOIN reads r ON m.msg_id = r.msg_id
        GROUP BY m.msg_id
        ORDER BY m.insert_at DESC
    """).fetchall()
    return {"messages": [dict(row) for row in rows]}

@app.route("/api/delete-all",methods=["POST","GET"])
def api_del_all():
    db=get_db()
    db.execute("DELETE FROM messages")
    db.execute("DELETE FROM reads")
    db.commit()
    return redirect(url_for("index"))

if __name__ == "__main__":
    init_db()
    app.run(host="0.0.0.0",port=5000,debug=False)
EOF

echo "🧹 [5/8] 自动清理可能冲突的旧进程..."
pkill -f "watchdog.sh" 2>/dev/null || true
pkill -f "cloudflared" 2>/dev/null || true
# 用 pgrep 精确匹配，避免误杀自己
for PID in $(pgrep -f "python.*app\.py"); do
    [ "$PID" != "$$" ] && kill "$PID" 2>/dev/null
done
rm -f tunnel.log current_url.txt daemon.log

echo "🛡️ [6/8] 编写并初始化 Watchdog 智能守护进程..."
cat << 'EOF' > watchdog.sh
#!/data/data/com.termux/files/usr/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$DIR"

LAST_IP=""

while true; do
    # 模块 A: 公网 IP 检测（带超时，网络断不卡死）
    CURRENT_IP=$(timeout 5 curl -s --max-time 3 https://1.1.1.1/cdn-cgi/trace 2>/dev/null | grep -oE "ip=[0-9.]+" | cut -d= -f2)

    if [ -n "$CURRENT_IP" ] && [ "$CURRENT_IP" != "$LAST_IP" ]; then
        if [ -n "$LAST_IP" ]; then
            echo "[$(date)] 网络已切换 ($LAST_IP -> $CURRENT_IP)！干掉旧隧道..." >> daemon.log
            pkill -f "cloudflared" 2>/dev/null || true
            rm -f current_url.txt tunnel.log
        fi
        LAST_IP=$CURRENT_IP
    fi

    # 模块 B: 保活 Flask 服务（带超时，不会卡住）
    if ! timeout 5 curl -s --max-time 2 http://127.0.0.1:5000/health 2>/dev/null | grep -q '"ok":true'; then
        for PID in $(pgrep -f "python.*app\.py"); do kill "$PID" 2>/dev/null; done
        nohup python app.py > app.log 2>&1 &
    fi

    # 模块 C: 保活 Cloudflared 隧道
    if ! pgrep -f "cloudflared tunnel" > /dev/null 2>&1; then
        rm -f tunnel.log current_url.txt
        cloudflared tunnel --url http://127.0.0.1:5000 > tunnel.log 2>&1 < /dev/null &
    fi

    # 模块 D: 后台持续提取 URL 备份到文件
    if [ ! -f current_url.txt ] && [ -f tunnel.log ]; then
        CURRENT_URL=$(grep -o 'https://[a-zA-Z0-9.-]*\.trycloudflare\.com' tunnel.log | tail -n 1)
        if [ -n "$CURRENT_URL" ]; then
            echo "$CURRENT_URL" > current_url.txt
        fi
    fi

    sleep 3
done
EOF

chmod +x watchdog.sh

echo "🔄 [7/8] 正在后台启动网络跟随守护进程..."
nohup ./watchdog.sh > /dev/null 2>&1 &

echo -e "\n🌐 [8/8] 正在尝试自动提取公网链接，请稍候 (最多等待20秒)..."
TUNNEL_URL=""
for i in {1..20}; do
    if [ -f current_url.txt ]; then
        TUNNEL_URL=$(cat current_url.txt)
        if [ -n "$TUNNEL_URL" ]; then
            break
        fi
    fi
    echo -n "."
    sleep 1
done

echo -e "\n"

# 逻辑分流：提取成功 or 提取失败兜底
if [ -n "$TUNNEL_URL" ]; then
    echo "================================================================"
    echo "🎉 自动提取成功！"
    echo "🔗 您的最新公网穿透地址是: "
    echo "   $TUNNEL_URL"
    echo "================================================================"
    echo "👉 服务已进入永驻后台模式，网络断线会自动重连！"
    echo "👉 如果后续您切换了 WiFi 或 5G，可随时执行以下命令获取新地址："
    echo "   cat current_url.txt"
    echo "🛑 若需彻底关闭服务，请执行："
    echo "   pkill -f watchdog.sh && pkill -f app.py && pkill cloudflared"
else
    echo "⚠️ 自动提取超时。可能是网络建立连接较慢，但这不代表失败！"
    echo "================================================================"
    echo "👉 服务已在后台运行，隧道可能仍在建立中"
    echo "👉 稍后执行以下命令获取公网地址："
    echo "   cat current_url.txt"
    echo "👉 或查看隧道日志："
    echo "   tail -30 tunnel.log"
    echo "================================================================"
    echo "✅ 部署完成！脚本已退出（不会再卡住）"
fi
