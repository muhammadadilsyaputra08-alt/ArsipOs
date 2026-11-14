@file:OptIn(ExperimentalMaterial3Api::class)

package id.arsipos.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import java.util.Locale

fun openUrl(ctx: Context, url: String): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

fun copyText(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun readClipboard(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim() ?: ""
}

fun formatSize(b: Long): String = when {
    b >= (1L shl 30) -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    b >= (1L shl 20) -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}

fun fmtLock(ms: Long): String {
    val s = (ms + 999) / 1000
    return if (s >= 60) "${(s + 59) / 60} menit" else "$s detik"
}

fun Context.findActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun Picker(label: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: $current", maxLines = 1, softWrap = false) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false })
            }
        }
    }
}

/** Bilah progres: persentase saat unggah/unduh, indeterminate saat sibuk tanpa angka. */
@Composable
fun ProgressBar(vm: MainVM) {
    val p = vm.progress
    if (p != null && p >= 0f) {
        Column(Modifier.fillMaxWidth()) {
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            Text("${(p * 100).toInt()}%")
        }
    } else if (vm.busy) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}
