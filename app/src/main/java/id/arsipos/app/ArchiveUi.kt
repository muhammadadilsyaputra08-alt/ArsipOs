@file:OptIn(ExperimentalMaterial3Api::class)

package id.arsipos.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun TagField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text("Tag (mis. keuangan 2026 penting)") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun CategoryPicker(vm: MainVM, current: String, onPick: (String) -> Unit) {
    Picker("Kategori", current.ifBlank { "-" }, listOf("-") + vm.categories.map { it.name }) {
        onPick(if (it == "-") "" else it)
    }
}

private fun viaTelegram(d: Doc) = d.kind == KIND_FILE && (d.fileId.isBlank() || d.size > Telegram.MAX_DOWNLOAD)

private fun describe(d: Doc): String {
    val l1 = when (d.kind) {
        KIND_FILE -> {
            val date = d.date.ifBlank { "${d.year}-${"%02d".format(d.month)}" }
            var s = "${d.type} • ${d.number.ifBlank { "-" }} • $date"
            if (d.size > 0) s += " • " + formatSize(d.size)
            if (viaTelegram(d)) s += " • Unduh via Telegram"
            s
        }
        KIND_NOTE -> "Catatan • ${d.date}"
        else -> "Tautan • ${d.institution.ifBlank { d.linkUrl }}"
    }
    val l2 = listOf(d.category, d.tags).filter { it.isNotBlank() }.joinToString(" • ")
    return if (l2.isEmpty()) l1 else "$l1\n$l2"
}

@Composable
fun ArchiveScreen(vm: MainVM) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = { vm.query = it; vm.refresh() },
            modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp),
            placeholder = { Text("Cari judul, nomor, tag, isi…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = vm.kindFilter == null,
                    onClick = { vm.kindFilter = null; vm.refresh() },
                    label = { Text("Semua (${vm.counts.values.sum()})") }
                )
            }
            items(KINDS) { (k, label) ->
                FilterChip(
                    selected = vm.kindFilter == k,
                    onClick = { vm.kindFilter = k; vm.refresh() },
                    label = { Text("$label (${vm.counts[k] ?: 0})") }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Picker("Kategori", vm.categoryFilter ?: "Semua", listOf("Semua") + vm.categories.map { it.name }) {
                vm.categoryFilter = if (it == "Semua") null else it; vm.refresh()
            }
            Picker("Tag", vm.tagFilter ?: "Semua", listOf("Semua") + vm.tags) {
                vm.tagFilter = if (it == "Semua") null else it; vm.refresh()
            }
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
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(painterResource(R.drawable.ic_empty_archive), null, Modifier.size(120.dp))
                Text("Belum ada arsip", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tekan + untuk mengunggah dokumen, atau tombol pensil dan tautan untuk menyimpan catatan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.docs, key = { it.id }) { d ->
                    val cat = vm.categories.firstOrNull { it.name == d.category }
                    ListItem(
                        modifier = Modifier.clickable { vm.openDetail(d) },
                        leadingContent = {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(cat?.let { Color(it.color).copy(alpha = 0.18f) } ?: MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) { Text(cat?.icon?.ifBlank { null } ?: kindIcon(d.kind)) }
                        },
                        headlineContent = { Text(d.subject.ifBlank { d.fileName }, maxLines = 2) },
                        supportingContent = { Text(describe(d), maxLines = 3) }
                    )
                }
            }
        }
    }
}

// ---------------- detail / edit / hapus ----------------

@Composable
fun DetailDialog(vm: MainVM, d: Doc) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { vm.detail = null },
        title = { Text(d.subject.ifBlank { d.fileName }, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${kindLabel(d.kind)}" + (if (d.category.isNotBlank()) " • ${d.category}" else "") +
                        (if (d.date.isNotBlank()) " • ${d.date}" else "")
                )
                if (d.tags.isNotBlank()) Text(d.tags, color = MaterialTheme.colorScheme.primary)
                if (d.kind == KIND_FILE) {
                    Text("Jenis: ${d.type}\nNomor: ${d.number.ifBlank { "-" }}\nInstansi: ${d.institution.ifBlank { "-" }}\nStatus: ${d.status}")
                    if (d.size > 0) Text("Ukuran: ${formatSize(d.size)}")
                    if (viaTelegram(d)) Text("Unduh via Telegram", color = MaterialTheme.colorScheme.primary)
                }
                if (d.kind == KIND_LINK) SelectionContainer { Text(d.linkUrl) }
                if (d.content.isNotBlank()) SelectionContainer { Text(d.content) }
                ProgressBar(vm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.requestEdit(d) }) { Text("Edit") }
                    OutlinedButton(onClick = { vm.deleting = d }) { Text("Hapus") }
                    if (d.kind != KIND_FILE) OutlinedButton(onClick = {
                        copyText(ctx, "arsip", if (d.kind == KIND_LINK) d.linkUrl else d.content)
                        vm.message = "Disalin"
                    }) { Text("Salin") }
                }
            }
        },
        confirmButton = {
            when (d.kind) {
                KIND_FILE -> Button(onClick = { vm.openFile(d) }, enabled = !vm.busy) {
                    Text(if (viaTelegram(d)) "Buka di Telegram" else "Buka / Unduh")
                }
                KIND_LINK -> Button(onClick = {
                    if (!openUrl(ctx, d.linkUrl)) vm.message = "Tidak bisa membuka link"
                }) { Text("Buka link") }
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = { vm.detail = null }) { Text("Tutup") } }
    )
}

@Composable
fun EditDialog(vm: MainVM, d: Doc) {
    var subject by remember { mutableStateOf(d.subject) }
    var category by remember { mutableStateOf(d.category) }
    var tags by remember { mutableStateOf(d.tags) }
    var type by remember { mutableStateOf(d.type) }
    var number by remember { mutableStateOf(d.number) }
    var date by remember { mutableStateOf(d.date) }
    var institution by remember { mutableStateOf(d.institution) }
    var status by remember { mutableStateOf(d.status) }
    var content by remember { mutableStateOf(d.content) }
    var url by remember { mutableStateOf(d.linkUrl) }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) vm.editing = null },
        title = { Text("Edit arsip") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text("Judul") }, modifier = Modifier.fillMaxWidth())
                CategoryPicker(vm, category) { category = it }
                TagField(tags) { tags = it }
                if (d.kind == KIND_FILE) {
                    Picker("Jenis", type, TYPES) { type = it }
                    OutlinedTextField(number, { number = it }, label = { Text("Nomor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it }, label = { Text("Tanggal (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(institution, { institution = it }, label = { Text("Instansi") }, modifier = Modifier.fillMaxWidth())
                    Picker("Status", status, STATUSES) { status = it }
                }
                if (d.kind == KIND_LINK) {
                    OutlinedTextField(url, { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                }
                if (d.kind != KIND_FILE) {
                    OutlinedTextField(
                        content, { content = it },
                        label = { Text(if (d.kind == KIND_NOTE) "Isi catatan" else "Catatan") },
                        minLines = 4, modifier = Modifier.fillMaxWidth()
                    )
                }
                ProgressBar(vm)
            }
        },
        confirmButton = {
            Button(
                enabled = subject.isNotBlank() && !vm.busy,
                onClick = {
                    vm.applyEdit(
                        d.copy(
                            subject = subject.trim(), category = category, tags = tags, type = type,
                            number = number.trim(), date = date.trim(), institution = institution.trim(),
                            status = status, content = content, linkUrl = url.trim()
                        )
                    )
                }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = { vm.editing = null }, enabled = !vm.busy) { Text("Batal") } }
    )
}

@Composable
fun DeleteDialog(vm: MainVM, d: Doc) {
    var alsoTg by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.deleting = null },
        title = { Text("Hapus arsip?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\"${d.subject.ifBlank { d.fileName }}\" akan dihapus dari indeks aplikasi. Anda akan diminta PIN Keamanan Aksi.")
                if (d.messageId > 0) {
                    Row(Modifier.clickable { alsoTg = !alsoTg }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(alsoTg, { alsoTg = it })
                        Text("Hapus juga pesan di channel Telegram")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.guard("Hapus arsip") { vm.performDelete(d, alsoTg && d.messageId > 0) }
            }) { Text("Hapus") }
        },
        dismissButton = { TextButton(onClick = { vm.deleting = null }) { Text("Batal") } }
    )
}

// ---------------- tambah arsip ----------------

@Composable
fun DraftDialog(vm: MainVM, d: Draft) {
    AlertDialog(
        onDismissRequest = { if (!vm.busy) vm.cancelDraft() },
        title = { Text("Periksa metadata") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${d.name} • ${formatSize(d.size)}", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(d.subject, { vm.draft = d.copy(subject = it) }, label = { Text("Judul / Perihal") }, modifier = Modifier.fillMaxWidth())
                CategoryPicker(vm, d.category) { vm.draft = d.copy(category = it) }
                TagField(d.tags) { vm.draft = d.copy(tags = it) }
                Picker("Jenis", d.type, TYPES) { vm.draft = d.copy(type = it) }
                OutlinedTextField(d.number, { vm.draft = d.copy(number = it) }, label = { Text("Nomor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.date, { vm.draft = d.copy(date = it) }, label = { Text("Tanggal (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(d.institution, { vm.draft = d.copy(institution = it) }, label = { Text("Instansi") }, modifier = Modifier.fillMaxWidth())
                Picker("Status", d.status, STATUSES) { vm.draft = d.copy(status = it) }
                ProgressBar(vm)
            }
        },
        confirmButton = { Button(onClick = { vm.save() }, enabled = !vm.busy) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = { vm.cancelDraft() }, enabled = !vm.busy) { Text("Batal") } }
    )
}

@Composable
fun NoteDialog(vm: MainVM, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var send by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text("Catat teks") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Judul (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("Isi: pengumuman, informasi") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                CategoryPicker(vm, category) { category = it }
                TagField(tags) { tags = it }
                Row(Modifier.clickable { send = !send }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(send, { send = it })
                    Text("Kirim juga ke Telegram")
                }
                ProgressBar(vm)
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.saveNote(title, body, category, tags, send, onDismiss) },
                enabled = body.isNotBlank() && !vm.busy
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Batal") } }
    )
}

@Composable
fun LinkDialog(vm: MainVM, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var send by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) onDismiss() },
        title = { Text("Simpan tautan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Judul (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Catatan deskripsi") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                CategoryPicker(vm, category) { category = it }
                TagField(tags) { tags = it }
                Row(Modifier.clickable { send = !send }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(send, { send = it })
                    Text("Kirim juga ke Telegram")
                }
                ProgressBar(vm)
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.saveLink(title, url, note, category, tags, send, onDismiss) },
                enabled = url.isNotBlank() && !vm.busy
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.busy) { Text("Batal") } }
    )
}

@Composable
fun OversizeDialog(vm: MainVM, o: Oversize, onHybrid: () -> Unit) {
    AlertDialog(
        onDismissRequest = { vm.oversize = null },
        title = { Text("File lebih dari 50 MB") },
        text = {
            Text(
                "${o.name} berukuran ${formatSize(o.size)}. Batas unggah Telegram Bot API adalah 50 MB, jadi file ini tidak dikirim.\n\n" +
                    "• Kompres otomatis: perkecil gambar/PDF di perangkat.\n" +
                    "• Mode Hybrid: unggah file lewat aplikasi Telegram (hingga 2 GB), lalu tautkan pesannya ke indeks ArsipOS."
            )
        },
        confirmButton = {
            Button(onClick = { vm.compressOversize() }, enabled = Compressor.supports(o.mime, o.name)) {
                Text("Kompres otomatis")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.oversize = null }) { Text("Batal") }
                TextButton(onClick = { vm.oversize = null; onHybrid() }) { Text("Mode Hybrid") }
            }
        }
    )
}

@Composable
fun HybridDialog(vm: MainVM, onDismiss: () -> Unit) {
    var link by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mode Hybrid: tautkan pesan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Unggah file langsung lewat aplikasi Telegram ke channel arsip.\n2. Tekan lama pesan tersebut > Copy Link.\n3. Tempel tautannya di bawah.")
                OutlinedTextField(link, { link = it }, label = { Text("Tautan pesan (t.me/c/...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(title, { title = it }, label = { Text("Judul") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(size, { size = it }, label = { Text("Ukuran file (MB, opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CategoryPicker(vm, category) { category = it }
                TagField(tags) { tags = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.saveHybrid(link, title, size, category, tags, onDismiss) },
                enabled = link.isNotBlank() && title.isNotBlank()
            ) { Text("Tautkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
