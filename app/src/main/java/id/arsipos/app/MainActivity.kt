@file:OptIn(ExperimentalMaterial3Api::class)

package id.arsipos.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

@Composable
fun App(vm: MainVM = viewModel()) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.pick(u)
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    LaunchedEffect(vm.toOpen) {
        vm.toOpen?.let { (f, mime) ->
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { ctx.startActivity(i) } catch (e: Exception) { vm.message = "Tidak ada aplikasi untuk membuka file ini" }
            vm.toOpen = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopAppBar(title = { Text("ArsipOS") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) }, label = { Text("Arsip") }
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Pengaturan") }
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = {
                    picker.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "image/*"
                        )
                    )
                }) { Icon(Icons.Default.Add, "Unggah") }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) ArchiveScreen(vm) else SettingsScreen(vm)
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }

    vm.draft?.let { DraftDialog(vm, it) }
}

@Composable
fun ArchiveScreen(vm: MainVM) {
    var del by remember { mutableStateOf<Doc?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it; vm.refresh() },
            modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp),
            placeholder = { Text("Cari nomor, perihal, instansi, isi…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = vm.typeFilter == null,
                    onClick = { vm.typeFilter = null; vm.refresh() },
                    label = { Text("Semua (${vm.counts.values.sum()})") }
                )
            }
            items(TYPES) { t ->
                FilterChip(
                    selected = vm.typeFilter == t,
                    onClick = { vm.typeFilter = t; vm.refresh() },
                    label = { Text("$t (${vm.counts[t] ?: 0})") }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Picker("Tahun", vm.yearFilter?.toString() ?: "Semua", listOf("Semua") + vm.years.map { it.toString() }) {
                vm.yearFilter = it.toIntOrNull(); vm.refresh()
            }
            Picker("Bulan", vm.monthFilter?.let { MONTHS[it - 1] } ?: "Semua", listOf("Semua") + MONTHS) {
                val i = MONTHS.indexOf(it)
                vm.monthFilter = if (i < 0) null else i + 1; vm.refresh()
            }
            Picker("Status", vm.statusFilter ?: "Semua", listOf("Semua") + STATUSES) {
                vm.statusFilter = if (it == "Semua") null else it; vm.refresh()
            }
        }
        if (vm.docs.isEmpty()) {
            Text("Belum ada arsip. Tekan + untuk mengunggah.", Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.docs, key = { it.id }) { d ->
                    ListItem(
                        modifier = Modifier.clickable { vm.open(d) },
                        headlineContent = { Text(d.subject.ifBlank { d.fileName }, maxLines = 2) },
                        supportingContent = {
                            Text(
                                "${d.type} • ${d.number.ifBlank { "-" }} • ${d.date.ifBlank { "${d.year}-${"%02d".format(d.month)}" }}" +
                                    "\n${d.institution} • ${d.status}",
                                maxLines = 3
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { del = d }) { Icon(Icons.Default.Delete, "Hapus") }
                        }
                    )
                }
            }
        }
    }

    del?.let { d ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Hapus dari indeks?") },
            text = { Text("Metadata dihapus dari database lokal. File di Telegram tidak terhapus.") },
            confirmButton = { TextButton(onClick = { vm.delete(d); del = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Batal") } }
        )
    }
}

@Composable
fun Picker(label: String, current: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: $current") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false })
            }
        }
    }
}

@Composable
fun DraftDialog(vm: MainVM, d: Draft) {
    AlertDialog(
        onDismissRequest = { if (!vm.busy) vm.draft = null },
        title = { Text("Periksa metadata") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.name, style = MaterialTheme.typography.labelMedium)
                Picker("Jenis", d.type, TYPES) { vm.draft = d.copy(type = it) }
                OutlinedTextField(d.number, { vm.draft = d.copy(number = it) }, label = { Text("Nomor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.date, { vm.draft = d.copy(date = it) }, label = { Text("Tanggal (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.subject, { vm.draft = d.copy(subject = it) }, label = { Text("Perihal") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.institution, { vm.draft = d.copy(institution = it) }, label = { Text("Instansi") }, modifier = Modifier.fillMaxWidth())
                Picker("Status", d.status, STATUSES) { vm.draft = d.copy(status = it) }
            }
        },
        confirmButton = { Button(onClick = { vm.save() }, enabled = !vm.busy) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = { vm.draft = null }, enabled = !vm.busy) { Text("Batal") } }
    )
}

@Composable
fun SettingsScreen(vm: MainVM) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Telegram Bot sebagai penyimpanan file", style = MaterialTheme.typography.titleMedium)
        Text("Buat bot lewat @BotFather, tambahkan bot sebagai admin channel/grup arsip, lalu isi token dan chat ID (contoh: -100123456789).")
        OutlinedTextField(
            value = vm.token, onValueChange = { vm.token = it },
            label = { Text("Bot token") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = vm.chatId, onValueChange = { vm.chatId = it },
            label = { Text("Chat ID") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveSettings() }) { Text("Simpan") }
            OutlinedButton(onClick = { vm.testConnection() }) { Text("Tes koneksi") }
        }
        Text("Batas Bot API: unggah maks 50 MB, unduh langsung maks 20 MB (file lebih besar dikirim ulang ke chat).", style = MaterialTheme.typography.bodySmall)
    }
}
