@file:OptIn(ExperimentalMaterial3Api::class)

package id.arsipos.app

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

fun canBiometric(ctx: Context): Boolean =
    BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

fun showBiometric(act: FragmentActivity, title: String, onOk: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(act)
    val prompt = BiometricPrompt(act, executor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onOk()
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText("Pakai PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
fun PinField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        modifier = modifier
    )
}

private fun pinError(r: PinResult): String? = when (r) {
    is PinResult.Ok -> null
    is PinResult.Wrong ->
        if (r.lockedMs > 0) "PIN salah berulang kali. Terkunci ${fmtLock(r.lockedMs)}." else "PIN salah."
    is PinResult.Locked -> "Terkunci. Coba lagi dalam ${fmtLock(r.ms)}."
}

/** Layar kunci aplikasi (PIN Utama + biometrik opsional). */
@Composable
fun LockScreen(vm: MainVM) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val bio = vm.biometricOn && canBiometric(ctx)

    fun submit(p: String) {
        val r = vm.unlock(p)
        if (r !is PinResult.Ok) {
            err = pinError(r)
            pin = ""
        }
    }

    LaunchedEffect(Unit) {
        if (bio) ctx.findActivity()?.let { showBiometric(it, "Buka ArsipOS") { vm.unlockBio() } }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒 ArsipOS terkunci", style = MaterialTheme.typography.headlineSmall)
        Text("Masukkan PIN Utama (6 digit)", Modifier.padding(top = 8.dp, bottom = 16.dp))
        PinField(pin, { v -> pin = v; if (v.length == 6) submit(v) }, "PIN Utama")
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = { submit(pin) }, enabled = pin.length == 6, modifier = Modifier.padding(top = 16.dp)) {
            Text("Buka")
        }
        if (bio) {
            OutlinedButton(
                onClick = { ctx.findActivity()?.let { showBiometric(it, "Buka ArsipOS") { vm.unlockBio() } } },
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("Gunakan biometrik") }
        }
    }
}

/** Dialog PIN generik untuk PIN Utama (gerbang Pengaturan) maupun PIN Keamanan Aksi. */
@Composable
fun PinDialog(
    title: String,
    subtitle: String? = null,
    verify: (String) -> PinResult,
    onOk: () -> Unit,
    onCancel: () -> Unit,
    onBiometric: (() -> Unit)? = null,
    onForgot: (() -> Unit)? = null
) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    fun submit(p: String) {
        val r = verify(p)
        if (r is PinResult.Ok) {
            onOk()
        } else {
            err = pinError(r)
            pin = ""
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subtitle != null) Text(subtitle)
                PinField(pin, { v -> pin = v; if (v.length == 6) submit(v) }, "PIN (6 digit)")
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (onBiometric != null) TextButton(onClick = onBiometric) { Text("Gunakan biometrik") }
                if (onForgot != null) TextButton(onClick = onForgot) { Text("Lupa PIN Keamanan?") }
            }
        },
        confirmButton = { Button(onClick = { submit(pin) }, enabled = pin.length == 6) { Text("Konfirmasi") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Batal") } }
    )
}

/** Dialog PIN Keamanan Aksi untuk setiap aksi kritis; menjalankan aksi setelah PIN benar. */
@Composable
fun ActionPinHost(vm: MainVM) {
    val req = vm.pinReq ?: return
    var forgot by remember { mutableStateOf(false) }
    if (forgot) {
        RecoveryDialog(vm) { forgot = false }
    } else {
        PinDialog(
            title = "PIN Keamanan Aksi",
            subtitle = req.title,
            verify = { vm.verifyMaster(it) },
            onOk = { vm.pinReq = null; req.action() },
            onCancel = { vm.pinReq = null },
            onForgot = { forgot = true }
        )
    }
}

/** Pemulihan PIN Keamanan Aksi via OTP 6 digit yang dikirim ke channel arsip. */
@Composable
fun RecoveryDialog(vm: MainVM, onClose: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    var otp by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Pulihkan PIN Keamanan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stage == 0) {
                    Text("Kode OTP 6 digit (berlaku 3 menit) akan dikirim ke channel arsip Telegram Anda.")
                } else {
                    Text("Masukkan kode OTP dari channel, lalu buat PIN Keamanan baru.")
                    PinField(otp, { otp = it }, "Kode OTP")
                    PinField(p1, { p1 = it }, "PIN Keamanan baru")
                    PinField(p2, { p2 = it }, "Ulangi PIN baru")
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !vm.busy,
                onClick = {
                    if (stage == 0) {
                        vm.sendOtp { e -> if (e == null) { stage = 1; err = null } else err = e }
                    } else {
                        val e = vm.resetMasterWithOtp(otp, p1, p2)
                        if (e == null) {
                            vm.message = "PIN Keamanan diperbarui"
                            vm.pinReq = null
                            onClose()
                        } else err = e
                    }
                }
            ) { Text(if (stage == 0) "Kirim OTP" else "Simpan PIN") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}

/** Dialog untuk membuat PIN baru (6 digit, diulang). onSubmit mengembalikan pesan error atau null. */
@Composable
fun NewPinDialog(title: String, onSubmit: (String) -> String?, onClose: () -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(p1, { p1 = it }, "PIN baru (6 digit)")
                PinField(p2, { p2 = it }, "Ulangi PIN baru")
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (p1 != p2) err = "Kedua PIN tidak sama"
                else {
                    val e = onSubmit(p1)
                    if (e == null) onClose() else err = e
                }
            }, enabled = p1.length == 6) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}
