@file:OptIn(ExperimentalMaterial3Api::class)

package id.arsipos.app

import android.os.Bundle
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import java.io.File

class MainActivity : FragmentActivity() {
    private val vm by lazy { ViewModelProvider(this)[MainVM::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArsipTheme { Root(vm) } }
    }

    override fun onStart() {
        super.onStart()
        vm.onForegrounded()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackgrounded()
    }
}

@Composable
fun Root(vm: MainVM) {
    val ctx = LocalContext.current
    var crash by remember {
        mutableStateOf(
            try {
                File(ctx.filesDir, "crash.txt").takeIf { it.exists() }?.readText()
            } catch (e: Exception) {
                null
            }
        )
    }
    LaunchedEffect(Unit) {
        try { File(ctx.filesDir, "launching").delete() } catch (_: Exception) {}
    }
    // Pratinjau aplikasi terbaru disembunyikan saat terkunci, atau selalu jika dipilih di Pengaturan.
    val hide = vm.secureScreen || vm.locked
    LaunchedEffect(hide) {
        ctx.findActivity()?.window?.let {
            if (hide) it.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else it.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    val c = crash
    if (c != null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Aplikasi crash sebelumnya. Salin teks di bawah dan kirim ke pengembang.", style = MaterialTheme.typography.titleSmall)
            Box(Modifier.weight(1f).padding(vertical = 8.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(c.take(6000), style = MaterialTheme.typography.bodySmall) }
            }
            Button(onClick = {
                File(ctx.filesDir, "crash.txt").delete()
                crash = null
            }) { Text("Hapus laporan & lanjut") }
        }
        return
    }

    when {
        !vm.setupDone || vm.wizardOpen -> WizardScreen(vm)
        vm.locked -> LockScreen(vm)
        else -> MainScreen(vm)
    }
    ActionPinHost(vm)
}

@Composable
fun MainScreen(vm: MainVM) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var showNote by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var showHybrid by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.pick(u)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.loadRestore(u)
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.message = null }
    }
    LaunchedEffect(vm.toOpen) {
        vm.toOpen?.let { (f, mime) ->
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try { ctx.startActivity(i) } catch (e: Exception) { vm.message = "Tidak ada aplikasi untuk membuka file ini" }
            vm.toOpen = null
        }
    }
    LaunchedEffect(vm.toLink) {
        vm.toLink?.let {
            if (!openUrl(ctx, it)) vm.message = "Tidak bisa membuka Telegram"
            vm.toLink = null
        }
    }
    LaunchedEffect(tab) {
        if (tab == 0) vm.settingsUnlocked = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(44.dp))
                        Column {
                            Text("ArsipOS", fontWeight = FontWeight.SemiBold)
                            ConnLine(vm.conn)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E3A8A),
                    titleContentColor = Color.White
                )
            )
        },
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
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = { showNote = true }) { Icon(Icons.Default.Edit, "Catat teks") }
                    SmallFloatingActionButton(onClick = { showLink = true }) { Text("🔗") }
                    FloatingActionButton(onClick = {
                        picker.launch(
                            arrayOf(
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-powerpoint",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                "application/zip",
                                "application/vnd.rar",
                                "application/x-rar-compressed",
                                "image/*"
                            )
                        )
                    }) { Icon(Icons.Default.Add, "Unggah") }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) {
                ArchiveScreen(vm)
            } else if (vm.settingsUnlocked) {
                SettingsScreen(vm) { restorePicker.launch(arrayOf("*/*")) }
            } else {
                SettingsGate(vm) { tab = 0 }
            }
            Box(Modifier.align(Alignment.TopCenter)) { ProgressBar(vm) }
        }
    }

    vm.draft?.let { DraftDialog(vm, it) }
    vm.oversize?.let { OversizeDialog(vm, it) { showHybrid = true } }
    if (showHybrid) HybridDialog(vm) { showHybrid = false }
    if (showNote) NoteDialog(vm) { showNote = false }
    if (showLink) LinkDialog(vm) { showLink = false }
    vm.detail?.let { DetailDialog(vm, it) }
    vm.editing?.let { EditDialog(vm, it) }
    vm.deleting?.let { DeleteDialog(vm, it) }
    vm.restorePending?.let { RestoreDialog(vm, it) }
}

@Composable
fun SettingsGate(vm: MainVM, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val bio = vm.biometricOn && canBiometric(ctx)
    PinDialog(
        title = "PIN Utama",
        subtitle = "Masukkan PIN Utama untuk membuka Pengaturan",
        verify = { vm.verifyApp(it) },
        onOk = { vm.settingsUnlocked = true },
        onCancel = onCancel,
        onBiometric = if (bio) {
            { ctx.findActivity()?.let { showBiometric(it, "Buka Pengaturan") { vm.settingsUnlocked = true } } }
        } else null
    )
}

@Composable
fun ConnLine(s: ConnState) {
    val green = Color(0xFF86EFAC)
    val red = Color(0xFFFCA5A5)
    val gray = Color(0xFFCBD5E1)
    when (s) {
        is ConnState.Ok -> Text("● Terhubung ke: ${s.title}", color = green, style = MaterialTheme.typography.labelSmall)
        is ConnState.Bad -> Text("● ${s.msg}", color = red, style = MaterialTheme.typography.labelSmall, maxLines = 2)
        is ConnState.Offline -> Text("● Offline (data lokal tetap bisa dicari)", color = gray, style = MaterialTheme.typography.labelSmall)
        is ConnState.Checking -> Text("● Memeriksa koneksi…", color = gray, style = MaterialTheme.typography.labelSmall)
        is ConnState.None -> Text("● Belum terhubung ke Telegram", color = red, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun RestoreDialog(vm: MainVM, rp: RestorePending) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!vm.busy) vm.restorePending = null },
        title = { Text("Pulihkan backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Gabungkan: hanya menambah data yang belum ada.\n" +
                        "Timpa semua: menghapus seluruh data lokal, lalu menggantinya dengan isi backup termasuk PIN dari backup.\n" +
                        "Anda akan diminta PIN Keamanan Aksi."
                )
                if (rp.encrypted) {
                    OutlinedTextField(
                        pw, { pw = it }, label = { Text("Kata sandi backup") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                }
                ProgressBar(vm)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !vm.busy && (!rp.encrypted || pw.isNotEmpty()),
                onClick = { vm.guard("Pulihkan backup") { vm.performRestore(false, pw) } }
            ) { Text("Gabungkan") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { vm.restorePending = null }, enabled = !vm.busy) { Text("Batal") }
                TextButton(
                    enabled = !vm.busy && (!rp.encrypted || pw.isNotEmpty()),
                    onClick = { vm.guard("Timpa semua data dengan backup") { vm.performRestore(true, pw) } }
                ) { Text("Timpa semua") }
            }
        }
    )
}
