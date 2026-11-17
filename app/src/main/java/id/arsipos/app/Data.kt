package id.arsipos.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import java.util.Calendar

const val KIND_FILE = "file"
const val KIND_NOTE = "note"
const val KIND_LINK = "link"
val KINDS = listOf(KIND_FILE to "Dokumen", KIND_NOTE to "Catatan", KIND_LINK to "Tautan")
fun kindLabel(k: String): String = KINDS.firstOrNull { it.first == k }?.second ?: k
fun kindIcon(k: String): String = when (k) {
    KIND_NOTE -> "📝"
    KIND_LINK -> "🔗"
    else -> "📄"
}

/** Jenis dokumen hasil deteksi otomatis (terpisah dari kategori buatan pengguna). */
val TYPES = listOf("Surat Masuk", "Surat Keluar", "SK", "Proposal", "LPJ", "Nota", "Lainnya")
val STATUSES = listOf("Baru", "Diproses", "Selesai")
val MONTHS = listOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
)

data class Category(val id: Long = 0, val name: String, val color: Int, val icon: String)

val DEFAULT_CATEGORIES = listOf(
    Category(name = "Dokumen Kantor", color = 0xFF1E3A8A.toInt(), icon = "🏢"),
    Category(name = "Arsip Pribadi", color = 0xFF047857.toInt(), icon = "🗂"),
    Category(name = "Catatan Teks", color = 0xFFB45309.toInt(), icon = "📝"),
    Category(name = "Link Penting", color = 0xFF7C3AED.toInt(), icon = "🔗")
)

data class Doc(
    val id: Long = 0,
    val kind: String = KIND_FILE,
    val fileName: String,
    val type: String,
    val number: String,
    val date: String,
    val subject: String,
    val institution: String,
    val status: String,
    val fileId: String,
    val messageId: Long = 0,
    val size: Long,
    val mime: String,
    val createdAt: Long = System.currentTimeMillis(),
    val year: Int,
    val month: Int,
    val content: String = "",
    val category: String = "",
    val tags: String = "",
    val linkUrl: String = ""
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
    val content: String,
    val category: String = "",
    val tags: String = ""
)

/** Item antrean impor (file dari aplikasi luar, sudah disalin ke storage internal). */
data class QueueItem(
    val id: Long = 0,
    val path: String,
    val origName: String,
    val name: String,
    val mime: String,
    val size: Long,
    val category: String = "",
    val tags: String = "",
    val edited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** Normalisasi tag: "Keuangan, 2026 #Penting" -> "#keuangan #2026 #penting". */
fun normTags(raw: String): String =
    raw.split(Regex("[\\s,]+"))
        .map { t -> t.trim().trimStart('#').lowercase().filter { it.isLetterOrDigit() || it == '_' } }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ") { "#$it" }

class ArchiveDb(ctx: Context) : SQLiteOpenHelper(ctx, "arsip.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE docs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_name TEXT, doc_type TEXT, doc_number TEXT, doc_date TEXT,
                subject TEXT, institution TEXT, status TEXT, file_id TEXT,
                file_size INTEGER, mime TEXT, created_at INTEGER,
                year INTEGER, month INTEGER, content TEXT,
                kind TEXT DEFAULT 'file', category TEXT DEFAULT '', tags TEXT DEFAULT '',
                message_id INTEGER DEFAULT 0, link_url TEXT DEFAULT '')"""
        )
        createAux(db)
    }

    private fun createAux(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS categories(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, color INTEGER, icon TEXT)"
        )
        db.execSQL(
            "CREATE VIRTUAL TABLE docs_fts USING fts4(file_name, doc_type, doc_number, subject, institution, body, tags, category, link_url)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_docs_kind ON docs(kind)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_docs_ym ON docs(year, month)")
        createQueue(db)
    }

    private fun createQueue(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS queue(
                id INTEGER PRIMARY KEY AUTOINCREMENT, path TEXT NOT NULL, orig_name TEXT, name TEXT,
                mime TEXT, size INTEGER, status TEXT DEFAULT 'PENDING', category TEXT DEFAULT '',
                tags TEXT DEFAULT '', edited INTEGER DEFAULT 0, created_at INTEGER)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE docs ADD COLUMN kind TEXT DEFAULT 'file'")
            db.execSQL("ALTER TABLE docs ADD COLUMN category TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE docs ADD COLUMN tags TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE docs ADD COLUMN message_id INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE docs ADD COLUMN link_url TEXT DEFAULT ''")
            db.execSQL("UPDATE docs SET kind='note' WHERE doc_type='Catatan'")
            db.execSQL("DROP TABLE IF EXISTS docs_fts")
            createAux(db)
            db.execSQL(
                "INSERT INTO docs_fts(docid, file_name, doc_type, doc_number, subject, institution, body, tags, category, link_url) " +
                    "SELECT id, file_name, doc_type, doc_number, subject, institution, content, tags, category, link_url FROM docs"
            )
        }
        if (oldVersion < 3) createQueue(db)
    }

    // ---------- Antrean impor ----------

    fun queueAdd(q: QueueItem): Long {
        val cv = ContentValues().apply {
            put("path", q.path); put("orig_name", q.origName); put("name", q.name); put("mime", q.mime)
            put("size", q.size); put("status", "PENDING"); put("category", q.category); put("tags", q.tags)
            put("edited", if (q.edited) 1 else 0); put("created_at", q.createdAt)
        }
        return writableDatabase.insertOrThrow("queue", null, cv)
    }

    fun queueAll(): List<QueueItem> {
        val out = mutableListOf<QueueItem>()
        readableDatabase.rawQuery(
            "SELECT id, path, orig_name, name, mime, size, category, tags, edited, created_at FROM queue WHERE status='PENDING' ORDER BY id", null
        ).use { c ->
            while (c.moveToNext()) {
                out += QueueItem(
                    c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "",
                    c.getString(4) ?: "", c.getLong(5), c.getString(6) ?: "", c.getString(7) ?: "",
                    c.getInt(8) == 1, c.getLong(9)
                )
            }
        }
        return out
    }

    fun queueUpdate(q: QueueItem) {
        val cv = ContentValues().apply {
            put("name", q.name); put("category", q.category); put("tags", q.tags); put("edited", if (q.edited) 1 else 0)
        }
        writableDatabase.update("queue", cv, "id=?", arrayOf(q.id.toString()))
    }

    fun queueDelete(id: Long) {
        writableDatabase.delete("queue", "id=?", arrayOf(id.toString()))
    }

    fun queueBulk(category: String?, tags: String?) {
        val cv = ContentValues()
        if (category != null) cv.put("category", category)
        if (tags != null) cv.put("tags", tags)
        if (cv.size() > 0) writableDatabase.update("queue", cv, "status='PENDING'", null)
    }

    // ---------- CRUD ----------

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
                put("kind", d.kind); put("category", d.category); put("tags", d.tags)
                put("message_id", d.messageId); put("link_url", d.linkUrl)
            }
            val id = db.insertOrThrow("docs", null, cv)
            val fts = ftsValues(d).apply { put("docid", id) }
            db.insertOrThrow("docs_fts", null, fts)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    private fun ftsValues(d: Doc) = ContentValues().apply {
        put("file_name", d.fileName); put("doc_type", d.type); put("doc_number", d.number)
        put("subject", d.subject); put("institution", d.institution); put("body", d.content)
        put("tags", d.tags); put("category", d.category); put("link_url", d.linkUrl)
    }

    fun update(d: Doc) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("file_name", d.fileName); put("doc_type", d.type); put("doc_number", d.number)
                put("doc_date", d.date); put("subject", d.subject); put("institution", d.institution)
                put("status", d.status); put("year", d.year); put("month", d.month)
                put("content", d.content); put("category", d.category); put("tags", d.tags)
                put("link_url", d.linkUrl)
            }
            db.update("docs", cv, "id=?", arrayOf(d.id.toString()))
            db.update("docs_fts", ftsValues(d), "docid=?", arrayOf(d.id.toString()))
            db.setTransactionSuccessful()
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

    fun search(
        q: String, kind: String?, category: String?, tag: String?,
        year: Int?, month: Int?, status: String?
    ): List<Doc> {
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
        if (kind != null) { where += "d.kind = ?"; args += kind }
        if (category != null) { where += "d.category = ?"; args += category }
        if (tag != null) { where += "instr(' ' || d.tags || ' ', ?) > 0"; args += " $tag " }
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

    fun exportAll(): List<Doc> {
        val out = mutableListOf<Doc>()
        readableDatabase.rawQuery("SELECT * FROM docs ORDER BY created_at", null).use { c ->
            while (c.moveToNext()) out += c.toDoc(withContent = true)
        }
        return out
    }

    fun getContent(id: Long): String =
        readableDatabase.rawQuery("SELECT content FROM docs WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) it.getString(0) ?: "" else ""
        }

    fun counts(): Map<String, Int> {
        val m = linkedMapOf<String, Int>()
        readableDatabase.rawQuery("SELECT kind, COUNT(*) FROM docs GROUP BY kind", null).use { c ->
            while (c.moveToNext()) m[c.getString(0) ?: KIND_FILE] = c.getInt(1)
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

    fun allTags(): List<String> {
        val freq = HashMap<String, Int>()
        readableDatabase.rawQuery("SELECT tags FROM docs WHERE tags IS NOT NULL AND tags != ''", null).use { c ->
            while (c.moveToNext()) {
                for (t in (c.getString(0) ?: "").split(' ')) if (t.startsWith("#")) freq[t] = (freq[t] ?: 0) + 1
            }
        }
        return freq.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
    }

    // ---------- Kategori ----------

    fun categories(): List<Category> {
        val out = mutableListOf<Category>()
        readableDatabase.rawQuery("SELECT id, name, color, icon FROM categories ORDER BY id", null).use { c ->
            while (c.moveToNext()) out += Category(c.getLong(0), c.getString(1) ?: "", c.getInt(2), c.getString(3) ?: "")
        }
        return out
    }

    fun addCategory(c: Category): Long = writableDatabase.insertOrThrow("categories", null, catValues(c))

    fun addCategoriesIgnore(list: List<Category>) {
        val db = writableDatabase
        for (c in list) db.insertWithOnConflict("categories", null, catValues(c), SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun catValues(c: Category) = ContentValues().apply {
        put("name", c.name); put("color", c.color); put("icon", c.icon)
    }

    fun updateCategory(c: Category) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val oldName = db.rawQuery("SELECT name FROM categories WHERE id=?", arrayOf(c.id.toString())).use {
                if (it.moveToFirst()) it.getString(0) ?: "" else ""
            }
            db.update("categories", catValues(c), "id=?", arrayOf(c.id.toString()))
            if (oldName.isNotEmpty() && oldName != c.name) {
                db.execSQL("UPDATE docs SET category=? WHERE category=?", arrayOf<Any?>(c.name, oldName))
                db.execSQL(
                    "UPDATE docs_fts SET category=? WHERE docid IN (SELECT id FROM docs WHERE category=?)",
                    arrayOf<Any?>(c.name, c.name)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteCategory(c: Category) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE docs_fts SET category='' WHERE docid IN (SELECT id FROM docs WHERE category=?)",
                arrayOf<Any?>(c.name)
            )
            db.execSQL("UPDATE docs SET category='' WHERE category=?", arrayOf<Any?>(c.name))
            db.delete("categories", "id=?", arrayOf(c.id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---------- Impor ----------

    private fun exists(d: Doc): Boolean {
        val (sql, args) = when {
            d.fileId.isNotBlank() -> "SELECT 1 FROM docs WHERE file_id=? LIMIT 1" to arrayOf(d.fileId)
            d.messageId > 0 -> "SELECT 1 FROM docs WHERE message_id=? LIMIT 1" to arrayOf(d.messageId.toString())
            else -> "SELECT 1 FROM docs WHERE created_at=? AND subject=? LIMIT 1" to arrayOf(d.createdAt.toString(), d.subject)
        }
        return readableDatabase.rawQuery(sql, args).use { it.moveToFirst() }
    }

    /** Mengembalikan (ditambah, dilewati). Atomik: gagal di tengah = tidak ada perubahan. */
    fun importAll(list: List<Doc>, cats: List<Category>, replace: Boolean): Pair<Int, Int> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (replace) {
                db.delete("docs", null, null)
                db.delete("docs_fts", null, null)
                db.delete("categories", null, null)
            }
            addCategoriesIgnore(cats)
            var added = 0
            var skipped = 0
            for (d in list) {
                if (!replace && exists(d)) skipped++ else { insert(d); added++ }
            }
            db.setTransactionSuccessful()
            return added to skipped
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.toDoc(withContent: Boolean = false) = Doc(
        id = getLong(getColumnIndexOrThrow("id")),
        kind = getString(getColumnIndexOrThrow("kind")) ?: KIND_FILE,
        fileName = getString(getColumnIndexOrThrow("file_name")) ?: "",
        type = getString(getColumnIndexOrThrow("doc_type")) ?: "",
        number = getString(getColumnIndexOrThrow("doc_number")) ?: "",
        date = getString(getColumnIndexOrThrow("doc_date")) ?: "",
        subject = getString(getColumnIndexOrThrow("subject")) ?: "",
        institution = getString(getColumnIndexOrThrow("institution")) ?: "",
        status = getString(getColumnIndexOrThrow("status")) ?: "",
        fileId = getString(getColumnIndexOrThrow("file_id")) ?: "",
        messageId = getLong(getColumnIndexOrThrow("message_id")),
        size = getLong(getColumnIndexOrThrow("file_size")),
        mime = getString(getColumnIndexOrThrow("mime")) ?: "",
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        year = getInt(getColumnIndexOrThrow("year")),
        month = getInt(getColumnIndexOrThrow("month")),
        content = if (withContent) getString(getColumnIndexOrThrow("content")) ?: "" else "",
        category = getString(getColumnIndexOrThrow("category")) ?: "",
        tags = getString(getColumnIndexOrThrow("tags")) ?: "",
        linkUrl = getString(getColumnIndexOrThrow("link_url")) ?: ""
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
