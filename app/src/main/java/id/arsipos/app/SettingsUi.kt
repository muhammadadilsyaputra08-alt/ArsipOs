@file:OptIn(ExperimentalMaterial3Api::class)

package id.arsipos.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ICONS = listOf(
    "📁", "🏢", "🗂", "📝", "🔗", "💰",
    "🎓", "🏥", "🏠", "📊", "⭐"
)
private val COLORS = listOf(
    0xFF1E3A8A.toInt(), 0xFF047857.toInt(), 0xFFB45309.toInt(), 0xFF7C3AED.toInt(),
    0xFFBE123C.toInt(), 0xFF0E7490.toInt(), 0xFF4D7C0F.toInt(), 0xFF475569.toInt()
)

@Composable
fun SettingsScreen(vm: MainVM, onPickBackup: () -> Unit) {
    val store = vm.store
    var token by remember { mutableStateOf(store.token) }
    var chat by remember { mutableStateOf(store.chatId) }
    var connMsg by remember { mutableStateOf<String?>(null) }
    var backupPw by remember { mutableStateOf("") }
    var showCats by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf<String?>(null) } // "app" | "master"
    val ctx = LocalContext.current

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!store.secure) {
            Text(
                "Peringatan: Keystore perangkat tidak tersedia, kredensial disimpan tanpa enkripsi.",
                color = MaterialTheme.colorScheme.error
            )
        }

        ThemePicker(vm)

        Text("Koneksi Telegram", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("Bot token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = chat, onValueChange = { chat = it },
            label = { Text("Chat ID (-100...)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = token.isNotBlank() && !vm.busy, onClick = {
                vm.detectChat(token) { list, err ->
                    if (err != null) connMsg = err
                    else if (list.isNullOrEmpty()) connMsg = "Belum ada pesan terbaca. Jadikan bot Admin channel dan kirim 1 pesan ke channel."
                    else { chat = list.first().id; connMsg = "Terdeteksi: ${list.first().title}" }
                }
            }) { Text("Deteksi Chat ID") }
            Button(enabled = !vm.busy, onClick = {
                val changed = token.trim() != store.token || chat.trim() != store.chatId
                val run = {
                    vm.testAndSave(token, chat) { err -> connMsg = err ?: "Terhubung. Pesan tes terkirim." }
                }
                if (changed) vm.guard("Ganti Token / Chat ID") { run() } else run()
            }) { Text("Tes & simpan") }
        }
        connMsg?.let { Text(it) }

        Text("Keamanan", style = MaterialTheme.typography.titleMedium)
        if (canBiometric(ctx)) {
            Row(
                Modifier.clickable { vm.setBiometric(!vm.biometricOn) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(vm.biometricOn, { vm.setBiometric(it) })
                Text("Buka aplikasi dengan biometrik")
            }
        }
        Row(
            Modifier.clickable { vm.applySecureScreen(!vm.secureScreen) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(vm.secureScreen, { vm.applySecureScreen(it) })
            Text("Blokir screenshot & pratinjau di daftar aplikasi terbaru")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.guard("Ubah PIN Utama") { newPin = "app" } }) { Text("Ubah PIN Utama") }
            OutlinedButton(onClick = { vm.guard("Ubah PIN Keamanan Aksi") { newPin = "master" } }) { Text("Ubah PIN Aksi") }
        }
        OutlinedButton(onClick = { vm.guard("Panduan Setup Ulang") { vm.wizardOpen = true } }) {
            Text("Panduan Setup Ulang")
        }

        Text("Kategori", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { showCats = true }) { Text("Kelola kategori (${vm.categories.size})") }

        Text("Cadangan database", style = MaterialTheme.typography.titleMedium)
        Text(
            "Backup dikirim ke channel arsip dengan keterangan ${Backup.TAG}. Berisi metadata, kategori, tag, " +
                "dan hash PIN (bukan token). Isi kata sandi untuk mengenkripsi dengan AES-256. " +
                "Pulihkan: unduh file backup dari Telegram ke HP, lalu pilih di bawah. " +
                if (vm.lastBackup > 0) "Backup terakhir: " +
                    SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(vm.lastBackup))
                else "Belum pernah backup."
        )
        OutlinedTextField(
            value = backupPw, onValueChange = { backupPw = it },
            label = { Text("Kata sandi enkripsi (opsional)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.backup(backupPw) }, enabled = !vm.busy) { Text("Backup ke Telegram") }
            OutlinedButton(onClick = onPickBackup, enabled = !vm.busy) { Text("Pulihkan dari file") }
        }
        ProgressBar(vm)
        Text(
            "Batas Bot API: unggah maks 50 MB, unduh langsung maks 20 MB (file lebih besar dibuka lewat Telegram).",
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (showCats) CategoryManager(vm) { showCats = false }
    newPin?.let { which ->
        NewPinDialog(
            title = if (which == "app") "PIN Utama baru" else "PIN Keamanan Aksi baru",
            onSubmit = { p -> if (which == "app") vm.changeAppPin(p) else vm.changeMasterPin(p) },
            onClose = { newPin = null }
        )
    }
}

@Composable
fun CategoryManager(vm: MainVM, onClose: () -> Unit) {
    var edit by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<Category?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Kategori") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vm.categories.isEmpty()) Text("Belum ada kategori.")
                vm.categories.forEach { c ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(c.color)))
                        Text("  ${c.icon} ${c.name}", Modifier.weight(1f))
                        TextButton(onClick = { edit = c }) { Text("Ubah") }
                        TextButton(onClick = { del = c }) { Text("Hapus") }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { adding = true }) { Text("Tambah") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Tutup") } }
    )
    if (adding) CategoryEditor(null, { vm.addCategory(it) }) { adding = false }
    edit?.let { c -> CategoryEditor(c, { vm.updateCategory(it) }) { edit = null } }
    del?.let { c ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Hapus kategori?") },
            text = { Text("Kategori \"${c.name}\" dihapus. Arsip di dalamnya tidak ikut terhapus, hanya menjadi tanpa kategori.") },
            confirmButton = { TextButton(onClick = { vm.deleteCategory(c); del = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun CategoryEditor(c: Category?, onSave: (Category) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf(c?.name ?: "") }
    var icon by remember { mutableStateOf(c?.icon ?: ICONS.first()) }
    var color by remember { mutableStateOf(c?.color ?: COLORS.first()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (c == null) "Kategori baru" else "Ubah kategori") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Ikon")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ICONS.forEach { i ->
                        Text(
                            i,
                            Modifier.clickable { icon = i }
                                .border(2.dp, if (i == icon) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .padding(6.dp)
                        )
                    }
                }
                Text("Warna")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLORS.forEach { col ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(col))
                                .border(3.dp, if (col == color) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { color = col }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave(Category(id = c?.id ?: 0, name = name.trim(), color = color, icon = icon))
                onClose()
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}
