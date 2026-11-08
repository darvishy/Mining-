package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import android.util.Log

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "DatabaseHelper"
        private const val DATABASE_NAME = "nullhud_records.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_RECORDS = "records"
        const val COL_ID = "id"
        const val COL_NAT_CODE = "national_code"
        const val COL_CARD_NO = "card_no"
        const val COL_FULL_NAME = "full_name"
        const val COL_BIRTH_DATE = "birth_date"
        const val COL_MOBILE = "mobile"

        const val TABLE_SESSION = "import_session"

        @Volatile
        private var INSTANCE: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        try {
            db.enableWriteAheadLogging()
            db.execSQL("PRAGMA cache_size = -64000;") // 64MB cache
            db.execSQL("PRAGMA temp_store = MEMORY;")
            db.execSQL("PRAGMA synchronous = NORMAL;")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring database PRAGMAs", e)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RECORDS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAT_CODE TEXT NOT NULL,
                $COL_CARD_NO TEXT,
                $COL_FULL_NAME TEXT,
                $COL_BIRTH_DATE TEXT,
                $COL_MOBILE TEXT
            );
            """.trimIndent()
        )

        // Create high-performance B-Tree indexes for instant search across millions of records
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_nat_code ON $TABLE_RECORDS ($COL_NAT_CODE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_card_no ON $TABLE_RECORDS ($COL_CARD_NO);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mobile ON $TABLE_RECORDS ($COL_MOBILE);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_full_name ON $TABLE_RECORDS ($COL_FULL_NAME);")

        // Create session table for pause/resume and crash recovery
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SESSION (
                session_id INTEGER PRIMARY KEY DEFAULT 1,
                source_name TEXT,
                source_uri TEXT,
                source_type TEXT,
                processed_records INTEGER,
                byte_offset INTEGER,
                total_records INTEGER,
                status TEXT,
                elapsed_ms INTEGER,
                updated_at INTEGER
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_RECORDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSION")
        onCreate(db)
    }

    fun optimizeForBulkInsert(db: SQLiteDatabase) {
        try {
            db.execSQL("PRAGMA synchronous = OFF;")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set synchronous = OFF", e)
        }
    }

    fun restoreStandardConfig(db: SQLiteDatabase) {
        try {
            db.execSQL("PRAGMA synchronous = NORMAL;")
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore synchronous = NORMAL", e)
        }
    }

    fun createInsertStatement(db: SQLiteDatabase): SQLiteStatement {
        val sql = "INSERT INTO $TABLE_RECORDS ($COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE) VALUES (?, ?, ?, ?, ?)"
        return db.compileStatement(sql)
    }

    fun getTotalCount(): Long {
        val db = readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM $TABLE_RECORDS", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    fun clearAllRecords() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM $TABLE_RECORDS")
            db.execSQL("DELETE FROM $TABLE_SESSION")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Executes ultra-fast search leveraging B-Tree indexes.
     */
    fun searchRecords(
        query: String,
        field: SearchField,
        limit: Int = 100,
        offset: Int = 0
    ): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return SearchResult(emptyList(), 0.0, 0, "NONE")
        }

        val db = readableDatabase
        val startNano = System.nanoTime()
        val records = mutableListOf<PersonRecord>()
        var indexUsed = "B-TREE INDEX"

        val (sql, selectionArgs) = when (field) {
            SearchField.NATIONAL_CODE -> {
                indexUsed = "idx_nat_code"
                "SELECT $COL_ID, $COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE " +
                        "FROM $TABLE_RECORDS WHERE $COL_NAT_CODE LIKE ? || '%' LIMIT ? OFFSET ?" to arrayOf(trimmed, limit.toString(), offset.toString())
            }
            SearchField.CARD_NO -> {
                indexUsed = "idx_card_no"
                "SELECT $COL_ID, $COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE " +
                        "FROM $TABLE_RECORDS WHERE $COL_CARD_NO LIKE ? || '%' LIMIT ? OFFSET ?" to arrayOf(trimmed, limit.toString(), offset.toString())
            }
            SearchField.MOBILE -> {
                indexUsed = "idx_mobile"
                "SELECT $COL_ID, $COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE " +
                        "FROM $TABLE_RECORDS WHERE $COL_MOBILE LIKE ? || '%' LIMIT ? OFFSET ?" to arrayOf(trimmed, limit.toString(), offset.toString())
            }
            SearchField.FULL_NAME -> {
                indexUsed = "idx_full_name"
                "SELECT $COL_ID, $COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE " +
                        "FROM $TABLE_RECORDS WHERE $COL_FULL_NAME LIKE ? || '%' LIMIT ? OFFSET ?" to arrayOf(trimmed, limit.toString(), offset.toString())
            }
            SearchField.ALL -> {
                val cleanDigits = trimmed.replace("-", "").replace(" ", "")
                val isAllDigits = cleanDigits.all { it.isDigit() }

                when {
                    isAllDigits && cleanDigits.length == 16 -> {
                        indexUsed = "idx_card_no"
                        "SELECT $COL_ID, $COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE " +
                                "FROM $TABLE_RECORDS WHERE $COL_CARD_NO LIKE ? || '%' LIMIT ? OFFSET ?" to arrayOf(cleanDigits, limit.toString(), offset.toString())
                    }
                    isAllDigits && (cleanDigits.length in 10..11) && cleanDigits.startsWith("09") -> {
                        indexUsed = "idx_mobile"
                        "SELECT $COL_ID, $COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE " +
                                "FROM $TABLE_RECORDS WHERE $COL_MOBILE LIKE ? || '%' LIMIT ? OFFSET ?" to arrayOf(cleanDigits, limit.toString(), offset.toString())
                    }
                    isAllDigits && cleanDigits.length == 10 -> {
                        indexUsed = "idx_nat_code"
                        "SELECT $COL_ID, $COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE " +
                                "FROM $TABLE_RECORDS WHERE $COL_NAT_CODE LIKE ? || '%' LIMIT ? OFFSET ?" to arrayOf(cleanDigits, limit.toString(), offset.toString())
                    }
                    else -> {
                        indexUsed = "MULTI-INDEX"
                        "SELECT $COL_ID, $COL_NAT_CODE, $COL_CARD_NO, $COL_FULL_NAME, $COL_BIRTH_DATE, $COL_MOBILE " +
                                "FROM $TABLE_RECORDS WHERE $COL_NAT_CODE LIKE ? || '%' OR $COL_FULL_NAME LIKE ? || '%' OR $COL_CARD_NO LIKE ? || '%' OR $COL_MOBILE LIKE ? || '%' " +
                                "LIMIT ? OFFSET ?" to arrayOf(trimmed, trimmed, trimmed, trimmed, limit.toString(), offset.toString())
                    }
                }
            }
        }

        db.rawQuery(sql, selectionArgs).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COL_ID)
            val natIdx = cursor.getColumnIndexOrThrow(COL_NAT_CODE)
            val cardIdx = cursor.getColumnIndexOrThrow(COL_CARD_NO)
            val nameIdx = cursor.getColumnIndexOrThrow(COL_FULL_NAME)
            val birthIdx = cursor.getColumnIndexOrThrow(COL_BIRTH_DATE)
            val mobIdx = cursor.getColumnIndexOrThrow(COL_MOBILE)

            while (cursor.moveToNext()) {
                records.add(
                    PersonRecord(
                        id = cursor.getLong(idIdx),
                        nationalCode = cursor.getString(natIdx) ?: "",
                        cardNo = cursor.getString(cardIdx) ?: "",
                        fullName = cursor.getString(nameIdx) ?: "",
                        birthDate = cursor.getString(birthIdx) ?: "",
                        mobile = cursor.getString(mobIdx) ?: ""
                    )
                )
            }
        }

        val elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0

        return SearchResult(
            records = records,
            queryDurationMs = elapsedMs,
            totalMatches = records.size,
            indexUsed = indexUsed
        )
    }

    // Persist session state for pause/resume and crash recovery
    fun saveSession(
        sourceName: String,
        sourceUri: String?,
        sourceType: String,
        processedRecords: Long,
        byteOffset: Long,
        totalRecords: Long,
        status: String,
        elapsedMs: Long
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("session_id", 1)
            put("source_name", sourceName)
            put("source_uri", sourceUri ?: "")
            put("source_type", sourceType)
            put("processed_records", processedRecords)
            put("byte_offset", byteOffset)
            put("total_records", totalRecords)
            put("status", status)
            put("elapsed_ms", elapsedMs)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_SESSION, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getActiveSession(): SessionData? {
        val db = readableDatabase
        db.rawQuery("SELECT source_name, source_uri, source_type, processed_records, byte_offset, total_records, status, elapsed_ms FROM $TABLE_SESSION WHERE session_id = 1", null).use { cursor ->
            if (cursor.moveToFirst()) {
                return SessionData(
                    sourceName = cursor.getString(0) ?: "",
                    sourceUri = cursor.getString(1).takeIf { !it.isNullOrEmpty() },
                    sourceType = cursor.getString(2) ?: "FILE",
                    processedRecords = cursor.getLong(3),
                    byteOffset = cursor.getLong(4),
                    totalRecords = cursor.getLong(5),
                    status = cursor.getString(6) ?: "IDLE",
                    elapsedMs = cursor.getLong(7)
                )
            }
        }
        return null
    }

    fun clearSession() {
        writableDatabase.execSQL("DELETE FROM $TABLE_SESSION")
    }

    data class SessionData(
        val sourceName: String,
        val sourceUri: String?,
        val sourceType: String,
        val processedRecords: Long,
        val byteOffset: Long,
        val totalRecords: Long,
        val status: String,
        val elapsedMs: Long
    )
}
