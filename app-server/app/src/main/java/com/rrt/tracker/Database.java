package com.rrt.tracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 数据库封装（Android 原生 SQLite）
 */
public class Database extends SQLiteOpenHelper {

    public Database(Context context) {
        super(context, "receipts.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS messages (" +
            "id TEXT PRIMARY KEY, wx_id TEXT NOT NULL, content TEXT DEFAULT '', " +
            "create_time INTEGER NOT NULL, " +
            "registered_at INTEGER DEFAULT (strftime('%s','now')))");
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reads (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, msg_id TEXT NOT NULL, wx_id TEXT NOT NULL, " +
            "ip_address TEXT, user_agent TEXT, country TEXT DEFAULT '', region TEXT DEFAULT '', " +
            "city TEXT DEFAULT '', isp TEXT DEFAULT '', loc TEXT DEFAULT '', " +
            "reader_wx_id TEXT DEFAULT '', " +
            "read_at INTEGER DEFAULT (strftime('%s','now')), UNIQUE(msg_id, ip_address))");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reads_msg ON reads(msg_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msgs_wx ON messages(wx_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        String[] cols = {"country", "region", "city", "isp", "loc", "reader_wx_id"};
        for (String col : cols) {
            try { db.execSQL("ALTER TABLE reads ADD COLUMN " + col + " TEXT DEFAULT ''"); }
            catch (Exception ignored) {}
        }
    }

    public void registerMessage(String id, String wxId, String content, long createTime) {
        ContentValues cv = new ContentValues();
        cv.put("id", id);
        cv.put("wx_id", wxId);
        cv.put("content", content);
        cv.put("create_time", createTime);
        getWritableDatabase().insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public Cursor getMessage(String id) {
        Cursor c = getReadableDatabase().query("messages", null, "id=?",
                new String[]{id}, null, null, null);
        if (c.moveToFirst()) return c;
        c.close();
        return null;
    }

    public Cursor messageList(int limit) {
        return getReadableDatabase().rawQuery(
            "SELECT m.*, (SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id) cnt " +
            "FROM messages m ORDER BY registered_at DESC LIMIT " + limit, null);
    }

    /** 消息列表正序（先发的在前） */
    public Cursor messageListAsc(int limit) {
        return getReadableDatabase().rawQuery(
            "SELECT m.*, (SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id) cnt " +
            "FROM messages m ORDER BY registered_at ASC LIMIT " + limit, null);
    }

    public long[] stats() {
        long tm = 0, tr = 0;
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages", null);
        if (c.moveToFirst()) tm = c.getLong(0);
        c.close();
        c = getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT ip_address) FROM reads", null);
        if (c.moveToFirst()) tr = c.getLong(0);
        c.close();
        return new long[]{tm, tr};
    }

    public void recordRead(String msgId, String wxId, String ip, String ua,
                           String country, String region, String city,
                           String isp, String loc, String readerWxId) {
        ContentValues cv = new ContentValues();
        cv.put("msg_id", msgId);
        cv.put("wx_id", wxId);
        cv.put("ip_address", ip);
        cv.put("user_agent", ua != null && ua.length() > 500 ? ua.substring(0, 500) : ua);
        cv.put("country", country);
        cv.put("region", region);
        cv.put("city", city);
        cv.put("isp", isp);
        cv.put("loc", loc);
        cv.put("reader_wx_id", readerWxId);
        getWritableDatabase().insertWithOnConflict("reads", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public Cursor readList(String msgId) {
        return getReadableDatabase().rawQuery(
            "SELECT * FROM reads WHERE msg_id=? ORDER BY read_at DESC, id DESC",
            new String[]{msgId});
    }

    public int readCount(String msgId, String wxId) {
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT COUNT(DISTINCT ip_address) FROM reads WHERE msg_id=? AND wx_id=?",
            new String[]{msgId, wxId});
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public void deleteMessage(String id) {
        getWritableDatabase().delete("reads", "msg_id=?", new String[]{id});
        getWritableDatabase().delete("messages", "id=?", new String[]{id});
    }

    public void deleteAll() {
        getWritableDatabase().delete("reads", null, null);
        getWritableDatabase().delete("messages", null, null);
    }
}
