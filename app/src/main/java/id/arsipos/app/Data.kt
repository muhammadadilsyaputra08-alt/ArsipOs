package id.arsipos.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import java.util.Calendar

val TYPES = listOf("Surat Masuk", "Surat Keluar", "SK", "Proposal", "LPJ", "Nota", "Lainnya")
val STATUSES = listOf("Baru", "Diproses", "Selesai")
val MONTHS = listOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

data class Doc(
    val id: Long = 0,
    val fileName: String,
    val type: String,
    val number: String,
    val date: String,
    val subject: String,
    val institution: String,
    val status: String,
    val fileId: String,
    val size: Long,
    val mime: String,
    val createdAt: Long = System.currentTimeMillis(),
    val year: Int,
    val month: Int,
    val content: String = ""
)

data class Draft(
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    val type: String,
    val number: String,
    val date: String,
    val subject: String,
    val institution: String,
    val status: String,
    val content: String
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("arsipos", Context.MODE_PRIVATE)
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v.trim()).apply()
    var chatId: String
        get() = sp.getString("chat_id", "") ?: ""
        set(v) = sp.edit().putString("chat_id", v.trim()).apply()
    val ready: Boolean get() = token.isNotBlank() && chatId.isNotBlank()
}

class ArchiveDb(ctx: Context) : SQLiteOpenHelper(ctx, "arsip.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE docs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_name TEXT, doc_type TEXT, doc_number TEXT, doc_date TEXT,
                subject TEXT, institution TEXT, status TEXT, file_id TEXT,
                file_size INTEGER, mime TEXT, created_at INTEGER,
                year INTEGER, month INTEGER, content TEXT)"""
        )
        db.execSQL(
            "CREATE VIRTUAL TABLE docs_fts USING fts4(file_name, doc_type, doc_number, subject, institution, content)"
        )
        db.execSQL("CREATE INDEX idx_docs_type ON docs(doc_type)")
        db.execSQL("CREATE INDEX idx_docs_ym ON docs(year, month)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun insert(d: Doc): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("file_name", d.fileName); put("doc_type", d.type); put("doc_number", d.number)
                put("doc_date", d.date); put("subject", d.subject); put("institution", d.institution)
                put("status", d.status); put("file_id", d.fileId); put("file_size", d.size)
                put("mime", d.mime); put("created_at", d.createdAt)
                put("year", d.year); put("month", d.month); put("content", d.content)
            }
            val id = db.insertOrThrow("docs", null, cv)
            val fts = ContentValues().apply {
                put("docid", id)
                put("file_name", d.fileName); put("doc_type", d.type); put("doc_number", d.number)
                put("subject", d.subject); put("institution", d.institution); put("content", d.content)
            }
            db.insertOrThrow("docs_fts", null, fts)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("docs", "id=?", arrayOf(id.toString()))
            db.delete("docs_fts", "docid=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun search(q: String, type: String?, year: Int?, month: Int?, status: String?): List<Doc> {
        val args = mutableListOf<String>()
        val where = mutableListOf<String>()
        var sql = "SELECT d.* FROM docs d"
        val tokens = q.split(Regex("\\s+"))
            .map { t -> t.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) {
            sql += " JOIN docs_fts f ON f.docid = d.id"
            where += "docs_fts MATCH ?"
            args += tokens.joinToString(" ") { "$it*" }
        }
        if (type != null) { where += "d.doc_type = ?"; args += type }
        if (year != null) { where += "d.year = ?"; args += year.toString() }
        if (month != null) { where += "d.month = ?"; args += month.toString() }
        if (status != null) { where += "d.status = ?"; args += status }
        if (where.isNotEmpty()) sql += " WHERE " + where.joinToString(" AND ")
        sql += " ORDER BY d.created_at DESC LIMIT 500"
        val out = mutableListOf<Doc>()
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) out += c.toDoc()
        }
        return out
    }

    fun counts(): Map<String, Int> {
        val m = linkedMapOf<String, Int>()
        readableDatabase.rawQuery("SELECT doc_type, COUNT(*) FROM docs GROUP BY doc_type", null).use { c ->
            while (c.moveToNext()) m[c.getString(0)] = c.getInt(1)
        }
        return m
    }

    fun years(): List<Int> {
        val l = mutableListOf<Int>()
        readableDatabase.rawQuery("SELECT DISTINCT year FROM docs ORDER BY year DESC", null).use { c ->
            while (c.moveToNext()) l += c.getInt(0)
        }
        return l
    }

    private fun Cursor.toDoc() = Doc(
        id = getLong(getColumnIndexOrThrow("id")),
        fileName = getString(getColumnIndexOrThrow("file_name")) ?: "",
        type = getString(getColumnIndexOrThrow("doc_type")) ?: "",
        number = getString(getColumnIndexOrThrow("doc_number")) ?: "",
        date = getString(getColumnIndexOrThrow("doc_date")) ?: "",
        subject = getString(getColumnIndexOrThrow("subject")) ?: "",
        institution = getString(getColumnIndexOrThrow("institution")) ?: "",
        status = getString(getColumnIndexOrThrow("status")) ?: "",
        fileId = getString(getColumnIndexOrThrow("file_id")) ?: "",
        size = getLong(getColumnIndexOrThrow("file_size")),
        mime = getString(getColumnIndexOrThrow("mime")) ?: "",
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        year = getInt(getColumnIndexOrThrow("year")),
        month = getInt(getColumnIndexOrThrow("month"))
    )
}

fun yearMonthOf(date: String): Pair<Int, Int> {
    val m = Regex("^(\\d{4})-(\\d{2})").find(date)
    if (m != null) {
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        if (mo in 1..12) return y to mo
    }
    val c = Calendar.getInstance()
    return c.get(Calendar.YEAR) to (c.get(Calendar.MONTH) + 1)
}
