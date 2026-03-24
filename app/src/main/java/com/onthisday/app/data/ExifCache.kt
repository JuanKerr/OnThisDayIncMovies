package com.onthisday.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local SQLite cache mapping MediaStore image ID → EXIF capture date (ms).
 * A row with date_ms = -1 means "we tried but found no usable EXIF date".
 * DATE_ADDED (seconds) is stored so we can detect new photos added since the
 * last scan without re-reading every file.
 */
class ExifCache(context: Context) {

    companion object {
        private const val DB_NAME    = "exif_cache.db"
        private const val DB_VERSION = 1
        private const val TABLE      = "exif"
        private const val COL_ID     = "media_id"       // MediaStore _ID
        private const val COL_DATE   = "date_ms"        // EXIF date in ms, or -1
        private const val COL_ADDED  = "date_added"     // MediaStore DATE_ADDED (seconds)
    }

    private val helper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE $TABLE (
                    $COL_ID    INTEGER PRIMARY KEY,
                    $COL_DATE  INTEGER NOT NULL,
                    $COL_ADDED INTEGER NOT NULL
                )
            """.trimIndent())
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(db)
        }
    }

    /** Returns a map of media_id → date_ms for all cached rows. */
    fun loadAll(): Map<Long, Long> {
        val map = HashMap<Long, Long>()
        helper.readableDatabase.query(TABLE, arrayOf(COL_ID, COL_DATE),
            null, null, null, null, null).use { c ->
            while (c.moveToNext()) map[c.getLong(0)] = c.getLong(1)
        }
        return map
    }

    /** Returns the cached date_added values so we can detect new/changed files. */
    fun loadDateAdded(): Map<Long, Long> {
        val map = HashMap<Long, Long>()
        helper.readableDatabase.query(TABLE, arrayOf(COL_ID, COL_ADDED),
            null, null, null, null, null).use { c ->
            while (c.moveToNext()) map[c.getLong(0)] = c.getLong(1)
        }
        return map
    }

    /** Write a batch of entries inside a single transaction. */
    fun putAll(entries: List<Triple<Long, Long, Long>>) {
        // entries: (media_id, date_ms, date_added)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues(3)
            for ((id, date, added) in entries) {
                cv.put(COL_ID,    id)
                cv.put(COL_DATE,  date)
                cv.put(COL_ADDED, added)
                db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Remove rows for IDs that no longer exist in MediaStore. */
    fun deleteIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = helper.writableDatabase
        // SQLite IN clause limit is 999; chunk if needed
        ids.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            db.execSQL(
                "DELETE FROM $TABLE WHERE $COL_ID IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray()
            )
        }
    }
}
