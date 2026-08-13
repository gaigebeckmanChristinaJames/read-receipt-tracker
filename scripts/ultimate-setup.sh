#!/data/data/com.termux/files/usr/bin/bash
# ================================================================
# read-receipt-tracker · 终极一键版 PRO (含 IP 定位，零下载，全内嵌)
# 所有代码内置在脚本中，不需要从任何网站下载文件
# 只需: bash setup.sh
# ================================================================

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log() { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }

echo "=== read-receipt-tracker 一键部署 (IP 定位默认开启) ==="
echo "   关闭定位(Lite模式): ENABLE_GEO=0 python app.py"
echo ""

echo "[1/4] 检查环境 + 配置清华源..."
if [ ! -d "/data/data/com.termux" ]; then
    echo "提示: 非 Termux 环境，继续尝试..."
fi
termux-wake-lock 2>/dev/null || true

# 自动配置清华源 (解决 pkg 卡住/下载慢)
if [ -w "$PREFIX/etc/apt/sources.list" ]; then
    echo "  写入清华镜像源..."
    echo "deb https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main stable main" > "$PREFIX/etc/apt/sources.list"
    echo "  更新软件列表..."
    pkg update -y 2>&1 | tail -3 || true
else
    echo "  无法写 sources.list，跳过换源"
fi
log "环境 OK"

echo ""
echo "[2/4] 安装 Python + Flask (如已装会跳过)..."
command -v python >/dev/null 2>&1 || { echo "  安装 python..."; pkg install -y python; }
python -c "import flask" 2>/dev/null && log "Flask 已安装" || {
    echo "  安装 Flask (清华源)..."
    pip install flask -i https://pypi.tuna.tsinghua.edu.cn/simple 2>/dev/null || pip install flask
    log "Flask 安装完成"
}

echo ""
echo "[3/4] 写入服务代码 (全部内嵌，无网络依赖)..."
mkdir -p "$HOME/rrt"
cat << 'PYEOF' > "$HOME/rrt/app.py"
#!/usr/bin/env python3
import os, sqlite3, hashlib, time
from datetime import datetime
from flask import Flask, request, jsonify, send_file, render_template_string, g
from io import BytesIO

app = Flask(__name__)
BASE = os.path.dirname(os.path.abspath(__file__))
DATABASE = os.path.join(BASE, "receipts.db")

TRANSPARENT_GIF = bytes([
    0x47,0x49,0x46,0x38,0x39,0x61,0x01,0x00,0x01,0x00,
    0x80,0x00,0x00,0x00,0x00,0x00,0xFF,0xFF,0xFF,0x21,
    0xF9,0x04,0x01,0x00,0x00,0x00,0x00,0x2C,0x00,0x00,
    0x00,0x00,0x01,0x00,0x01,0x00,0x00,0x02,0x02,0x44,
    0x01,0x00,0x3B,
])

INDEX_HTML = r'''<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>消息已读追踪后台</title>
<style>
:root{--bg:#0d1117;--card:#161b22;--bd:#30363d;--text:#e6edf3;--t2:#8b949e;--ok:#3fb950;--danger:#f85149;--blue:#58a6ff}
*{margin:0;padding:0;box-sizing:border-box}
body{background:var(--bg);font-family:system-ui;color:var(--text);line-height:1.6}
.c{max-width:1100px;margin:0 auto;padding:20px}
.top{display:flex;gap:10px;margin-bottom:18px}
.sb{flex:1;display:flex;align-items:center;background:var(--card);border:1px solid var(--bd);border-radius:10px;padding:0 14px}
.sb input{flex:1;background:none;border:none;color:var(--text);padding:12px 8px;font-size:15px;outline:none}
h1{font-size:22px;margin-bottom:14px}
.st{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px;margin-bottom:18px}
.sc{background:var(--card);border:1px solid var(--bd);border-radius:12px;padding:18px;text-align:center}
.sc .n{font-size:32px;font-weight:800;color:var(--ok)}
.sc .n.b{color:var(--blue)}
.sc .l{color:var(--t2);font-size:13px}
.btns{display:flex;gap:10px;margin-bottom:18px;flex-wrap:wrap}
.btn{border:none;border-radius:8px;padding:10px 18px;font-size:14px;font-weight:600;cursor:pointer}
.btn-d{background:var(--danger);color:#fff}
.btn-o{background:var(--ok);color:#fff}
.btn-g{background:var(--card);color:var(--text);border:1px solid var(--bd)}
.tb{background:var(--card);border:1px solid var(--bd);border-radius:12px;overflow:hidden}
table{width:100%;border-collapse:collapse}
th,td{padding:12px 14px;text-align:left;border-bottom:1px solid var(--bd);font-size:14px}
th{background:#1c2129;color:var(--t2);font-size:12px;text-transform:uppercase}
tr:hover td{background:#1c2129}
.mono{font-family:monospace;font-size:12px;color:var(--blue)}
.badge{display:inline-block;padding:3px 10px;border-radius:16px;font-size:12px;font-weight:600}
.b-ok{background:rgba(63,185,80,.15);color:var(--ok)}
.b-0{background:rgba(139,148,158,.15);color:var(--t2)}
a{color:var(--blue);text-decoration:none;margin-right:10px}
.emp{padding:50px;text-align:center;color:var(--t2)}
</style></head><body><div class="c">
<div class="top"><div class="sb"><span>🔍</span><input id="s" placeholder="搜索 wxId / 内容" oninput="f()"></div>
<button class="btn btn-g" onclick="location.reload()">🔄</button></div>
<h1>消息已读追踪后台</h1>
<div class="st">
<div class="sc"><div class="n">{{tm}}</div><div class="l">总消息</div></div>
<div class="sc"><div class="n">{{tr}}</div><div class="l">总读取记录</div></div>
<div class="sc"><div class="n b">{{ar}}</div><div class="l">平均已读</div></div>
</div>
<div class="btns">
<button class="btn btn-o" onclick="expCSV()">📥 导出 CSV</button>
<button class="btn btn-d" onclick="delAll()">🧹 清空全部</button>
</div>
<div class="tb"><table>
<thead><tr><th>ID</th><th>wxId</th><th>内容</th><th>已读数</th><th>时间</th><th>操作</th></tr></thead>
<tbody id="list">
{%for m in msgs%}
<tr data-c="{{m.content}}" data-w="{{m.wxid}}">
<td class="mono">{{m.id[:12]}}…</td><td>{{m.wxid}}</td>
<td style="max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{m.content}}</td>
<td><span class="badge {%if m.cnt==0%}b-0{%else%}b-ok{%endif%}">{{m.cnt}} 人</span>{%if m.loc%}<span style="margin-left:6px;font-size:12px;color:var(--blue)">📍{{m.loc}}城</span>{%endif%}</td>
<td style="color:var(--t2)">{{m.t}}</td>
<td><a href="/message/{{m.id}}">详情</a><button class="btn btn-d" style="padding:4px 10px;font-size:12px" onclick="del('{{m.id}}')">删</button></td>
</tr>{%endfor%}
{%if not msgs%}<tr><td colspan="6" class="emp">📭 暂无消息</td></tr>{%endif%}
</tbody></table></div></div>
<script>
function f(){let v=document.getElementById("s").value.toLowerCase();document.querySelectorAll("#list tr[data-c]").forEach(r=>{r.style.display=(r.dataset.c+r.dataset.w).toLowerCase().includes(v)?"":"none"})}
async function del(id){if(!confirm("删除?"))return;await fetch("/api/delete/"+id,{method:"POST"});location.reload()}
async function delAll(){if(!confirm("清空全部?不可恢复!"))return;await fetch("/api/delete-all",{method:"POST"});location.reload()}
function expCSV(){let rows=[["ID","wxId","内容","已读数","时间"]];document.querySelectorAll("#list tr[data-c]").forEach(r=>{let td=r.querySelectorAll("td");rows.push([td[0].textContent.trim(),td[1].textContent.trim(),'"'+r.dataset.c.replace(/"/g,'""')+'"',td[3].textContent.trim(),td[4].textContent.trim()])});let b=new Blob(["\uFEFF"+rows.map(x=>x.join(",")).join("\n")],{type:"text/csv"});let a=document.createElement("a");a.href=URL.createObjectURL(b);a.download="export.csv";a.click()}
</script></body></html>'''

DETAIL_HTML = r'''<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>消息详情</title>
<style>
:root{--bg:#0d1117;--card:#161b22;--bd:#30363d;--text:#e6edf3;--t2:#8b949e;--ok:#3fb950;--danger:#f85149;--blue:#58a6ff}
*{margin:0;padding:0;box-sizing:border-box}
body{background:var(--bg);font-family:system-ui;color:var(--text);line-height:1.6}
.c{max-width:900px;margin:0 auto;padding:20px}
.back{color:var(--blue);text-decoration:none;display:inline-block;margin-bottom:16px}
.card{background:var(--card);border:1px solid var(--bd);border-radius:12px;padding:24px;margin-bottom:20px}
h2{font-size:19px;margin-bottom:16px}
.g{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin-bottom:16px}
.i{background:var(--bg);border:1px solid var(--bd);padding:14px;border-radius:8px}
.l{font-size:11px;color:var(--t2);text-transform:uppercase;margin-bottom:6px}
.v{word-break:break-all;font-size:14px}
.mono{font-family:monospace;font-size:12px;color:var(--blue)}
.cb{background:var(--bg);border:1px solid var(--bd);border-left:3px solid var(--ok);padding:18px;border-radius:8px;white-space:pre-wrap;margin:10px 0}
.cnt{display:inline-block;padding:7px 18px;border-radius:20px;background:rgba(63,185,80,.15);color:var(--ok);font-weight:700}
.cnt.z{background:rgba(139,148,158,.15);color:var(--t2)}
table{width:100%;border-collapse:collapse;margin-top:12px}
th,td{padding:12px;text-align:left;border-bottom:1px solid var(--bd);font-size:14px}
th{background:#1c2129;color:var(--t2);font-size:12px}
.ip{font-family:monospace;color:var(--blue)}
.ua{font-size:12px;color:var(--t2);max-width:250px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.btn-d{background:var(--danger);color:#fff;border:none;padding:10px 18px;border-radius:8px;cursor:pointer;margin-top:14px}
.emp{padding:36px;text-align:center;color:var(--t2)}
</style></head><body><div class="c">
<a class="back" href="/">← 返回</a>
<div class="card"><h2>📨 消息信息</h2>
<div class="g">
<div class="i"><div class="l">消息ID</div><div class="v mono">{{m.id}}</div></div>
<div class="i"><div class="l">wxId</div><div class="v">{{m.wxid}}</div></div>
<div class="i"><div class="l">已读数</div><div class="v"><span class="cnt {%if m.cnt==0%}z{%endif%}">{{m.cnt}} 人</span></div></div>
<div class="i"><div class="l">注册时间</div><div class="v">{{m.t}}</div></div>
</div>
<div class="l">消息内容</div><div class="cb">{{m.content}}</div>
<button class="btn-d" onclick="del()">🗑 删除本条</button></div>
<div class="card"><h2>👁 已读记录 ({{reads|length}})</h2>
{%if reads%}
<table><thead><tr><th>IP</th><th>📍 位置</th><th>User-Agent</th><th>读取时间</th></tr></thead><tbody>
{%for r in reads%}
<tr><td class="ip">{{r.ip_address}}</td><td>{{r.geo}}</td><td class="ua" title="{{r.user_agent}}">{{r.user_agent or "-"}}</td><td>{{r.t}}</td></tr>
{%endfor%}
</tbody></table>{%else%}<div class="emp">📭 暂无读取记录</div>{%endif%}</div>
</div>
<script>
async function del(){if(!confirm("删除这条?"))return;await fetch("/api/delete/{{m.id}}",{method:"POST"});location.href="/"}
</script></body></html>'''

def get_db():
    db = getattr(g, "_db", None)
    if db is None:
        db = g._db = sqlite3.connect(DATABASE, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA journal_mode=WAL")
        db.execute("PRAGMA busy_timeout=5000")
    return db

@app.teardown_appcontext
def close_db(e=None):
    db = getattr(g, "_db", None)
    if db is not None:
        db.close()

def init_db():
    db = sqlite3.connect(DATABASE)
    db.executescript("""
        CREATE TABLE IF NOT EXISTS messages (
            id TEXT PRIMARY KEY, wx_id TEXT NOT NULL, content TEXT DEFAULT '',
            create_time INTEGER NOT NULL,
            registered_at INTEGER DEFAULT (CAST(strftime('%s','now') AS INTEGER))
        );
        CREATE TABLE IF NOT EXISTS reads (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            msg_id TEXT NOT NULL, wx_id TEXT NOT NULL,
            ip_address TEXT, user_agent TEXT,
            country TEXT DEFAULT '', region TEXT DEFAULT '',
            city TEXT DEFAULT '', isp TEXT DEFAULT '',
            read_at INTEGER DEFAULT (CAST(strftime('%s','now') AS INTEGER)),
            UNIQUE(msg_id, ip_address)
        );
        CREATE INDEX IF NOT EXISTS idx_reads_msg ON reads(msg_id);
    """)
    # 兼容旧库：动态补列
    for col in ["country", "region", "city", "isp", "loc"]:
        try:
            db.execute(f"ALTER TABLE reads ADD COLUMN {col} TEXT DEFAULT ''")
        except Exception:
            pass
    db.commit()
    db.close()

def gen_id(wx, c, ct):
    m = hashlib.sha256()
    m.update(wx.encode()); m.update(b"\x00")
    m.update(c.encode()); m.update(b"\x00")
    m.update(str(ct).encode())
    return m.hexdigest()

def get_ip():
    xff = request.headers.get("X-Forwarded-For")
    if xff: return xff.split(",")[0].strip()
    xri = request.headers.get("X-Real-IP")
    if xri: return xri.strip()
    return request.remote_addr or "0.0.0.0"

def lookup_geo(ip):
    # IP 定位开关: 环境变量 ENABLE_GEO=0 关闭 (Lite 模式, 零外部请求)
    if os.environ.get("ENABLE_GEO", "1").lower() in ("0", "off", "false", "no"):
        return None
    # IP 定位：中文优先 (ip-api.com lang=zh-CN)，失败回退 ipwho.is / ipinfo.io
    if ip in ("0.0.0.0", "127.0.0.1", "::1", "") or not ip:
        return None
    import urllib.request, json as _json

    # 接口 1: ip-api.com 中文 (支持 IPv6，返回 中国/上海市/上海)
    try:
        req = urllib.request.Request(
            f"http://ip-api.com/json/{ip}?lang=zh-CN&fields=status,message,country,regionName,city,isp,lat,lon",
            headers={"User-Agent": "rrt/2.1"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            d = _json.load(resp)
        if d.get("status") == "success":
            return {
                "country": d.get("country", ""),
                "region": d.get("regionName", ""),
                "city": d.get("city", ""),
                "isp": d.get("isp", ""),
                "org": d.get("isp", ""),
                "loc": f"{d.get('lat','')},{d.get('lon','')}" if d.get("lat") is not None else "",
            }
    except Exception:
        pass

    # 接口 2: ipwho.is 中文 (支持 IPv6)
    try:
        req = urllib.request.Request(
            f"https://ipwho.is/{ip}?lang=zh-CN",
            headers={"User-Agent": "rrt/2.1"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            d = _json.load(resp)
        if d.get("success", False):
            return {
                "country": d.get("country", ""),
                "region": d.get("region", ""),
                "city": d.get("city", ""),
                "isp": (d.get("connection") or {}).get("isp", ""),
                "org": (d.get("connection") or {}).get("isp", ""),
                "loc": f"{d.get('latitude','')},{d.get('longitude','')}" if d.get("latitude") is not None else "",
            }
    except Exception:
        pass

    # 接口 3: ipinfo.io (英文，仅兜底)
    try:
        req = urllib.request.Request(
            f"https://ipinfo.io/{ip}/json",
            headers={"User-Agent": "curl/7.81.0"})
        with urllib.request.urlopen(req, timeout=6) as resp:
            d = _json.load(resp)
        if d.get("country"):
            return {
                "country": d.get("country", ""),
                "region": d.get("region", ""),
                "city": d.get("city", ""),
                "isp": (d.get("org", "") or "").split(" ", 1)[-1] if d.get("org") else "",
                "org": d.get("org", ""),
                "loc": d.get("loc", ""),
            }
    except Exception:
        pass
    return None

@app.route("/health")
def health():
    return jsonify({"status": "ok"})

@app.route("/register", methods=["POST"])
def register():
    try:
        d = request.get_json(force=True)
    except Exception:
        return jsonify({"error": "Invalid JSON"}), 400
    wx = (d.get("wxId", "") or "").strip()
    c = d.get("content", "") or ""
    ct = d.get("createTime", int(time.time() * 1000))
    if not wx:
        return jsonify({"error": "wxId required"}), 400
    mid = gen_id(wx, c, ct)
    db = get_db()
    try:
        db.execute("INSERT OR IGNORE INTO messages(id,wx_id,content,create_time) VALUES(?,?,?,?)",
                   (mid, wx, c, ct))
        db.commit()
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    pu = f"{request.host_url.rstrip('/')}/pixel?wxId={wx}&id={mid}"
    return jsonify({"success": True, "id": mid, "wxId": wx, "pixel_url": pu})

@app.route("/pixel")
def pixel():
    wx = request.args.get("wxId", "")
    mid = request.args.get("id", "")
    if not wx or not mid:
        return send_file(BytesIO(TRANSPARENT_GIF), mimetype="image/gif")
    ip = get_ip()
    ua = (request.headers.get("User-Agent", "") or "")[:500]
    geo = lookup_geo(ip)
    country = geo["country"] if geo else ""
    region = geo["region"] if geo else ""
    city = geo["city"] if geo else ""
    isp = geo["isp"] if geo else ""
    db = get_db()
    try:
        db.execute("INSERT OR IGNORE INTO reads(msg_id,wx_id,ip_address,user_agent,country,region,city,isp) VALUES(?,?,?,?,?,?,?,?)",
                   (mid, wx, ip, ua, country, region, city, isp))
        db.commit()
    except Exception:
        pass
    return send_file(BytesIO(TRANSPARENT_GIF), mimetype="image/gif")

@app.route("/count")
def count():
    wx = request.args.get("wxId", "")
    mid = request.args.get("id", "")
    if not wx or not mid:
        return jsonify({"count": 0})
    db = get_db()
    r = db.execute("SELECT COUNT(DISTINCT ip_address) c FROM reads WHERE msg_id=? AND wx_id=?",
                   (mid, wx)).fetchone()
    rows = db.execute(
        "SELECT * FROM reads WHERE msg_id=? AND wx_id=? ORDER BY read_at DESC",
        (mid, wx)).fetchall()
    return jsonify({
        "count": r["c"] if r else 0,
        "msg_id": mid,
        "reads": [{
            "ip_address": x["ip_address"],
            "location": " ".join([y for y in [x["country"], x["region"], x["city"]] if y]) or "-",
            "province": x["region"],
            "city": x["city"],
            "country": x["country"],
            "isp": x["isp"] if "isp" in x.keys() else "",
            "loc": x["loc"] if "loc" in x.keys() else "",
            "user_agent": x["user_agent"],
            "read_at": datetime.fromtimestamp(x["read_at"]).strftime("%Y-%m-%d %H:%M:%S"),
        } for x in rows],
    })

@app.route("/")
def index():
    db = get_db()
    tm = db.execute("SELECT COUNT(*) c FROM messages").fetchone()["c"]
    tr = db.execute("SELECT COUNT(DISTINCT ip_address) c FROM reads").fetchone()["c"]
    ar = round(tr / tm, 1) if tm else 0
    rows = db.execute(
        "SELECT m.*, (SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id) cnt, "
        "(SELECT COUNT(DISTINCT city) FROM reads r WHERE r.msg_id=m.id AND r.city!='') loc_cnt, "
        "(SELECT GROUP_CONCAT(DISTINCT city) FROM reads r WHERE r.msg_id=m.id AND r.city!='') locs "
        "FROM messages m ORDER BY registered_at DESC LIMIT 100").fetchall()
    msgs = [{"id": r["id"], "wxid": r["wx_id"], "content": r["content"],
             "cnt": r["cnt"], "loc": r["loc_cnt"] or 0,
             "locs": (r["locs"] or "")[:60],
             "t": datetime.fromtimestamp(r["registered_at"]).strftime("%Y-%m-%d %H:%M:%S")}
            for r in rows]
    return render_template_string(INDEX_HTML, tm=tm, tr=tr, ar=ar, msgs=msgs)

@app.route("/message/<mid>")
def detail(mid):
    db = get_db()
    m = db.execute("SELECT * FROM messages WHERE id=?", (mid,)).fetchone()
    if not m:
        return "not found", 404
    rows = db.execute("SELECT * FROM reads WHERE msg_id=? ORDER BY read_at DESC", (mid,)).fetchall()
    reads = [{"ip_address": r["ip_address"], "user_agent": r["user_agent"],
              "geo": " ".join([x for x in [r["country"], r["region"], r["city"]] if x]) or "-",
              "t": datetime.fromtimestamp(r["read_at"]).strftime("%Y-%m-%d %H:%M:%S")}
             for r in rows]
    msg = {"id": m["id"], "wxid": m["wx_id"], "content": m["content"],
           "cnt": len(rows),
           "t": datetime.fromtimestamp(m["registered_at"]).strftime("%Y-%m-%d %H:%M:%S")}
    # 支持 JSON 返回 (?json=1 或 Accept: application/json)
    if request.args.get("json") == "1" or "application/json" in request.headers.get("Accept", ""):
        return jsonify({
            "message": msg,
            "reads": [{
                "ip_address": r["ip_address"],
                "user_agent": r["user_agent"],
                "country": (db.execute("SELECT country FROM reads WHERE id=?", (r["id"],)).fetchone() or {"country": ""})["country"],
                "location": r["geo"],
                "read_at": r["t"],
            } for r in rows],
        })
    return render_template_string(DETAIL_HTML, m=msg, reads=reads)

# JSON 详情接口: /api/reads/<mid> (客户端专用)
@app.route("/api/reads/<mid>")
def api_reads(mid):
    db = get_db()
    m = db.execute("SELECT * FROM messages WHERE id=?", (mid,)).fetchone()
    if not m:
        return jsonify({"error": "not found"}), 404
    rows = db.execute("SELECT * FROM reads WHERE msg_id=? ORDER BY read_at DESC", (mid,)).fetchall()
    return jsonify({
        "msg_id": mid,
        "wxId": m["wx_id"],
        "content": m["content"],
        "read_count": len(rows),
        "reads": [{
            "ip_address": r["ip_address"],
            "location": " ".join([x for x in [r["country"], r["region"], r["city"]] if x]) or "-",
            "country": r["country"],
            "region": r["region"],
            "city": r["city"],
            "isp": r["isp"] if "isp" in r.keys() else "",
            "loc": r["loc"] if "loc" in r.keys() else "",
            "user_agent": r["user_agent"],
            "read_at": datetime.fromtimestamp(r["read_at"]).strftime("%Y-%m-%d %H:%M:%S"),
        } for r in rows],
    })

# JSON 消息列表: /api/messages (客户端专用)
@app.route("/api/messages")
def api_messages():
    db = get_db()
    rows = db.execute(
        "SELECT m.*, (SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id) cnt "
        "FROM messages m ORDER BY registered_at DESC LIMIT 100").fetchall()
    return jsonify({"messages": [{
        "id": r["id"], "wxId": r["wx_id"], "content": r["content"],
        "read_count": r["cnt"],
        "registered_at": datetime.fromtimestamp(r["registered_at"]).strftime("%Y-%m-%d %H:%M:%S"),
    } for r in rows]})

@app.route("/api/delete/<mid>", methods=["POST"])
def del_msg(mid):
    db = get_db()
    db.execute("DELETE FROM reads WHERE msg_id=?", (mid,))
    db.execute("DELETE FROM messages WHERE id=?", (mid,))
    db.commit()
    return jsonify({"success": True})

@app.route("/api/delete-all", methods=["POST"])
def del_all():
    db = get_db()
    db.execute("DELETE FROM reads")
    db.execute("DELETE FROM messages")
    db.commit()
    return jsonify({"success": True})

@app.route("/batch-status")
def batch():
    ids_str = request.args.get("ids", "")
    if not ids_str:
        return jsonify({"error": "ids required"}), 400
    ids = [i.strip() for i in ids_str.split(",") if i.strip()]
    db = get_db()
    ph = ",".join("?" * len(ids))
    rows = db.execute(
        f"SELECT msg_id, COUNT(DISTINCT ip_address) c FROM reads WHERE msg_id IN({ph}) GROUP BY msg_id",
        ids).fetchall()
    rv = {r["msg_id"]: r["c"] for r in rows}
    for i in ids:
        if i not in rv: rv[i] = 0
    return jsonify({"statuses": rv})

if __name__ == "__main__":
    init_db()
    print("")
    print("=" * 50)
    print("  已读追踪服务已启动")
    print("  控制台: http://127.0.0.1:5000")
    print("=" * 50)
    print("")
    app.run(host="0.0.0.0", port=5000, debug=False, threaded=True)
PYEOF
log "代码写入完成: $HOME/rrt/app.py"

echo ""
echo "[4/4] 启动服务 (前台运行)..."
cd "$HOME/rrt"

pkill -f "python app\.py" 2>/dev/null || true

echo ""
echo "════════════════════════════════════════════"
echo "  🖥  控制台地址: http://127.0.0.1:5000"
echo "════════════════════════════════════════════"
echo ""
echo "▶ 服务前台运行中 (Ctrl+C 停止)"
echo "▶ 需要公网地址？另开一个 Termux 会话执行:"
echo "   cloudflared tunnel --protocol http2 --url http://127.0.0.1:5000"
echo ""

exec python app.py
