package id.arsipos.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class Conn(val token: String, val chatId: String)
class SendResult(val fileId: String, val messageId: Long)
class ChatCandidate(val id: String, val title: String, val type: String)

/** Error dari Telegram dengan pesan yang sudah dalam bahasa Indonesia. */
class TgException(val code: Int, val desc: String, message: String) : IOException(message)

class TooBig : IOException("File lebih dari 20 MB tidak bisa diunduh langsung")

object Telegram {
    const val MAX_UPLOAD = 50L * 1024 * 1024
    const val MAX_DOWNLOAD = 20L * 1024 * 1024

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun api(c: Conn, method: String) = "https://api.telegram.org/bot${c.token}/$method"

    fun friendly(e: Throwable): String = when (e) {
        is TgException -> e.message ?: "Kesalahan Telegram"
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "Tidak ada koneksi internet atau Telegram tidak terjangkau."
        is IOException -> "Gangguan jaringan: ${e.message}"
        else -> e.message ?: e.toString()
    }

    private fun localize(code: Int, desc: String, retry: Int): String {
        val d = desc.lowercase()
        return when {
            code == 401 || code == 404 -> "Token bot tidak valid atau sudah dicabut. Buat ulang token di @BotFather."
            code == 403 -> "Bot belum ditambahkan sebagai Admin di channel, atau izin Post/Edit/Delete Messages belum diaktifkan."
            code == 409 -> "Bot ini memakai webhook sehingga Chat ID tidak bisa dideteksi otomatis. Gunakan bot baru atau isi Chat ID manual."
            code == 413 -> "Ukuran file terlalu besar untuk Telegram Bot API (maks 50 MB)."
            code == 429 -> "Terlalu banyak permintaan ke Telegram. Coba lagi dalam ${retry.coerceAtLeast(1)} detik."
            code == 400 && "chat not found" in d -> "Chat ID tidak ditemukan. Pastikan ID benar dan bot sudah menjadi Admin channel."
            code == 400 && ("message to edit not found" in d || "can't be edited" in d) ->
                "Pesan di Telegram tidak ditemukan atau sudah tidak bisa diubah."
            code == 400 && "message to delete not found" in d -> "Pesan sudah tidak ada di Telegram."
            code == 400 -> "Permintaan ditolak Telegram: $desc"
            else -> "Kesalahan Telegram ($code): $desc"
        }
    }

    private fun check(r: Response): JSONObject {
        val body = r.body?.string() ?: ""
        val j = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw TgException(r.code, "", localize(r.code, "respons tidak valid", 0))
        }
        if (!j.optBoolean("ok")) {
            val code = j.optInt("error_code", r.code)
            val desc = j.optString("description")
            val retry = j.optJSONObject("parameters")?.optInt("retry_after", 0) ?: 0
            throw TgException(code, desc, localize(code, desc, retry))
        }
        return j
    }

    private fun streamBody(
        mime: String, size: Long, onProgress: (Float) -> Unit, open: () -> InputStream
    ) = object : RequestBody() {
        override fun contentType() = mime.toMediaTypeOrNull()
        override fun contentLength() = if (size > 0) size else -1L
        override fun writeTo(sink: BufferedSink) {
            open().use { input ->
                val buf = ByteArray(16 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    sink.write(buf, 0, n)
                    done += n
                    if (size > 0) onProgress((done.toFloat() / size).coerceAtMost(1f))
                }
            }
        }
    }

    // ---------- Kirim ----------

    suspend fun sendFile(
        c: Conn, name: String, mime: String, size: Long, caption: String,
        onProgress: (Float) -> Unit, open: () -> InputStream
    ): SendResult = withContext(Dispatchers.IO) {
        if (size > MAX_UPLOAD) throw TgException(413, "", localize(413, "", 0))
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", c.chatId)
            .addFormDataPart("caption", caption.take(1024))
            .addFormDataPart("document", name, streamBody(mime, size, onProgress, open))
            .build()
        http.newCall(Request.Builder().url(api(c, "sendDocument")).post(body).build()).execute().use { r ->
            val res = check(r).getJSONObject("result")
            SendResult(res.getJSONObject("document").getString("file_id"), res.optLong("message_id"))
        }
    }

    suspend fun sendBytes(c: Conn, bytes: ByteArray, name: String, mime: String, caption: String): SendResult =
        sendFile(c, name, mime, bytes.size.toLong(), caption, {}) { bytes.inputStream() }

    suspend fun sendText(c: Conn, text: String): SendResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("chat_id", c.chatId).add("text", text.take(4096)).build()
        http.newCall(Request.Builder().url(api(c, "sendMessage")).post(body).build()).execute().use { r ->
            SendResult("", check(r).getJSONObject("result").optLong("message_id"))
        }
    }

    suspend fun editCaption(c: Conn, messageId: Long, caption: String) = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("chat_id", c.chatId).add("message_id", messageId.toString())
            .add("caption", caption.take(1024)).build()
        editCall(c, "editMessageCaption", body)
    }

    suspend fun editText(c: Conn, messageId: Long, text: String) = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("chat_id", c.chatId).add("message_id", messageId.toString())
            .add("text", text.take(4096)).build()
        editCall(c, "editMessageText", body)
    }

    private fun editCall(c: Conn, method: String, body: FormBody) {
        try {
            http.newCall(Request.Builder().url(api(c, method)).post(body).build()).execute().use { check(it) }
        } catch (e: TgException) {
            if ("not modified" in e.desc.lowercase()) return
            throw e
        }
    }

    suspend fun deleteMessage(c: Conn, messageId: Long) {
        val body = FormBody.Builder().add("chat_id", c.chatId).add("message_id", messageId.toString()).build()
        withContext(Dispatchers.IO) {
            try {
                http.newCall(Request.Builder().url(api(c, "deleteMessage")).post(body).build()).execute()
                    .use { check(it) }
            } catch (e: TgException) {
                if ("not found" !in e.desc.lowercase()) throw e
            }
        }
    }

    suspend fun resend(c: Conn, fileId: String) = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("chat_id", c.chatId).add("document", fileId).build()
        http.newCall(Request.Builder().url(api(c, "sendDocument")).post(body).build()).execute().use { check(it) }
        Unit
    }

    // ---------- Unduh ----------

    suspend fun download(c: Conn, fileId: String, dest: File, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val path = try {
                val req = Request.Builder()
                    .url(api(c, "getFile") + "?file_id=" + URLEncoder.encode(fileId, "UTF-8")).build()
                http.newCall(req).execute().use { r -> check(r).getJSONObject("result").getString("file_path") }
            } catch (e: TgException) {
                if ("too big" in e.desc.lowercase()) throw TooBig()
                throw e
            }
            val req = Request.Builder().url("https://api.telegram.org/file/bot${c.token}/$path").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw TgException(r.code, "", localize(r.code, "unduh gagal", 0))
                dest.parentFile?.mkdirs()
                val part = File(dest.path + ".part")
                try {
                    val body = r.body!!
                    val total = body.contentLength()
                    body.byteStream().use { i ->
                        part.outputStream().use { o ->
                            val buf = ByteArray(16 * 1024)
                            var done = 0L
                            while (true) {
                                val n = i.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                done += n
                                if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                            }
                        }
                    }
                    if (!part.renameTo(dest)) throw IOException("Gagal menyimpan file")
                } finally {
                    if (part.exists()) part.delete()
                }
            }
        }

    // ---------- Koneksi & deteksi Chat ID ----------

    /** Mengembalikan nama channel/grup. */
    suspend fun status(c: Conn): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(api(c, "getMe")).build()).execute().use { check(it) }
        val chat = http.newCall(
            Request.Builder().url(api(c, "getChat") + "?chat_id=" + URLEncoder.encode(c.chatId, "UTF-8")).build()
        ).execute().use { check(it) }
        chat.getJSONObject("result").let { it.optString("title", it.optString("first_name", c.chatId)) }
    }

    /** Membaca getUpdates; channel diprioritaskan, lalu grup. Terbaru lebih dulu. */
    suspend fun detectChats(token: String): List<ChatCandidate> = withContext(Dispatchers.IO) {
        val c = Conn(token.trim(), "")
        val j = http.newCall(Request.Builder().url(api(c, "getUpdates") + "?limit=100").build())
            .execute().use { check(it) }
        val arr = j.getJSONArray("result")
        val found = LinkedHashMap<String, ChatCandidate>()
        for (i in arr.length() - 1 downTo 0) {
            val u = arr.getJSONObject(i)
            for (key in listOf("channel_post", "edited_channel_post", "message", "my_chat_member")) {
                val o = u.optJSONObject(key) ?: continue
                val chat = o.optJSONObject("chat") ?: continue
                val type = chat.optString("type")
                if (type == "private") continue
                val id = chat.optLong("id").toString()
                if (!found.containsKey(id)) found[id] = ChatCandidate(id, chat.optString("title", id), type)
            }
        }
        found.values.sortedBy { if (it.type == "channel") 0 else 1 }
    }

    // ---------- Tautan pesan ----------

    /** https://t.me/c/<id tanpa -100>/<message_id>, hanya untuk channel/supergroup (-100...). */
    fun chatLink(chatId: String, messageId: Long): String? {
        if (messageId <= 0 || !chatId.startsWith("-100")) return null
        return "https://t.me/c/${chatId.removePrefix("-100")}/$messageId"
    }

    /** Mengembalikan (chatId "-100...", messageId) dari tautan pesan Telegram. */
    fun parseMessageLink(url: String): Pair<String, Long>? {
        val m = Regex("t\\.me/c/(\\d+)/(?:\\d+/)?(\\d+)").find(url.trim()) ?: return null
        val id = m.groupValues[2].toLongOrNull() ?: return null
        return "-100" + m.groupValues[1] to id
    }
}
