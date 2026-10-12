package com.walktape.app.ui.screens.profiles

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.walktape.app.R
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ProtocolErrorKind
import com.walktape.app.core.profiles.QrCodec
import com.walktape.app.core.profiles.QrPayload

@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel = viewModel(factory = ProfilesViewModel.Factory)) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val removal by viewModel.removal.collectAsStateWithLifecycle()
    val share by viewModel.share.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    // Credentials never enter saved state, SavedStateHandle, preferences or diagnostics.
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var http by remember { mutableStateOf(false) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { contents ->
            QrCodec.decode(contents)?.let { payload ->
                if (payload.name.isNotEmpty()) name = payload.name
                url = payload.url
                if (payload.username.isNotEmpty()) username = payload.username
                adding = true
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.profiles_title), style = MaterialTheme.typography.headlineMedium)
        if (action.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        action.error?.let { Text(stringResource(errorLabel(it)), color = MaterialTheme.colorScheme.error) }
        if (action.success) Text(stringResource(R.string.connection_ok))
        profiles.forEach { profile ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(profile.serverUrl)
                    Text(stringResource(if (profile.serverUrl.startsWith("https:")) R.string.connection_https else R.string.connection_http))
                    if (profile.isActive) Text(stringResource(R.string.profile_active))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!profile.isActive) TextButton(enabled = !action.busy, onClick = { viewModel.activate(profile.id) }) {
                            Text(stringResource(R.string.profile_activate))
                        }
                        TextButton(enabled = !action.busy, onClick = { viewModel.verify(profile.id) }) { Text(stringResource(R.string.connection_verify)) }
                        TextButton(enabled = !action.busy, onClick = { viewModel.shareQr(profile) }) { Text(stringResource(R.string.profile_share_qr)) }
                        TextButton(enabled = !action.busy, onClick = { viewModel.confirmRemoval(profile) }) { Text(stringResource(R.string.profile_remove)) }
                    }
                }
            }
        }
        if (!adding) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !action.busy, onClick = { viewModel.reset(); adding = true }) {
                Text(stringResource(R.string.profile_add))
            }
            OutlinedButton(enabled = !action.busy, onClick = {
                scanLauncher.launch(ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt(null)
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                })
            }) {
                Text(stringResource(R.string.profile_scan_qr))
            }
        } else {
            Text(stringResource(R.string.profile_setup_help))
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true, enabled = !action.busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.profile_url)) }, singleLine = true, enabled = !action.busy, modifier = Modifier.fillMaxWidth())
            Row {
                Checkbox(http, { http = it }, enabled = !action.busy)
                Text(stringResource(R.string.profile_http_opt_in), modifier = Modifier.padding(top = 12.dp))
            }
            if (http) Text(stringResource(R.string.profile_http_help))
            OutlinedTextField(username, { username = it }, label = { Text(stringResource(R.string.profile_username)) }, singleLine = true, enabled = !action.busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.profile_password)) }, singleLine = true, enabled = !action.busy, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(enabled = !action.busy && name.isNotBlank() && url.isNotBlank() && username.isNotBlank() && password.isNotEmpty(), onClick = {
                viewModel.add(name, url, username, password, http) {
                    password = ""; username = ""; url = ""; name = ""; http = false; adding = false
                }
            }) { Text(stringResource(R.string.profile_verify_save)) }
            TextButton(enabled = !action.busy, onClick = { password = ""; adding = false; viewModel.reset() }) { Text(stringResource(R.string.action_cancel)) }
        }
    }
    removal?.let { (profile, bytes) ->
        AlertDialog(onDismissRequest = viewModel::cancelRemoval,
            title = { Text(stringResource(R.string.profile_remove)) },
            text = { Text(stringResource(R.string.profile_remove_confirm, profile.name, bytes)) },
            confirmButton = { TextButton(enabled = !action.busy, onClick = viewModel::remove) { Text(stringResource(R.string.profile_remove)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRemoval) { Text(stringResource(R.string.action_cancel)) } })
    }
    share?.let { profile ->
        val qr = remember(profile.id) {
            generateQrBitmap(QrCodec.encode(QrPayload(profile.name, profile.serverUrl, profile.username)), 720)
        }
        AlertDialog(onDismissRequest = viewModel::dismissShare,
            title = { Text(stringResource(R.string.profile_share_qr)) },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr != null) {
                    Image(bitmap = qr.asImageBitmap(), contentDescription = stringResource(R.string.profile_share_qr),
                        modifier = Modifier.size(280.dp))
                } else {
                    Text(stringResource(R.string.error_qr_generation))
                }
                Text(stringResource(R.string.profile_share_qr_body), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = viewModel::dismissShare) { Text(stringResource(R.string.action_close)) } })
    }
}

/** Pure zxing-core generation: the QR is rendered locally, no network or external service. */
private fun generateQrBitmap(content: String, size: Int): Bitmap? = try {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { index ->
        if (matrix.get(index % size, index / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
} catch (_: Exception) { null }

private fun errorLabel(error: ApiError): Int = when (error) {
    ApiError.InvalidRequest -> R.string.error_configuration
    ApiError.CredentialsUnavailable -> R.string.error_credentials_storage
    ApiError.LocalStorage -> R.string.error_local_storage
    ApiError.Unreachable, ApiError.Network -> R.string.error_network
    ApiError.Timeout -> R.string.error_timeout
    ApiError.Tls -> R.string.error_tls
    ApiError.InvalidResponse -> R.string.error_response
    is ApiError.Http -> R.string.error_http
    is ApiError.Protocol -> when (error.kind) {
        ProtocolErrorKind.AUTHENTICATION -> R.string.error_auth
        ProtocolErrorKind.FORBIDDEN -> R.string.error_permission
        ProtocolErrorKind.AUTH_UNSUPPORTED, ProtocolErrorKind.AUTH_CONFLICT -> R.string.error_auth_method
        ProtocolErrorKind.CLIENT_VERSION, ProtocolErrorKind.SERVER_VERSION -> R.string.error_version
        else -> R.string.error_protocol
    }
}
