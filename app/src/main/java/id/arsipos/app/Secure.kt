package id.arsipos.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

sealed class PinResult {
    object Ok : PinResult()
    class Wrong(val lockedMs: Long) : PinResult()
    class Locked(val ms: Long) : PinResult()
}

/**
 * Penyimpanan terenkripsi (Android Keystore + AES-256 GCM) untuk token, chat ID,
 * hash PIN, dan status penguncian. PIN disimpan hanya sebagai hash SHA-256 + salt acak.
 */
class SecureStore(ctx: Context) {
    val secure: Boolean
    private val sp: SharedPreferences

    init {
        val app = ctx.applicationContext
        var enc: SharedPreferences? = create(app)
        if (enc == null) {
            try { app.deleteSharedPreferences(FILE) } catch (_: Exception) {}
            enc = create(app)
        }
        secure = enc != null
        sp = enc ?: app.getSharedPreferences("arsipos_fallback", Context.MODE_PRIVATE)
        migrate(app)
    }

    private fun create(app: Context): SharedPreferences? = try {
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            app, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        null
    }

    private fun migrate(app: Context) {
        val old = app.getSharedPreferences("arsipos", Context.MODE_PRIVATE)
        if (old.contains("token") || old.contains("chat_id")) {
            if (!sp.contains("token")) {
                sp.edit()
                    .putString("token", old.getString("token", "") ?: "")
                    .putString("chat_id", old.getString("chat_id", "") ?: "")
                    .putLong("last_backup", old.getLong("last_backup", 0L))
                    .apply()
            }
            old.edit().clear().apply()
        }
    }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v.trim()).apply()
    var chatId: String
        get() = sp.getString("chat_id", "") ?: ""
        set(v) = sp.edit().putString("chat_id", v.trim()).apply()
    var lastBackup: Long
        get() = sp.getLong("last_backup", 0L)
        set(v) = sp.edit().putLong("last_backup", v).apply()
    var setupDone: Boolean
        get() = sp.getBoolean("setup_done", false)
        set(v) = sp.edit().putBoolean("setup_done", v).apply()
    var biometric: Boolean
        get() = sp.getBoolean("biometric", false)
        set(v) = sp.edit().putBoolean("biometric", v).apply()
    var secureScreen: Boolean
        get() = sp.getBoolean("secure_screen", false)
        set(v) = sp.edit().putBoolean("secure_screen", v).apply()
    var themeId: String
        get() = sp.getString("theme_id", "classic") ?: "classic"
        set(v) = sp.edit().putString("theme_id", v).apply()
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit().putInt("theme_mode", v).apply()
    var bgIntensity: Float
        get() = sp.getFloat("bg_intensity", 0.8f)
        set(v) = sp.edit().putFloat("bg_intensity", v).apply()
    var bgAnimated: Boolean
        get() = sp.getBoolean("bg_animated", true)
        set(v) = sp.edit().putBoolean("bg_animated", v).apply()
    val ready: Boolean get() = token.isNotBlank() && chatId.isNotBlank()

    // ---------- PIN ----------

    private fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val sb = salt.toByteArray(Charsets.UTF_8)
        var d = md.digest(sb + pin.toByteArray(Charsets.UTF_8))
        repeat(20000) { d = md.digest(d + sb) }
        return d.joinToString("") { "%02x".format(it) }
    }

    fun hasPins(): Boolean = sp.contains("app_hash") && sp.contains("master_hash")

    fun setAppPin(pin: String) = setPin("app", pin)
    fun setMasterPin(pin: String) = setPin("master", pin)

    private fun setPin(prefix: String, pin: String) {
        val s = newSalt()
        sp.edit().putString("${prefix}_salt", s).putString("${prefix}_hash", hashPin(pin, s)).apply()
        clearFails(prefix)
    }

    private fun matches(prefix: String, pin: String): Boolean {
        val s = sp.getString("${prefix}_salt", null) ?: return false
        val h = sp.getString("${prefix}_hash", null) ?: return false
        return MessageDigest.isEqual(h.toByteArray(), hashPin(pin, s).toByteArray())
    }

    fun sameAsApp(pin: String) = matches("app", pin)
    fun sameAsMaster(pin: String) = matches("master", pin)

    /** prefix: "app" (PIN Utama) atau "master" (PIN Keamanan Aksi). */
    fun check(prefix: String, pin: String): PinResult {
        val rem = lockRemaining(prefix)
        if (rem > 0) return PinResult.Locked(rem)
        if (matches(prefix, pin)) {
            clearFails(prefix)
            return PinResult.Ok
        }
        return PinResult.Wrong(registerFail(prefix))
    }

    fun lockRemaining(prefix: String): Long =
        (sp.getLong("${prefix}_lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun registerFail(prefix: String): Long {
        val c = sp.getInt("${prefix}_fails", 0) + 1
        var until = sp.getLong("${prefix}_lock_until", 0L)
        var locked = 0L
        if (c % 3 == 0) {
            val minutes = when {
                c >= 9 -> 60
                c >= 6 -> 15
                else -> 5
            }
            locked = minutes * 60_000L
            until = System.currentTimeMillis() + locked
        }
        sp.edit().putInt("${prefix}_fails", c).putLong("${prefix}_lock_until", until).apply()
        return locked
    }

    fun clearFails(prefix: String) {
        sp.edit().putInt("${prefix}_fails", 0).putLong("${prefix}_lock_until", 0L).apply()
    }

    // ---------- OTP pemulihan PIN Keamanan ----------

    fun createOtp(): String {
        val code = String.format(Locale.US, "%06d", SecureRandom().nextInt(1_000_000))
        val s = newSalt()
        sp.edit()
            .putString("otp_salt", s).putString("otp_hash", hashPin(code, s))
            .putLong("otp_exp", System.currentTimeMillis() + 180_000L).putInt("otp_tries", 0)
            .apply()
        return code
    }

    fun verifyOtp(code: String): Boolean {
        val s = sp.getString("otp_salt", null) ?: return false
        val h = sp.getString("otp_hash", null) ?: return false
        if (System.currentTimeMillis() > sp.getLong("otp_exp", 0L)) return false
        val tries = sp.getInt("otp_tries", 0)
        if (tries >= 5) return false
        sp.edit().putInt("otp_tries", tries + 1).apply()
        val ok = MessageDigest.isEqual(h.toByteArray(), hashPin(code, s).toByteArray())
        if (ok) sp.edit().remove("otp_hash").remove("otp_salt").apply()
        return ok
    }

    // ---------- Backup konfigurasi keamanan (hanya hash) ----------

    fun exportSecurity(): Map<String, String> =
        listOf("app_salt", "app_hash", "master_salt", "master_hash")
            .mapNotNull { k -> sp.getString(k, null)?.let { k to it } }
            .toMap()

    fun importSecurity(m: Map<String, String>) {
        val e = sp.edit()
        for (k in listOf("app_salt", "app_hash", "master_salt", "master_hash")) {
            m[k]?.let { e.putString(k, it) }
        }
        e.apply()
        clearFails("app")
        clearFails("master")
    }

    companion object {
        private const val FILE = "arsipos_secure"
    }
}
