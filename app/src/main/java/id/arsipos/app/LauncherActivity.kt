package id.arsipos.app

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Pintu masuk tanpa Compose: menampilkan diagnosis jika peluncuran sebelumnya gagal. */
class LauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val marker = File(filesDir, "launching")
        val crash = File(filesDir, "crash.txt")

        if (!marker.exists() && !crash.exists()) {
            startMain(marker)
            return
        }

        val sb = StringBuilder("Peluncuran sebelumnya gagal.\n\n")
        if (crash.exists()) sb.append(runCatching { crash.readText() }.getOrDefault("")).append('\n')
        else sb.append("(Tidak ada stack trace tersimpan: crash terjadi sebelum handler aktif.)\n")
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.getHistoricalProcessExitReasons(packageName, 0, 3).forEach {
                    sb.append("\nExit: reason=${it.reason} status=${it.status} desc=${it.description}")
                }
            }
        }
        sb.append("\n\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, ABI ${Build.SUPPORTED_ABIS.joinToString()}")
        show(sb.toString(), marker, crash)
    }

    private fun startMain(marker: File) {
        try {
            marker.writeText(System.currentTimeMillis().toString())
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } catch (e: Throwable) {
            File(filesDir, "crash.txt").writeText("Gagal memulai MainActivity: $e")
            recreate()
        }
    }

    private fun show(text: String, marker: File, crash: File) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(this).apply { addView(tv) }
        val copy = Button(this).apply {
            this.text = "Salin laporan"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("crash", text))
            }
        }
        val retry = Button(this).apply {
            this.text = "Coba lagi"
            setOnClickListener {
                marker.delete(); crash.delete()
                startMain(marker)
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(copy)
            addView(retry)
        }
        setContentView(root)
    }
}
