package id.arsipos.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ConnState {
    object None : ConnState()
    object Checking : ConnState()
    object Offline : ConnState()
    class Ok(val title: String) : ConnState()
    class Bad(val msg: String) : ConnState()
}

class Oversize(val uri: Uri, val name: String, val mime: String, val size: Long)
class PinReq(val title: String, val action: () -> Unit)
class RestorePending(val text: String, val encrypted: Boolean)
class SharedText(val subject: String, val text: String, val url: String?)

private const val MAX_SHARE = 50

fun caption(title: String, category: String, tags: String): String {
    val sb = StringBuilder("📌 ").append(title)
    if (category.isNotBlank()) sb.append("\n📂 ").append(category)
    if (tags.isNotBlank()) sb.append("\n🏷 ").append(tags)
    return sb.toString()
}

fun noteText(title: String, category: String, tags: String, body: String): String =
    caption(title, category, tags) + "\n\n" + body

fun linkText(title: String, category: String, tags: String, url: String, note: String): String =
    caption(title, category, tags) + "\n🔗 " + url + if (note.isNotBlank()) "\n\n$note" else ""

class MainVM(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val db = ArchiveDb(ctx)
    val store = SecureStore(ctx)
    private val main = Handler(Looper.getMainLooper())

    // ----- filter & data -----
    var query by mutableStateOf("")
    var kindFilter by mutableStateOf<String?>(null)
    var categoryFilter by mutableStateOf<String?>(null)
    var tagFilter by mutableStateOf<String?>(null)
    var yearFilter by mutableStateOf<Int?>(null)
    var monthFilter by mutableStateOf<Int?>(null)
    var statusFilter by mutableStateOf<String?>(null)

    var docs by mutableStateOf<List<Doc>>(emptyList())
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
    var categories by mutableStateOf<List<Category>>(emptyList())
    var tags by mutableStateOf<List<String>>(emptyList())
    var years by mutableStateOf<List<Int>>(emptyList())

    // ----- UI state -----
    var draft by mutableStateOf<Draft?>(null)
    var oversize by mutableStateOf<Oversize?>(null)
    var busy by mutableStateOf(false)
    var progress by mutableStateOf<Float?>(null)
    var message by mutableStateOf<String?>(null)
    var toOpen by mutableStateOf<Pair<File, String>?>(null)
    var toLink by mutableStateOf<String?>(null)
    var detail by mutableStateOf<Doc?>(null)
    var editing by mutableStateOf<Doc?>(null)
    var deleting by mutableStateOf<Doc?>(null)
    var restorePending by mutableStateOf<RestorePending?>(null)
    var conn by mutableStateOf<ConnState>(ConnState.None)

    // ----- keamanan -----
    var setupDone by mutableStateOf(store.setupDone)
    var wizardOpen by mutableStateOf(false)
    var locked by mutableStateOf(store.setupDone && store.hasPins())
    var settingsUnlocked by mutableStateOf(false)
    var pinReq by mutableStateOf<PinReq?>(null)
    var biometricOn by mutableStateOf(store.biometric)
    var secureScreen by mutableStateOf(store.secureScreen)
    var lastBackup by mutableStateOf(store.lastBackup)

    // ----- tema -----
    var themeId by mutableStateOf(store.themeId)
    var themeMode by mutableStateOf(store.themeMode)
    var bgIntensity by mutableStateOf(store.bgIntensity)
    var bgAnimated by mutableStateOf(store.bgAnimated)

    // ----- antrean impor -----
    var queue by mutableStateOf<List<QueueItem>>(emptyList())
    var queueOpen by mutableStateOf(false)
    var queueEditing by mutableStateOf<QueueItem?>(null)
    var queueMsg by mutableStateOf<String?>(null)
    var sharedText by mutableStateOf<SharedText?>(null)
    private var seq = 0
    private var leftAt = 0L
    private var lastPct = -1

    init {
        viewModelScope.launch {
            yield() // tunggu snapshot komposisi diterapkan sebelum membaca state
            refresh()
            refreshQueue()
            checkConnection()
        }
    }

    // ================= tema =================

    fun chooseTheme(id: String) { store.themeId = id; themeId = id }
    fun chooseMode(m: Int) { store.themeMode = m; themeMode = m }
    fun chooseIntensity(v: Float) { bgIntensity = v }
    fun commitIntensity() { store.bgIntensity = bgIntensity }
    fun chooseAnimated(on: Boolean) { store.bgAnimated = on; bgAnimated = on }

    // ================= antrean impor (Send Intent) =================

    fun refreshQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val q = db.queueAll()
                withContext(Dispatchers.Main) {
                    queue = q
                    if (q.isEmpty()) queueOpen = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message = "Database error: ${e.message}" }
            }
        }
    }

    /** Menangani ACTION_SEND / ACTION_SEND_MULTIPLE. Hanya URI content:// yang diterima. */
    fun handleShare(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return
        val hint = intent.type
        val uris = mutableListOf<Uri>()
        if (action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        } else {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        }
        if (uris.isEmpty()) {
            val cd = intent.clipData
            if (cd != null) for (i in 0 until cd.itemCount) cd.getItemAt(i).uri?.let { uris += it }
        }
        if (uris.isNotEmpty()) {
            enqueue(uris, hint)
        } else if (action == Intent.ACTION_SEND) {
            val t = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (t.isNotEmpty()) {
                val url = Regex("https?://\\S+").find(t)?.value
                sharedText = SharedText(intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty(), t.take(20000), url)
            }
        }
        // Cegah pemrosesan ulang saat Activity dibuat ulang (rotasi) dari intent yang sama.
        intent.action = null
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.clipData = null
    }

    fun enqueue(input: List<Uri>, hint: String? = null) {
        val list = input.filter { it.scheme == "content" }.distinct()
        val rejected = input.size - list.size
        val use = list.take(MAX_SHARE)
        val cut = list.size - use.size
        if (use.isEmpty()) {
            if (input.isNotEmpty()) message = "Sumber file tidak didukung"
            return
        }
        viewModelScope.launch {
            busy = true
            var ok = 0
            var big = 0
            var fail = 0
            try {
                withContext(Dispatchers.IO) {
                    val dir = File(ctx.filesDir, "queue").apply { mkdirs() }
                    for (u in use) {
                        var f: File? = null
                        try {
                            val (name, size) = meta(u)
                            if (size > Telegram.MAX_UPLOAD) { big++; continue }
                            val mime = ctx.contentResolver.getType(u) ?: hint?.takeIf { !it.endsWith("/*") } ?: "application/octet-stream"
                            val ext = name.substringAfterLast('.', "").filter { it.isLetterOrDigit() }.take(8)
                            val dest = File(dir, "${System.currentTimeMillis()}_${seq++}" + if (ext.isNotEmpty()) ".$ext" else "")
                            f = dest
                            var copied = 0L
                            var tooBig = false
                            val ins = ctx.contentResolver.openInputStream(u) ?: throw java.io.IOException("tidak bisa dibuka")
                            ins.use { i ->
                                dest.outputStream().use { o ->
                                    val buf = ByteArray(64 * 1024)
                                    while (true) {
                                        val n = i.read(buf)
                                        if (n < 0) break
                                        copied += n
                                        if (copied > Telegram.MAX_UPLOAD) { tooBig = true; break }
                                        o.write(buf, 0, n)
                                    }
                                }
                            }
                            if (tooBig) { dest.delete(); big++; continue }
                            val title = name.substringBeforeLast('.', name).ifBlank { name }
                            db.queueAdd(QueueItem(path = dest.absolutePath, origName = name, name = title, mime = mime, size = copied))
                            ok++
                        } catch (e: Exception) {
                            f?.delete()
                            fail++
                        }
                    }
                }
                val q = withContext(Dispatchers.IO) { db.queueAll() }
                queue = q
                if (ok > 0) queueOpen = true
                val parts = mutableListOf<String>()
                if (ok > 0) parts += "$ok file masuk antrean"
                if (big > 0) parts += "$big file > 50 MB dilewati"
                if (fail > 0) parts += "$fail gagal dibaca"
                if (rejected > 0) parts += "$rejected sumber ditolak"
                if (cut > 0) parts += "$cut melebihi batas $MAX_SHARE file"
                if (parts.isNotEmpty()) message = parts.joinToString(", ")
            } finally {
                busy = false
            }
        }
    }

    fun updateQueue(q: QueueItem) {
        queue = queue.map { if (it.id == q.id) q else it }
        viewModelScope.launch(Dispatchers.IO) { try { db.queueUpdate(q) } catch (_: Exception) {} }
    }

    fun deleteQueue(q: QueueItem) {
        queue = queue.filter { it.id != q.id }
        if (queue.isEmpty()) queueOpen = false
        viewModelScope.launch(Dispatchers.IO) {
            try { db.queueDelete(q.id); File(q.path).delete() } catch (_: Exception) {}
        }
    }

    fun applyToAll(category: String?, tagsRaw: String?) {
        val tg = tagsRaw?.let { normTags(it) }
        queue = queue.map { it.copy(category = category ?: it.category, tags = tg ?: it.tags) }
        viewModelScope.launch(Dispatchers.IO) { try { db.queueBulk(category, tg) } catch (_: Exception) {} }
    }

    fun saveQueue() {
        if (!store.ready) { message = "Hubungkan bot Telegram dulu di Pengaturan"; return }
        val items = queue.toList()
        if (items.isEmpty()) return
        val c = tg()
        viewModelScope.launch {
            busy = true
            progress = 0f
            lastPct = -1
            var done = 0
            val failed = mutableListOf<String>()
            try {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                for ((i, q) in items.withIndex()) {
                    queueMsg = "Menyimpan ${i + 1} dari ${items.size}…"
                    try {
                        val f = File(q.path)
                        if (!f.exists()) throw IllegalStateException("file sumber hilang")
                        val text = Extractor.text(ctx, Uri.fromFile(f), q.mime, q.origName)
                        val p = Extractor.parse(text, q.origName)
                        val title = if (q.edited) q.name.trim().ifBlank { q.origName }
                        else p.subject.trim().ifBlank { q.name.trim().ifBlank { q.origName } }
                        val tg = normTags(q.tags)
                        val upName = q.origName.replace(Regex("[/\\\\]"), "_")
                        val total = items.size
                        val r = Telegram.sendFile(c, upName, q.mime, f.length(), caption(title, q.category, tg), { pr -> postProgress((i + pr) / total) }) {
                            f.inputStream()
                        }
                        val date = p.date.ifBlank { today }
                        val (y, m) = yearMonthOf(date)
                        withContext(Dispatchers.IO) {
                            db.insert(
                                Doc(
                                    kind = KIND_FILE, fileName = upName, type = p.type, number = p.number.trim(),
                                    date = date, subject = title, institution = p.institution.trim(), status = "Baru",
                                    fileId = r.fileId, messageId = r.messageId, size = f.length(), mime = q.mime,
                                    year = y, month = m, content = text.take(20000), category = q.category, tags = tg
                                )
                            )
                            db.queueDelete(q.id)
                            f.delete()
                        }
                        done++
                    } catch (e: java.io.IOException) {
                        failed += "${q.name}: ${Telegram.friendly(e)}"
                        break // jaringan bermasalah: hentikan, sisanya tetap di antrean
                    } catch (e: Exception) {
                        failed += "${q.name}: ${Telegram.friendly(e)}"
                    }
                }
            } finally {
                busy = false
                progress = null
                queueMsg = null
            }
            val left = withContext(Dispatchers.IO) { db.queueAll() }
            queue = left
            if (left.isEmpty()) queueOpen = false
            refresh()
            message = if (failed.isEmpty()) "$done file diarsipkan ke Telegram"
            else "$done berhasil, ${failed.size} gagal tetap di antrean. ${failed.first()}"
        }
    }

    // ================= data =================

    fun refresh() {
        val q = query
        val kf = kindFilter
        val cf = categoryFilter
        val tf = tagFilter
        val yf = yearFilter
        val mf = monthFilter
        val sf = statusFilter
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = db.search(q, kf, cf, tf, yf, mf, sf)
                val c = db.counts()
                val cats = db.categories()
                val t = db.allTags()
                val y = db.years()
                withContext(Dispatchers.Main) {
                    docs = r; counts = c; categories = cats; tags = t; years = y
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { message = "Database error: ${e.message}" }
            }
        }
    }

    private fun postProgress(p: Float) {
        val pct = (p * 100).toInt()
        if (pct == lastPct) return
        lastPct = pct
        main.post { progress = p }
    }

    private fun tg(): Conn = Conn(store.token, store.chatId)

    fun checkConnection() {
        if (!store.ready) { conn = ConnState.None; return }
        val c = tg()
        conn = ConnState.Checking
        viewModelScope.launch {
            conn = try {
                ConnState.Ok(Telegram.status(c))
            } catch (e: TgException) {
                ConnState.Bad(e.message ?: "Error")
            } catch (e: Exception) {
                ConnState.Offline
            }
        }
    }

    // ================= unggah dokumen =================

    private fun meta(uri: Uri): Pair<String, Long> {
        var name = "dokumen"
        var size = 0L
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        return name to size
    }

    fun pick(uri: Uri) {
        viewModelScope.launch {
            busy = true
            try {
                val (name, size) = withContext(Dispatchers.IO) { meta(uri) }
                val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
                // Validasi pra-unggah: tolak sebelum ada permintaan jaringan.
                if (size > Telegram.MAX_UPLOAD) {
                    oversize = Oversize(uri, name, mime, size)
                    return@launch
                }
                prepareDraft(uri, name, mime, size)
            } catch (e: Exception) {
                message = "Gagal membaca file: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private suspend fun prepareDraft(uri: Uri, name: String, mime: String, size: Long) {
        val text = Extractor.text(ctx, uri, mime, name)
        val p = Extractor.parse(text, name)
        draft = Draft(
            uri, name, mime, size, p.type, p.number, p.date,
            p.subject, p.institution, "Baru", text.take(20000)
        )
        if (text.isBlank()) message = "Teks tidak terbaca, isi metadata manual"
    }

    fun compressOversize() {
        val o = oversize ?: return
        oversize = null
        viewModelScope.launch {
            busy = true
            progress = -1f
            try {
                val r = Compressor.compress(ctx, o.uri, o.mime, o.name)
                if (r == null) {
                    message = "Tidak bisa dikompres di bawah 50 MB. Gunakan Mode Hybrid."
                } else {
                    prepareDraft(Uri.fromFile(r.file), r.name, r.mime, r.file.length())
                }
            } catch (e: Exception) {
                message = "Kompresi gagal: ${e.message}"
            } catch (e: OutOfMemoryError) {
                message = "Memori tidak cukup untuk kompresi. Gunakan Mode Hybrid."
            } finally {
                busy = false
                progress = null
            }
        }
    }

    private fun deleteTemp(uri: Uri) {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return)
            if (f.absolutePath.startsWith(ctx.cacheDir.absolutePath)) f.delete()
        }
    }

    fun cancelDraft() {
        draft?.let { deleteTemp(it.uri) }
        draft = null
    }

    fun save() {
        val d = draft ?: return
        if (!store.ready) { message = "Hubungkan bot Telegram dulu di Pengaturan"; return }
        if (d.size > Telegram.MAX_UPLOAD) { message = "File lebih dari 50 MB"; return }
        val title = d.subject.trim().ifBlank { d.name }
        val tg = normTags(d.tags)
        val cap = caption(title, d.category, tg)
        val c = tg()
        viewModelScope.launch {
            busy = true
            progress = 0f
            lastPct = -1
            try {
                val r = Telegram.sendFile(c, d.name, d.mime, d.size, cap, { p -> postProgress(p) }) {
                    ctx.contentResolver.openInputStream(d.uri)!!
                }
                val (y, m) = yearMonthOf(d.date)
                withContext(Dispatchers.IO) {
                    db.insert(
                        Doc(
                            kind = KIND_FILE, fileName = d.name, type = d.type, number = d.number.trim(),
                            date = d.date.trim(), subject = title, institution = d.institution.trim(),
                            status = d.status, fileId = r.fileId, messageId = r.messageId, size = d.size,
                            mime = d.mime, year = y, month = m, content = d.content,
                            category = d.category, tags = tg
                        )
                    )
                }
                deleteTemp(d.uri)
                draft = null
                refresh()
                message = "Tersimpan di Telegram & database"
            } catch (e: Exception) {
                message = "Gagal: ${Telegram.friendly(e)}"
            } finally {
                busy = false
                progress = null
            }
        }
    }

    /** Mode Hybrid: file besar diunggah manual lewat aplikasi Telegram, lalu pesannya ditautkan ke indeks. */
    fun saveHybrid(
        link: String, title: String, sizeMb: String, category: String, tagsRaw: String, onDone: () -> Unit
    ) {
        val ref = Telegram.parseMessageLink(link)
        if (ref == null) {
            message = "Tautan pesan tidak valid. Contoh: https://t.me/c/1234567890/42"
            return
        }
        if (store.chatId.isNotBlank() && ref.first != store.chatId) {
            message = "Pesan ini bukan dari channel arsip yang terhubung."
            return
        }
        val head = title.trim()
        if (head.isEmpty()) { message = "Isi judul arsip"; return }
        val size = ((sizeMb.replace(',', '.').toDoubleOrNull() ?: 0.0) * 1024 * 1024).toLong()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val (y, m) = yearMonthOf(today)
        val tg = normTags(tagsRaw)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    db.insert(
                        Doc(
                            kind = KIND_FILE, fileName = head, type = "Lainnya", number = "", date = today,
                            subject = head, institution = "", status = "Baru", fileId = "", messageId = ref.second,
                            size = size, mime = "application/octet-stream", year = y, month = m,
                            category = category, tags = tg
                        )
                    )
                }
                refresh()
                onDone()
                message = "Ditautkan ke pesan Telegram"
            } catch (e: Exception) {
                message = "Gagal: ${e.message}"
            }
        }
    }

    // ================= catatan & tautan =================

    private suspend fun sendOrUpload(c: Conn, full: String, head: String): SendResult =
        if (full.length <= 4000) Telegram.sendText(c, full)
        else Telegram.sendBytes(c, full.toByteArray(Charsets.UTF_8), "catatan.txt", "text/plain", "#CATATAN $head")

    fun saveNote(
        title: String, body: String, category: String, tagsRaw: String, send: Boolean, onDone: () -> Unit
    ) {
        val text = body.trim()
        if (text.isEmpty()) return
        if (send && !store.ready) { message = "Hubungkan bot Telegram dulu di Pengaturan"; return }
        val head = title.trim().ifBlank { text.lineSequence().first().take(60) }
        val tg = normTags(tagsRaw)
        val c = tg()
        viewModelScope.launch {
            busy = true
            try {
                var r = SendResult("", 0)
                if (send) r = sendOrUpload(c, noteText(head, category, tg, text), head)
                insertTextItem(KIND_NOTE, head, text, "", category, tg, r)
                refresh()
                onDone()
                message = if (send) "Catatan tersimpan & dikirim ke Telegram" else "Catatan tersimpan"
            } catch (e: Exception) {
                message = "Gagal: ${Telegram.friendly(e)}"
            } finally {
                busy = false
            }
        }
    }

    fun saveLink(
        title: String, url: String, note: String, category: String, tagsRaw: String, send: Boolean, onDone: () -> Unit
    ) {
        val u = url.trim().let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        val host = try { Uri.parse(u).host ?: "" } catch (e: Exception) { "" }
        if (host.isEmpty() || !host.contains('.')) { message = "URL tidak valid"; return }
        if (send && !store.ready) { message = "Hubungkan bot Telegram dulu di Pengaturan"; return }
        val head = title.trim().ifBlank { host }
        val tg = normTags(tagsRaw)
        val c = tg()
        viewModelScope.launch {
            busy = true
            try {
                var r = SendResult("", 0)
                if (send) r = sendOrUpload(c, linkText(head, category, tg, u, note.trim()), head)
                insertTextItem(KIND_LINK, head, note.trim(), u, category, tg, r)
                refresh()
                onDone()
                message = if (send) "Tautan tersimpan & dikirim ke Telegram" else "Tautan tersimpan"
            } catch (e: Exception) {
                message = "Gagal: ${Telegram.friendly(e)}"
            } finally {
                busy = false
            }
        }
    }

    private suspend fun insertTextItem(
        kind: String, head: String, content: String, url: String, category: String, tg: String, r: SendResult
    ) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val (y, m) = yearMonthOf(today)
        val host = if (url.isNotEmpty()) (try { Uri.parse(url).host ?: "" } catch (e: Exception) { "" }) else ""
        withContext(Dispatchers.IO) {
            db.insert(
                Doc(
                    kind = kind, fileName = head, type = if (kind == KIND_NOTE) "Catatan" else "Tautan",
                    number = "", date = today, subject = head, institution = host, status = "Baru",
                    fileId = r.fileId, messageId = r.messageId, size = content.length.toLong(),
                    mime = "text/plain", year = y, month = m, content = content,
                    category = category, tags = tg, linkUrl = url
                )
            )
        }
    }

    // ================= buka / detail =================

    fun openDetail(d: Doc) {
        viewModelScope.launch {
            val content = withContext(Dispatchers.IO) { db.getContent(d.id) }
            detail = d.copy(content = content)
        }
    }

    fun openFile(d: Doc) {
        if (d.fileId.isBlank() || d.size > Telegram.MAX_DOWNLOAD) { openInTelegram(d); return }
        if (!store.ready) { message = "Hubungkan bot Telegram dulu di Pengaturan"; return }
        val c = tg()
        viewModelScope.launch {
            busy = true
            progress = 0f
            lastPct = -1
            try {
                val safe = d.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val f = File(ctx.cacheDir, "open/${d.id}_$safe")
                if (!f.exists()) Telegram.download(c, d.fileId, f) { p -> postProgress(p) }
                toOpen = f to d.mime
            } catch (e: TooBig) {
                openInTelegram(d)
            } catch (e: Exception) {
                message = "Gagal membuka: ${Telegram.friendly(e)}"
            } finally {
                busy = false
                progress = null
            }
        }
    }

    private fun openInTelegram(d: Doc) {
        val link = Telegram.chatLink(store.chatId, d.messageId)
        if (link != null) {
            toLink = link
        } else if (d.fileId.isNotBlank() && store.ready) {
            val c = tg()
            viewModelScope.launch {
                try {
                    Telegram.resend(c, d.fileId)
                    message = "File dikirim ulang ke chat Telegram arsip"
                } catch (e: Exception) {
                    message = "Gagal: ${Telegram.friendly(e)}"
                }
            }
        } else {
            message = "Tautan pesan Telegram tidak tersedia untuk item ini"
        }
    }

    // ================= aksi kritis (dilindungi PIN Keamanan Aksi) =================

    fun guard(title: String, action: () -> Unit) {
        if (!store.hasPins()) action() else pinReq = PinReq(title, action)
    }

    fun verifyMaster(pin: String): PinResult = store.check("master", pin)

    private suspend fun audit(c: Conn, title: String, verb: String) {
        if (!store.ready) return
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())
        try {
            Telegram.sendText(c, "⚠️ [AUDIT LOG] Arsip \"$title\" telah $verb pada $stamp.")
        } catch (e: Exception) {
            // log audit bersifat best-effort
        }
    }

    fun requestEdit(d: Doc) = guard("Edit arsip") { editing = d }

    fun applyEdit(n0: Doc) {
        val (y, m) = yearMonthOf(n0.date)
        val n = n0.copy(
            year = y, month = m, tags = normTags(n0.tags),
            fileName = if (n0.kind == KIND_FILE && n0.fileId.isNotBlank()) n0.fileName else n0.subject
        )
        val c = tg()
        viewModelScope.launch {
            busy = true
            var warn: String? = null
            try {
                withContext(Dispatchers.IO) { db.update(n) }
                if (n.messageId > 0 && store.ready) {
                    try {
                        when {
                            n.kind == KIND_FILE && n.fileId.isNotBlank() ->
                                Telegram.editCaption(c, n.messageId, caption(n.subject, n.category, n.tags))
                            n.kind == KIND_NOTE && n.fileId.isBlank() ->
                                Telegram.editText(c, n.messageId, noteText(n.subject, n.category, n.tags, n.content))
                            n.kind == KIND_LINK && n.fileId.isBlank() ->
                                Telegram.editText(c, n.messageId, linkText(n.subject, n.category, n.tags, n.linkUrl, n.content))
                            else -> Unit
                        }
                    } catch (e: Exception) {
                        warn = Telegram.friendly(e)
                    }
                }
                audit(c, n.subject, "DIUBAH")
                editing = null
                detail = n
                refresh()
                message = if (warn != null) "Tersimpan lokal. Pesan Telegram tidak diperbarui: $warn" else "Perubahan tersimpan"
            } catch (e: Exception) {
                message = "Gagal menyimpan: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun performDelete(d: Doc, alsoTelegram: Boolean) {
        val c = tg()
        viewModelScope.launch {
            busy = true
            var warn: String? = null
            try {
                if (alsoTelegram && d.messageId > 0 && store.ready) {
                    try {
                        Telegram.deleteMessage(c, d.messageId)
                    } catch (e: Exception) {
                        warn = Telegram.friendly(e)
                    }
                }
                withContext(Dispatchers.IO) {
                    db.delete(d.id)
                    File(ctx.cacheDir, "open").listFiles()
                        ?.filter { it.name.startsWith("${d.id}_") }?.forEach { it.delete() }
                }
                audit(c, d.subject.ifBlank { d.fileName }, "DIHAPUS")
                deleting = null
                detail = null
                refresh()
                message = if (warn != null) "Dihapus dari indeks. Pesan Telegram gagal dihapus: $warn" else "Arsip dihapus"
            } catch (e: Exception) {
                message = "Gagal menghapus: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    // ================= kategori =================

    fun addCategory(c: Category) = categoryOp { db.addCategory(c) }
    fun updateCategory(c: Category) = categoryOp { db.updateCategory(c) }
    fun deleteCategory(c: Category) = categoryOp { db.deleteCategory(c) }

    private fun categoryOp(op: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { op() }
                refresh()
            } catch (e: Exception) {
                message = if ((e.message ?: "").contains("UNIQUE", ignoreCase = true)) "Nama kategori sudah dipakai"
                else "Gagal: ${e.message}"
            }
        }
    }

    // ================= koneksi (wizard & pengaturan) =================

    fun detectChat(token: String, cb: (List<ChatCandidate>?, String?) -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                cb(Telegram.detectChats(token), null)
            } catch (e: Exception) {
                cb(null, Telegram.friendly(e))
            } finally {
                busy = false
            }
        }
    }

    /** Tes tulis via sendMessage; jika berhasil token & chat ID disimpan. cb(null) = sukses. */
    fun testAndSave(token: String, chatId: String, cb: (String?) -> Unit) {
        val c = Conn(token.trim(), chatId.trim())
        if (c.token.isEmpty() || c.chatId.isEmpty()) { cb("Token dan Chat ID wajib diisi"); return }
        viewModelScope.launch {
            busy = true
            try {
                val title = Telegram.status(c)
                Telegram.sendText(c, "✅ ArsipOS terhubung ke \"$title\".")
                store.token = c.token
                store.chatId = c.chatId
                checkConnection()
                cb(null)
            } catch (e: Exception) {
                cb(Telegram.friendly(e))
            } finally {
                busy = false
            }
        }
    }

    fun finishSetup(selected: List<Category>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { db.addCategoriesIgnore(selected) }
            store.setupDone = true
            setupDone = true
            wizardOpen = false
            locked = false
            settingsUnlocked = false
            refresh()
            checkConnection()
        }
    }

    // ================= PIN, kunci, biometrik =================

    fun unlock(pin: String): PinResult {
        val r = store.check("app", pin)
        if (r is PinResult.Ok) { locked = false; leftAt = 0L }
        return r
    }

    fun unlockBio() { locked = false; leftAt = 0L }
    fun verifyApp(pin: String): PinResult = store.check("app", pin)

    fun applySecureScreen(on: Boolean) { store.secureScreen = on; secureScreen = on }

    fun setBiometric(on: Boolean) { store.biometric = on; biometricOn = on }

    fun onBackgrounded() { leftAt = SystemClock.elapsedRealtime() }

    fun onForegrounded() {
        if (leftAt > 0 && store.setupDone && store.hasPins() &&
            SystemClock.elapsedRealtime() - leftAt > 60_000L
        ) {
            locked = true
            settingsUnlocked = false
        }
        leftAt = 0L
    }

    /** Mengembalikan pesan error, atau null jika berhasil. */
    fun changeAppPin(pin: String): String? {
        if (!Regex("\\d{6}").matches(pin)) return "PIN harus 6 digit angka"
        if (store.sameAsMaster(pin)) return "PIN Utama harus berbeda dari PIN Keamanan Aksi"
        store.setAppPin(pin)
        return null
    }

    fun changeMasterPin(pin: String): String? {
        if (!Regex("\\d{6}").matches(pin)) return "PIN harus 6 digit angka"
        if (store.sameAsApp(pin)) return "PIN Keamanan Aksi harus berbeda dari PIN Utama"
        store.setMasterPin(pin)
        return null
    }

    fun sendOtp(cb: (String?) -> Unit) {
        if (!store.ready) { cb("Bot Telegram belum terhubung"); return }
        val c = tg()
        viewModelScope.launch {
            busy = true
            try {
                val code = store.createOtp()
                Telegram.sendText(
                    c, "🔐 Kode OTP ArsipOS: $code\nBerlaku 3 menit. Jangan bagikan kode ini."
                )
                cb(null)
            } catch (e: Exception) {
                cb(Telegram.friendly(e))
            } finally {
                busy = false
            }
        }
    }

    fun resetMasterWithOtp(otp: String, p1: String, p2: String): String? {
        if (p1 != p2) return "Ulangi PIN baru dengan benar"
        if (!store.verifyOtp(otp)) return "Kode OTP salah atau kedaluwarsa"
        val err = changeMasterPin(p1)
        if (err == null) store.clearFails("master")
        return err
    }

    // ================= backup & restore =================

    fun backup(password: String) {
        if (!store.ready) { message = "Hubungkan bot Telegram dulu di Pengaturan"; return }
        val c = tg()
        val security = store.exportSecurity()
        viewModelScope.launch {
            busy = true
            try {
                val (docs, cats) = withContext(Dispatchers.IO) { db.exportAll() to db.categories() }
                val now = Date()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(now)
                val fname = "arsipos_backup_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(now) + ".json"
                var bytes = Backup.toJson(docs, cats, security)
                val enc = password.isNotEmpty()
                if (enc) bytes = withContext(Dispatchers.Default) { Backup.encrypt(bytes, password) }
                Telegram.sendBytes(
                    c, bytes, fname, "application/json",
                    "${Backup.TAG} $stamp (${docs.size} item)" + if (enc) " 🔒" else ""
                )
                store.lastBackup = now.time
                lastBackup = now.time
                message = "Backup ${docs.size} item terkirim ke Telegram"
            } catch (e: Exception) {
                message = "Backup gagal: ${Telegram.friendly(e)}"
            } finally {
                busy = false
            }
        }
    }

    fun loadRestore(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                restorePending = RestorePending(text, Backup.isEncrypted(text))
            } catch (e: Exception) {
                message = "Tidak bisa membaca file: ${e.message}"
            }
        }
    }

    fun performRestore(replace: Boolean, password: String) {
        val rp = restorePending ?: return
        viewModelScope.launch {
            busy = true
            try {
                val data = withContext(Dispatchers.Default) {
                    Backup.parse(if (rp.encrypted) Backup.decrypt(rp.text, password) else rp.text)
                }
                val (added, skipped) = withContext(Dispatchers.IO) { db.importAll(data.docs, data.categories, replace) }
                var extra = ""
                if (replace && data.security != null) {
                    store.importSecurity(data.security)
                    extra = ". PIN dipulihkan dari backup"
                }
                restorePending = null
                refresh()
                message = "Dipulihkan: $added item" + (if (skipped > 0) ", $skipped dilewati (sudah ada)" else "") + extra
            } catch (e: IllegalArgumentException) {
                message = e.message ?: "File backup tidak valid"
            } catch (e: Exception) {
                message = "File backup tidak valid: ${e.message}"
            } finally {
                busy = false
            }
        }
    }
}
