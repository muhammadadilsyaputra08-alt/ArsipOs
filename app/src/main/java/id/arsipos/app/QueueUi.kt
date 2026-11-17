@file:OptIn(ExperimentalMaterial3Api::class)

package id.arsipos.app

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val OK = androidx.compose.ui.graphics.Color(0xFF2E7D32)

@Composable
fun QueueScreen(vm: MainVM) {
    BackHandler(enabled = !vm.busy) { vm.queueOpen = false }
    var bulk by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.queueOpen = false }, enabled = !vm.busy) { Text("← Kembali") }
            Text(
                "Ada ${vm.queue.size} file baru dalam antrean untuk diarsipkan.",
                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall
            )
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { bulk = true }, enabled = !vm.busy && vm.queue.isNotEmpty()) { Text("Atur semua") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vm.queue, key = { it.id }) { q -> QueueCard(vm, q) }
        }
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.queueMsg?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            ProgressBar(vm)
            Button(
                onClick = { vm.saveQueue() },
                enabled = !vm.busy && vm.queue.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Simpan Semua ke Arsip") }
        }
    }
    vm.queueEditing?.let { QueueEditDialog(vm, it) }
    if (bulk) BulkDialog(vm) { bulk = false }
}

@Composable
private fun QueueCard(vm: MainVM, q: QueueItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumb(q)
            Column(Modifier.weight(1f)) {
                Text(q.name.ifBlank { q.origName }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf(formatSize(q.size), q.category, q.tags).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2
                )
                Text(
                    if (q.edited) "✔ Siap" else "● Belum diberi nama",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (q.edited) OK else MaterialTheme.colorScheme.tertiary
                )
            }
            Column {
                TextButton(onClick = { vm.queueEditing = q }, enabled = !vm.busy) { Text("Edit") }
                TextButton(onClick = { vm.deleteQueue(q) }, enabled = !vm.busy) { Text("Hapus") }
            }
        }
    }
}

@Composable
private fun Thumb(q: QueueItem) {
    val bmp by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, q.path) {
        if (q.mime.startsWith("image/")) {
            value = withContext(Dispatchers.IO) {
                try {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(q.path, o)
                    var s = 1
                    while (o.outWidth / s > 256 || o.outHeight / s > 256) s *= 2
                    BitmapFactory.decodeFile(q.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(if (q.mime == "application/pdf") "📕" else if (q.mime.startsWith("image/")) "🖼" else "📄")
    }
}

@Composable
fun QueueEditDialog(vm: MainVM, q: QueueItem) {
    var name by remember(q.id) { mutableStateOf(q.name) }
    var cat by remember(q.id) { mutableStateOf(q.category) }
    var tags by remember(q.id) { mutableStateOf(q.tags) }
    AlertDialog(
        onDismissRequest = { vm.queueEditing = null },
        title = { Text("Edit item antrean") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama arsip") }, modifier = Modifier.fillMaxWidth())
                CategoryPicker(vm, cat) { cat = it }
                TagField(tags) { tags = it }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                vm.updateQueue(q.copy(name = name.trim(), category = cat, tags = normTags(tags), edited = true))
                vm.queueEditing = null
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = { vm.queueEditing = null }) { Text("Batal") } }
    )
}

@Composable
private fun BulkDialog(vm: MainVM, onClose: () -> Unit) {
    var cat by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Atur semua item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kosongkan untuk tidak mengubah.", style = MaterialTheme.typography.bodySmall)
                CategoryPicker(vm, cat) { cat = it }
                TagField(tags) { tags = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.applyToAll(cat.ifBlank { null }, tags.ifBlank { null })
                onClose()
            }) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}
