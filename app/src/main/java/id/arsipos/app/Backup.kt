package id.arsipos.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupData(val docs: List<Doc>, val categories: List<Category>, val security: Map<String, String>?)

/** Format cadangan: JSON portabel (opsional dienkripsi AES-256-GCM dengan kata sandi). */
object Backup {
    const val TAG = "#ARSIPOS_BACKUP"

    fun toJson(docs: List<Doc>, cats: List<Category>, security: Map<String, String>): ByteArray {
        val arr = JSONArray()
        docs.forEach { d ->
            arr.put(
                JSONObject()
                    .put("kind", d.kind).put("file_name", d.fileName).put("type", d.type).put("number", d.number)
                    .put("date", d.date).put("subject", d.subject).put("institution", d.institution)
                    .put("status", d.status).put("file_id", d.fileId).put("message_id", d.messageId)
                    .put("size", d.size).put("mime", d.mime).put("created_at", d.createdAt)
                    .put("year", d.year).put("month", d.month).put("content", d.content)
                    .put("category", d.category).put("tags", d.tags).put("link_url", d.linkUrl)
            )
        }
        val carr = JSONArray()
        cats.forEach { c -> carr.put(JSONObject().put("name", c.name).put("color", c.color).put("icon", c.icon)) }
        val sec = JSONObject()
        security.forEach { (k, v) -> sec.put(k, v) }
        return JSONObject()
            .put("app", "ArsipOS").put("version", 2)
            .put("exported_at", System.currentTimeMillis())
            .put("count", docs.size).put("docs", arr)
            .put("categories", carr).put("security", sec)
            .toString().toByteArray(Charsets.UTF_8)
    }

    fun isEncrypted(text: String): Boolean = try {
        JSONObject(text).optBoolean("enc", false)
    } catch (e: Exception) {
        false
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val k = f.generateSecret(PBEKeySpec(password.toCharArray(), salt, 100_000, 256))
        return SecretKeySpec(k.encoded, "AES")
    }

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val data = c.doFinal(plain)
        return JSONObject()
            .put("app", "ArsipOS").put("enc", true).put("v", 2)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)
    }

    fun decrypt(text: String, password: String): String {
        val o = JSONObject(text)
        try {
            val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP)
            val data = Base64.decode(o.getString("data"), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            return String(c.doFinal(data), Charsets.UTF_8)
        } catch (e: java.security.GeneralSecurityException) {
            throw IllegalArgumentException("Kata sandi salah atau file rusak")
        }
    }

    fun parse(text: String): BackupData {
        val root = JSONObject(text)
        require(root.optString("app") == "ArsipOS") { "Bukan file backup ArsipOS" }
        val arr = root.getJSONArray("docs")
        val docs = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val date = o.optString("date")
            val (y, m) = yearMonthOf(date)
            val mo = o.optInt("month", m)
            Doc(
                kind = o.optString("kind").ifBlank { if (o.optString("type") == "Catatan") KIND_NOTE else KIND_FILE },
                fileName = o.optString("file_name"),
                type = o.optString("type").ifBlank { "Lainnya" },
                number = o.optString("number"),
                date = date,
                subject = o.optString("subject"),
                institution = o.optString("institution"),
                status = o.optString("status").ifBlank { "Baru" },
                fileId = o.optString("file_id"),
                messageId = o.optLong("message_id"),
                size = o.optLong("size"),
                mime = o.optString("mime"),
                createdAt = o.optLong("created_at", System.currentTimeMillis()),
                year = o.optInt("year", y),
                month = if (mo in 1..12) mo else m,
                content = o.optString("content"),
                category = o.optString("category"),
                tags = o.optString("tags"),
                linkUrl = o.optString("link_url")
            )
        }
        val cats = mutableListOf<Category>()
        root.optJSONArray("categories")?.let { ca ->
            for (i in 0 until ca.length()) {
                val o = ca.getJSONObject(i)
                val name = o.optString("name")
                if (name.isNotBlank()) cats += Category(name = name, color = o.optInt("color"), icon = o.optString("icon"))
            }
        }
        val sec = root.optJSONObject("security")?.let { s ->
            val m = HashMap<String, String>()
            for (k in listOf("app_salt", "app_hash", "master_salt", "master_hash")) {
                val v = s.optString(k)
                if (v.isNotEmpty()) m[k] = v
            }
            if (m.size == 4) m else null
        }
        return BackupData(docs, cats, sec)
    }
}
