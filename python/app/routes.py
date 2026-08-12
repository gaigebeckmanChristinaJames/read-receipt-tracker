# read-receipt-tracker routes.py
import time
from datetime import datetime
from functools import wraps
from io import BytesIO
from flask import Flask, current_app, g, jsonify, render_template, request, send_file
from .database import get_db
from .utils import generate_message_id, get_client_ip, lookup_ip_location

TRANSPARENT_GIF = bytes([
    0x47,0x49,0x46,0x38,0x39,0x61,0x01,0x00,0x01,0x00,
    0x80,0x00,0x00,0x00,0x00,0x00,0xFF,0xFF,0xFF,0x21,
    0xF9,0x04,0x01,0x00,0x00,0x00,0x00,0x2C,0x00,0x00,
    0x00,0x00,0x01,0x00,0x01,0x00,0x00,0x02,0x02,0x44,
    0x01,0x00,0x3B,
])

def _ts2date(ts):
    try: return datetime.fromtimestamp(int(ts)).strftime("%Y-%m-%d %H:%M:%S")
    except: return str(ts)

def require_api_key(f):
    @wraps(f)
    def d(*a,**kw):
        ak=current_app.config.get("API_KEY","")
        if not ak: return f(*a,**kw)
        rk=request.headers.get("X-API-Key") or request.args.get("api_key","")
        if rk!=ak: return jsonify({"error":"Unauthorized"}),401
        return f(*a,**kw)
    return d

def rate_limit(f):
    @wraps(f)
    def d(*a,**kw):
        lim=current_app.config.get("RATE_LIMIT_PER_MINUTE",60)
        if lim<=0: return f(*a,**kw)
        ip=get_client_ip(); k=f"_rl_{f.__name__}_{ip}"; n=int(time.time())
        w=g.__dict__.get(k)
        if w is None: g.__dict__[k]={"start":n,"count":1}
        else:
            if n-w["start"]>60: w["start"]=n; w["count"]=1
            else:
                w["count"]+=1
                if w["count"]>lim: return jsonify({"error":"Too many requests"}),429
        return f(*a,**kw)
    return d

def register_routes(app):
    app.template_filter("ts2date")(_ts2date)

    @app.route("/health")
    @rate_limit
    def health(): return jsonify({"status":"ok","service":"read-receipt-tracker"})

    @app.route("/register",methods=["POST"])
    @rate_limit
    def register():
        try: data=request.get_json(force=True)
        except: return jsonify({"error":"Invalid JSON"}),400
        wx=(data.get("wxId","") or "").strip()
        c=data.get("content","") or ""
        ct=data.get("createTime",int(time.time()*1000))
        if not wx: return jsonify({"error":"wxId required"}),400
        if len(c)>50000: return jsonify({"error":"content too long"}),400
        mid=generate_message_id(wx,c,ct)
        db=get_db()
        try:
            db.execute("INSERT OR IGNORE INTO messages(id,wx_id,content,create_time) VALUES(?,?,?,?)",(mid,wx,c,ct))
            db.commit()
        except Exception as e: return jsonify({"error":str(e)}),500
        pu=f"{request.host_url.rstrip('/')}/pixel?wxId={wx}&id={mid}"
        return jsonify({"success":True,"id":mid,"wxId":wx,"pixel_url":pu})

    @app.route("/pixel")
    @rate_limit
    def pixel():
        wx=request.args.get("wxId",""); mid=request.args.get("id","")
        if not wx or not mid: return send_file(BytesIO(TRANSPARENT_GIF),mimetype="image/gif")
        ip=get_client_ip(); ua=(request.headers.get("User-Agent","") or "")[:500]
        geo=lookup_ip_location(ip)
        country=geo.get("country","") if geo else ""
        region=geo.get("region","") if geo else ""
        city=geo.get("city","") if geo else ""
        isp=geo.get("iso_code","") if geo else ""
        db=get_db()
        db.execute("INSERT OR IGNORE INTO reads(msg_id,wx_id,ip_address,user_agent,country,region,city,isp) VALUES(?,?,?,?,?,?,?,?)",(mid,wx,ip,ua,country,region,city,isp))
        db.commit()
        return send_file(BytesIO(TRANSPARENT_GIF),mimetype="image/gif")

    @app.route("/count")
    @rate_limit
    def count():
        wx=request.args.get("wxId",""); mid=request.args.get("id","")
        if not wx or not mid: return jsonify({"count":0,"error":"wxId and id required"})
        db=get_db()
        r=db.execute("SELECT COUNT(DISTINCT ip_address) cnt FROM reads WHERE msg_id=? AND wx_id=?",(mid,wx)).fetchone()
        return jsonify({"count":r["cnt"] if r else 0,"msg_id":mid})

    @app.route("/")
    @require_api_key
    @rate_limit
    def index():
        db=get_db()
        s=db.execute("SELECT (SELECT COUNT(*) FROM messages) tm,(SELECT COUNT(DISTINCT ip_address) FROM reads) tr,CASE WHEN (SELECT COUNT(*) FROM messages)=0 THEN 0.0 ELSE ROUND((SELECT COUNT(*) FROM reads)*1.0/(SELECT COUNT(*) FROM messages),1) END ar").fetchone()
        tm=s["tm"] or 0; tr=s["tr"] or 0; ar=float(s["ar"] or 0)
        ms=db.execute("SELECT m.*,(SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id) read_cnt FROM messages m ORDER BY registered_at DESC LIMIT 100").fetchall()
        return render_template("index.html",total_messages=tm,total_reads=tr,avg_reads=ar,messages=ms)

    @app.route("/message/<mid>")
    @require_api_key
    @rate_limit
    def detail(mid):
        db=get_db()
        m=db.execute("SELECT m.*,(SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id) read_cnt FROM messages m WHERE m.id=?",(mid,)).fetchone()
        if not m: return render_template("error.html",message="404 — 消息不存在"),404
        rs=db.execute("SELECT ip_address,user_agent,read_at,country,region,city,isp FROM reads WHERE msg_id=? ORDER BY read_at DESC",(mid,)).fetchall()
        hg=any(r["country"] or r["city"] for r in rs) if rs else False
        return render_template("detail.html",message=m,reads=rs,has_geo=hg)

    @app.route("/api/delete/<mid>",methods=["POST"])
    @require_api_key
    @rate_limit
    def del_msg(mid):
        db=get_db()
        db.execute("DELETE FROM reads WHERE msg_id=?",(mid,))
        db.execute("DELETE FROM messages WHERE id=?",(mid,))
        db.commit()
        return jsonify({"success":True})

    @app.route("/api/delete-all",methods=["POST"])
    @require_api_key
    @rate_limit
    def del_all():
        db=get_db()
        db.execute("DELETE FROM reads"); db.execute("DELETE FROM messages")
        db.commit()
        return jsonify({"success":True})

    @app.route("/batch-status")
    @require_api_key
    @rate_limit
    def batch_status():
        ids_str=request.args.get("ids","")
        if not ids_str: return jsonify({"error":"ids required"}),400
        ids=[i.strip() for i in ids_str.split(",") if i.strip()]
        if not ids: return jsonify({"error":"no valid ids"}),400
        db=get_db()
        ph=",".join("?"*len(ids))
        rows=db.execute(f"SELECT msg_id,COUNT(DISTINCT ip_address) cnt FROM reads WHERE msg_id IN({ph}) GROUP BY msg_id",ids).fetchall()
        r={r["msg_id"]:r["cnt"] for r in rows}
        for mid in ids:
            if mid not in r: r[mid]=0
        return jsonify({"statuses":r})
