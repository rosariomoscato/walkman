package com.walktape.app.ui.screens.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walktape.app.R
import com.walktape.app.core.updates.UpdateManager
import com.walktape.app.ui.components.formatDownloadBytes
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory)
) {
    val check by viewModel.check.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val installPermission by viewModel.installPermission.collectAsStateWithLifecycle()
    val dateFormatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.update_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.update_current_version, viewModel.currentVersion),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(enabled = check !is UpdateViewModel.CheckState.Busy,
                    onClick = viewModel::checkNow) {
                    Text(stringResource(if (check is UpdateViewModel.CheckState.Busy)
                        R.string.update_checking else R.string.update_check))
                }
            }

            when (val state = check) {
                is UpdateViewModel.CheckState.Done -> when (val outcome = state.outcome) {
                    is UpdateManager.CheckOutcome.Available -> {
                        Text(stringResource(R.string.update_available, outcome.release.tagName),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp))
                        outcome.release.body?.takeIf { it.isNotBlank() }?.let { notes ->
                            Text(stringResource(R.string.update_notes_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp))
                            Text(notes,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        when (val dl = download) {
                            is UpdateViewModel.DownloadState.Running -> {
                                Text(stringResource(R.string.update_download_running,
                                    formatDownloadBytes(dl.bytes),
                                    if (dl.total > 0) formatDownloadBytes(dl.total) else "?"),
                                    modifier = Modifier.padding(horizontal = 16.dp))
                                if (dl.total > 0) {
                                    LinearProgressIndicator(
                                        progress = { (dl.bytes.toFloat() / dl.total).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                                }
                                TextButton(onClick = viewModel::cancelDownload, modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Text(stringResource(R.string.update_download_cancel))
                                }
                            }
                            is UpdateViewModel.DownloadState.Verified -> {
                                Text(stringResource(R.string.update_download_done),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp))
                                TextButton(onClick = { viewModel.install(dl.file) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Text(stringResource(R.string.update_install))
                                }
                                Text(stringResource(R.string.update_install_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            is UpdateViewModel.DownloadState.Rejected -> {
                                Text(rejectionLabel(dl.reason), color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp))
                                TextButton(onClick = viewModel::dismissDownload, modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Text(stringResource(R.string.action_close))
                                }
                            }
                            UpdateViewModel.DownloadState.Failed -> {
                                Text(stringResource(R.string.update_download_failed),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            UpdateViewModel.DownloadState.Cancelled -> Unit
                            UpdateViewModel.DownloadState.Idle -> {
                                val assets = outcome.release.assets
                                TextButton(
                                    enabled = com.walktape.app.core.updates.AssetSelector.selectApk(assets) != null,
                                    onClick = { viewModel.downloadApk(outcome.release) },
                                    modifier = Modifier.padding(horizontal = 16.dp)) {
                                    Text(stringResource(R.string.update_download))
                                }
                            }
                        }
                    }
                    UpdateManager.CheckOutcome.UpToDate ->
                        Text(stringResource(R.string.update_uptodate), modifier = Modifier.padding(horizontal = 16.dp))
                    UpdateManager.CheckOutcome.NoReleases ->
                        Text(stringResource(R.string.update_no_releases), modifier = Modifier.padding(horizontal = 16.dp))
                    UpdateManager.CheckOutcome.RateLimited ->
                        Text(stringResource(R.string.update_rate_limited), modifier = Modifier.padding(horizontal = 16.dp))
                    UpdateManager.CheckOutcome.Network ->
                        Text(stringResource(R.string.update_network_error), modifier = Modifier.padding(horizontal = 16.dp))
                    UpdateManager.CheckOutcome.Invalid ->
                        Text(stringResource(R.string.update_invalid_response), modifier = Modifier.padding(horizontal = 16.dp))
                    UpdateManager.CheckOutcome.BlockedOffline ->
                        Text(stringResource(R.string.update_blocked_offline), modifier = Modifier.padding(horizontal = 16.dp))
                    is UpdateManager.CheckOutcome.UnknownVersion ->
                        Text(stringResource(R.string.update_invalid_response), modifier = Modifier.padding(horizontal = 16.dp))
                }
                else -> Unit
            }

            if (installPermission) {
                Text(stringResource(R.string.update_install_permission),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(onClick = viewModel::dismissInstallPermission, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.action_close))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = settings.updateCheckEnabled, role = Role.Switch,
                        onValueChange = viewModel::setAutoCheck)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_auto), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.update_auto_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.updateCheckEnabled, onCheckedChange = null)
            }

            val lastCheck = if (settings.updateLastCheckAt > 0) {
                stringResource(R.string.update_last_check,
                    dateFormatter.format(Date(settings.updateLastCheckAt)))
            } else {
                stringResource(R.string.update_last_check_never)
            }
            val lastOutcome = when (settings.updateLastOutcome) {
                "available" -> null // Version shown only from a real check.
                "uptodate" -> stringResource(R.string.update_last_check_uptodate)
                "noreleases" -> stringResource(R.string.update_last_check_no_releases)
                "network", "ratelimit", "invalid", "unknown", "offline" ->
                    stringResource(R.string.update_last_check_error)
                else -> null
            }
            Text(lastCheck, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
            lastOutcome?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun rejectionLabel(reason: UpdateViewModel.DownloadState.Rejection): String = stringResource(
    when (reason) {
        UpdateViewModel.DownloadState.Rejection.NOT_WALKTAPE -> R.string.update_verify_failed_package
        UpdateViewModel.DownloadState.Rejection.NOT_NEWER -> R.string.update_verify_failed_version
        UpdateViewModel.DownloadState.Rejection.SIGNATURE -> R.string.update_verify_failed_signature
        UpdateViewModel.DownloadState.Rejection.CORRUPT -> R.string.update_verify_failed_corrupt
    }
)
