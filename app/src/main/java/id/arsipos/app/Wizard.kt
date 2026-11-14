@file:OptIn(ExperimentalMaterial3Api::class)

package id.arsipos.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private const val TOTAL = 6

/** Panduan setup 6 langkah: first-run, atau dibuka ulang dari Pengaturan (rerun = true). */
@Composable
fun WizardScreen(vm: MainVM) {
    val ctx = LocalContext.current
    val rerun = vm.setupDone
    var step by remember { mutableIntStateOf(1) }
    var token by remember { mutableStateOf(vm.store.token) }
    var candidates by remember { mutableStateOf<List<ChatCandidate>>(emptyList()) }
    var chosen by remember { mutableStateOf<ChatCandidate?>(null) }
    var connMsg by remember { mutableStateOf<String?>(null) }
    var connOk by remember { mutableStateOf(vm.store.ready && vm.conn is ConnState.Ok) }
    var a1 by remember { mutableStateOf("") }
    var a2 by remember { mutableStateOf("") }
    var m1 by remember { mutableStateOf("") }
    var m2 by remember { mutableStateOf("") }
    var pinErr by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(DEFAULT_CATEGORIES.map { it.name }.toSet()) }

    fun next() { step = if (rerun && step == 4) 6 else step + 1 }
    fun back() { step = if (rerun && step == 6) 4 else step - 1 }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Langkah $step dari $TOTAL", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(progress = { step / TOTAL.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (step) {
                1 -> {
                    Text("Persiapan Telegram", style = MaterialTheme.typography.titleLarge)
                    Text("ArsipOS memakai akun Telegram, bot pribadi, dan channel privat milik Anda sebagai penyimpanan cloud gratis. Data tidak melewati server pihak ketiga.")
                    Text("Pastikan Anda punya nomor HP aktif yang terdaftar di Telegram, dan aplikasi Telegram terpasang (atau akses ke web.telegram.org).")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openUrl(ctx, "https://telegram.org/dl") }) { Text("Unduh Telegram") }
                        OutlinedButton(onClick = { openUrl(ctx, "https://web.telegram.org") }) { Text("Telegram Web") }
                    }
                }
                2 -> {
                    Text("Buat bot pribadi", style = MaterialTheme.typography.titleLarge)
                    Text("1. Buka @BotFather di Telegram.\n2. Kirim perintah /newbot.\n3. Isi nama tampilan bot.\n4. Isi username unik yang berakhiran \"bot\".\n5. Salin HTTP API Token yang diberikan.")
                    Button(onClick = { openUrl(ctx, "https://t.me/BotFather") }) { Text("Buka @BotFather") }
                }
                3 -> {
                    Text("Buat Private Channel", style = MaterialTheme.typography.titleLarge)
                    Text("1. Di Telegram, buat Channel baru dan pilih tipe Private.\n2. Buka Channel > Administrators > Add Administrator.\n3. Cari username bot Anda dan tambahkan.\n4. Aktifkan izin: Post Messages, Edit Messages, dan Delete Messages.\n5. Kirim satu pesan apa saja ke channel (dipakai untuk mendeteksi Chat ID).")
                }
                4 -> {
                    Text("Hubungkan bot", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = token, onValueChange = { token = it; connOk = false },
                        label = { Text("Bot token") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { token = readClipboard(ctx); connOk = false }) { Text("Paste") }
                        Button(enabled = token.isNotBlank() && !vm.busy, onClick = {
                            connMsg = null
                            vm.detectChat(token) { list, err ->
                                if (err != null) connMsg = err
                                else if (list.isNullOrEmpty()) connMsg = "Belum ada pesan terbaca. Pastikan bot sudah menjadi Admin channel, kirim 1 pesan ke channel, lalu coba lagi."
                                else { candidates = list; chosen = list.first(); connMsg = null }
                            }
                        }) { Text("Deteksi Chat ID Otomatis") }
                    }
                    if (candidates.size > 1) {
                        Text("Pilih channel/grup tujuan:")
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            candidates.forEach { c ->
                                FilterChip(selected = chosen?.id == c.id, onClick = { chosen = c; connOk = false }, label = { Text(c.title) })
                            }
                        }
                    }
                    chosen?.let { c ->
                        Text("Ditemukan: ${c.title}\nID: ${c.id}", style = MaterialTheme.typography.bodyMedium)
                        Button(enabled = !vm.busy, onClick = {
                            vm.testAndSave(token, c.id) { err ->
                                connOk = err == null
                                connMsg = err ?: "Terhubung ke ${c.title}. Pesan tes terkirim ke channel."
                            }
                        }) { Text("Gunakan & tes koneksi") }
                    }
                    connMsg?.let {
                        Text(it, color = if (connOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    if (connOk && connMsg == null) Text("Koneksi sudah aktif.", color = MaterialTheme.colorScheme.primary)
                }
                5 -> {
                    Text("Keamanan Dual-PIN", style = MaterialTheme.typography.titleLarge)
                    Text("PIN Utama membuka aplikasi dan Pengaturan. PIN Keamanan Aksi diminta setiap kali mengedit, menghapus, mengganti token, atau memulihkan backup. Keduanya 6 digit dan harus berbeda.")
                    PinField(a1, { a1 = it }, "PIN Utama")
                    PinField(a2, { a2 = it }, "Ulangi PIN Utama")
                    PinField(m1, { m1 = it }, "PIN Keamanan Aksi")
                    PinField(m2, { m2 = it }, "Ulangi PIN Keamanan Aksi")
                    pinErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                else -> {
                    Text("Kategori awal", style = MaterialTheme.typography.titleLarge)
                    Text("Pilih kategori bawaan. Anda bisa menambah, mengubah, dan menghapusnya kapan saja di Pengaturan.")
                    DEFAULT_CATEGORIES.forEach { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (c.name in selected) selected - c.name else selected + c.name
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = c.name in selected, onCheckedChange = {
                                selected = if (it) selected + c.name else selected - c.name
                            })
                            Text("${c.icon}  ${c.name}")
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step == 1) {
                if (rerun) TextButton(onClick = { vm.wizardOpen = false }) { Text("Batal") } else Text("")
            } else {
                TextButton(onClick = { back() }) { Text("Kembali") }
            }
            when (step) {
                4 -> Button(enabled = connOk, onClick = { next() }) { Text("Lanjut") }
                5 -> Button(onClick = {
                    pinErr = when {
                        !Regex("\\d{6}").matches(a1) || !Regex("\\d{6}").matches(m1) -> "Kedua PIN harus 6 digit angka"
                        a1 != a2 -> "Ulangi PIN Utama dengan benar"
                        m1 != m2 -> "Ulangi PIN Keamanan Aksi dengan benar"
                        a1 == m1 -> "PIN Utama dan PIN Keamanan Aksi harus berbeda"
                        else -> null
                    }
                    if (pinErr == null) {
                        vm.store.setAppPin(a1)
                        vm.store.setMasterPin(m1)
                        next()
                    }
                }) { Text("Lanjut") }
                6 -> Button(onClick = {
                    vm.finishSetup(DEFAULT_CATEGORIES.filter { it.name in selected })
                }) { Text("Selesai") }
                else -> Button(onClick = { next() }) { Text("Lanjut") }
            }
        }
    }
}
