package dev.ujhhgtg.wekit.readreceipts

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 数据库封装（Android 原生 SQLite）
 */
class Database(context: Context) : SQLiteOpenHelper(context, "receipts.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS messages (" +
            "id TEXT PRIMARY KEY, wx_id TEXT NOT NULL, content TEXT DEFAULT '', " +
            "create_time INTEGER NOT NULL, " +
            "registered_at INTEGER DEFAULT (strftime('%s','now')))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reads (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, msg_id TEXT NOT NULL, wx_id TEXT NOT NULL, " +
            "ip_address TEXT, user_agent TEXT, country TEXT DEFAULT '', region TEXT DEFAULT '', " +
            "city TEXT DEFAULT '', isp TEXT DEFAULT '', loc TEXT DEFAULT '', " +
            "reader_wx_id TEXT DEFAULT '', " +
            "read_at INTEGER DEFAULT (strftime('%s','now')), UNIQUE(msg_id, ip_address))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_reads_msg ON reads(msg_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msgs_wx ON messages(wx_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val cols = arrayOf("country", "region", "city", "isp", "loc", "reader_wx_id")
        for (col in cols) {
            try { 
                db.execSQL("ALTER TABLE reads ADD COLUMN $col TEXT DEFAULT ''") 
            } catch (e: Exception) { 
                // Ignore column already exists
            }
        }
    }

    fun registerMessage(id: String, wxId: String, content: String, createTime: Long) {
        val cv = ContentValues().apply {
            put("id", id)
            put("wx_id", wxId)
            put("content", content)
            put("create_time", createTime)
        }
        writableDatabase.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun getMessage(id: String): Cursor? {
        val c = readableDatabase.query("messages", null, "id=?", arrayOf(id), null, null, null)
        return if (c.moveToFirst()) c else {
            c.close()
            null
        }
    }

    fun messageList(limit: Int): Cursor {
        return readableDatabase.rawQuery(
            "SELECT m.*, (SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id) cnt " +
            "FROM messages m ORDER BY registered_at DESC LIMIT $limit", null)
    }

    /** 消息列表正序（先发的在前） */
    fun messageListAsc(limit: Int): Cursor {
        return readableDatabase.rawQuery(
            "SELECT m.*, (SELECT COUNT(DISTINCT ip_address) FROM reads r WHERE r.msg_id=m.id) cnt " +
            "FROM messages m ORDER BY registered_at ASC LIMIT $limit", null)
    }

    fun stats(): LongArray {
        var tm = 0L
        var tr = 0L
        
        val c1 = readableDatabase.rawQuery("SELECT COUNT(*) FROM messages", null)
        if (c1.moveToFirst()) tm = c1.getLong(0)
        c1.close()
        
        val c2 = readableDatabase.rawQuery("SELECT COUNT(DISTINCT ip_address) FROM reads", null)
        if (c2.moveToFirst()) tr = c2.getLong(0)
        c2.close()
        
        return longArrayOf(tm, tr)
    }

    fun recordRead(msgId: String, wxId: String, ip: String, ua: String,
                   country: String, region: String, city: String,
                   isp: String, loc: String, readerWxId: String) {
        val cv = ContentValues().apply {
            put("msg_id", msgId)
            put("wx_id", wxId)
            put("ip_address", ip)
            put("user_agent", if (ua != null && ua.length > 500) ua.substring(0, 500) else ua)
            put("country", country)
            put("region", region)
            put("city", city)
            put("isp", isp)
            put("loc", loc)
            put("reader_wx_id", readerWxId)
        }
        writableDatabase.insertWithOnConflict("reads", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun readList(msgId: String): Cursor {
        return readableDatabase.rawQuery(
            "SELECT * FROM reads WHERE msg_id=? ORDER BY read_at DESC, id DESC",
            arrayOf(msgId))
    }

    fun readCount(msgId: String, wxId: String): Int {
        val c = readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT ip_address) FROM reads WHERE msg_id=? AND wx_id=?",
            arrayOf(msgId, wxId))
        return try {
            if (c.moveToFirst()) c.getInt(0) else 0
        } finally {
            c.close()
        }
    }

    fun deleteMessage(id: String) {
        writableDatabase.delete("reads", "msg_id=?", arrayOf(id))
        writableDatabase.delete("messages", "id=?", arrayOf(id))
    }

    fun deleteAll() {
        writableDatabase.delete("reads", null, null)
        writableDatabase.delete("messages", null, null)
    }
}