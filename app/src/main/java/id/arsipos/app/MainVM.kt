package id.arsipos.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainVM(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val db = ArchiveDb(ctx)
    val prefs = Prefs(ctx)

    var query by mutableStateOf("")
    var typeFilter by mutableStateOf<String?>(null)
    var yearFilter by mutableStateOf<Int?>(null)
    var monthFilter by mutableStateOf<Int?>(null)
    var statusFilter by mutableStateOf<String?>(null)

    var docs by mutableStateOf<List<Doc>>(emptyList())
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())
    var years by mutableStateOf<List<Int>>(emptyList())

    var draft by mutableStateOf<Draft?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var toOpen by mutableStateOf<Pair<File, String>?>(null)

    var token by mutableStateOf(prefs.token)
    var chatId by mutableStateOf(prefs.chatId)

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val r = db.search(query, typeFilter, yearFilter, monthFilter, statusFilter)
            val c = db.counts()
            val y = db.years()
            withContext(Dispatchers.Main) { docs = r; counts = c; years = y }
        }
    }

    fun pick(uri: Uri) {
        viewModelScope.launch {
            busy = true
            try {
                val (name, size) = withContext(Dispatchers.IO) { meta(uri) }
                val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
                val text = Extractor.text(ctx, uri, mime, name)
                val p = Extractor.parse(text, name)
                draft = Draft(
                    uri, name, mime, size, p.type, p.number, p.date,
                    p.subject, p.institution, "Baru", text.take(20000)
                )
                if (text.isBlank()) message = "Teks tidak terbaca, isi metadata manual"
            } catch (e: Exception) {
                message = "Gagal membaca file: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

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

    fun save() {
        val d = draft ?: return
        if (!prefs.ready) { message = "Isi token bot & chat ID di Pengaturan"; return }
        viewModelScope.launch {
            busy = true
            try {
                val fid = Telegram.upload(ctx, prefs, d.uri, d.name, d.mime, d.size)
                val (y, m) = yearMonthOf(d.date)
                withContext(Dispatchers.IO) {
                    db.insert(
                        Doc(
                            fileName = d.name, type = d.type, number = d.number.trim(), date = d.date.trim(),
                            subject = d.subject.trim(), institution = d.institution.trim(), status = d.status,
                            fileId = fid, size = d.size, mime = d.mime, year = y, month = m, content = d.content
                        )
                    )
                }
                draft = null
                refresh()
                message = "Tersimpan di Telegram & database"
            } catch (e: Exception) {
                message = "Gagal: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun open(d: Doc) {
        if (!prefs.ready) { message = "Isi token bot & chat ID di Pengaturan"; return }
        viewModelScope.launch {
            busy = true
            try {
                val safe = d.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val f = File(ctx.cacheDir, "open/${d.id}_$safe")
                if (!f.exists()) Telegram.download(prefs, d.fileId, f)
                toOpen = f to d.mime
            } catch (e: Telegram.TooBig) {
                try {
                    Telegram.resend(prefs, d.fileId)
                    message = "File >20 MB: dikirim ulang ke chat Telegram arsip"
                } catch (e2: Exception) {
                    message = "Gagal: ${e2.message}"
                }
            } catch (e: Exception) {
                message = "Gagal membuka: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun delete(d: Doc) {
        viewModelScope.launch(Dispatchers.IO) {
            db.delete(d.id)
            File(ctx.cacheDir, "open").listFiles()?.filter { it.name.startsWith("${d.id}_") }?.forEach { it.delete() }
            refresh()
        }
    }

    fun saveSettings() {
        prefs.token = token
        prefs.chatId = chatId
        message = "Pengaturan disimpan"
    }

    fun testConnection() {
        saveSettingsSilently()
        viewModelScope.launch {
            busy = true
            message = try { Telegram.test(prefs) } catch (e: Exception) { "Gagal: ${e.message}" }
            busy = false
        }
    }

    private fun saveSettingsSilently() {
        prefs.token = token
        prefs.chatId = chatId
    }
}
