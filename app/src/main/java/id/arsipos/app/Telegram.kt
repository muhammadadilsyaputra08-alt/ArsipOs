package id.arsipos.app

import android.content.Context
import android.net.Uri
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
import okio.source
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Telegram {
    const val MAX_UPLOAD = 50L * 1024 * 1024

    class TooBig : Exception("File lebih dari 20 MB")

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun api(p: Prefs, method: String) = "https://api.telegram.org/bot${p.token}/$method"

    private fun check(r: Response): JSONObject {
        val body = r.body?.string() ?: ""
        val j = try { JSONObject(body) } catch (e: Exception) {
            throw IOException("Respons tidak valid (HTTP ${r.code})")
        }
        if (!j.optBoolean("ok")) throw IOException(j.optString("description", "Error HTTP ${r.code}"))
        return j
    }

    private fun streamBody(ctx: Context, uri: Uri, mime: String, size: Long) = object : RequestBody() {
        override fun contentType() = mime.toMediaTypeOrNull()
        override fun contentLength() = if (size > 0) size else -1L
        override fun writeTo(sink: BufferedSink) {
            ctx.contentResolver.openInputStream(uri)!!.use { sink.writeAll(it.source()) }
        }
    }

    suspend fun upload(ctx: Context, p: Prefs, uri: Uri, name: String, mime: String, size: Long): String =
        withContext(Dispatchers.IO) {
            if (size > MAX_UPLOAD) throw IOException("Maksimal 50 MB per file (batas Telegram Bot API)")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", p.chatId)
                .addFormDataPart("caption", name.take(200))
                .addFormDataPart("document", name, streamBody(ctx, uri, mime, size))
                .build()
            val req = Request.Builder().url(api(p, "sendDocument")).post(body).build()
            http.newCall(req).execute().use { r ->
                check(r).getJSONObject("result").getJSONObject("document").getString("file_id")
            }
        }

    suspend fun download(p: Prefs, fileId: String, dest: File) = withContext(Dispatchers.IO) {
        val path = try {
            val req = Request.Builder()
                .url(api(p, "getFile") + "?file_id=" + URLEncoder.encode(fileId, "UTF-8")).build()
            http.newCall(req).execute().use { r ->
                check(r).getJSONObject("result").getString("file_path")
            }
        } catch (e: IOException) {
            if (e.message?.contains("too big", ignoreCase = true) == true) throw TooBig()
            throw e
        }
        val req = Request.Builder().url("https://api.telegram.org/file/bot${p.token}/$path").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Unduh gagal (HTTP ${r.code})")
            dest.parentFile?.mkdirs()
            r.body!!.byteStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
        }
    }

    suspend fun resend(p: Prefs, fileId: String) = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("chat_id", p.chatId).add("document", fileId).build()
        http.newCall(Request.Builder().url(api(p, "sendDocument")).post(body).build()).execute()
            .use { check(it) }
        Unit
    }

    suspend fun test(p: Prefs): String = withContext(Dispatchers.IO) {
        val me = http.newCall(Request.Builder().url(api(p, "getMe")).build()).execute().use { check(it) }
        val bot = me.getJSONObject("result").optString("username")
        val chat = http.newCall(
            Request.Builder().url(api(p, "getChat") + "?chat_id=" + URLEncoder.encode(p.chatId, "UTF-8")).build()
        ).execute().use { check(it) }
        val title = chat.getJSONObject("result").let { it.optString("title", it.optString("first_name", "")) }
        "OK: @$bot → $title"
    }
}
