package com.walktape.app.ui.screens.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walktape.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = viewModel()
) {
    val export by viewModel.export.collectAsStateWithLifecycle()
    val import by viewModel.import.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportDone = stringResource(R.string.backup_export_done)
    val exportFailed = stringResource(R.string.backup_error_write)

    // Terminal export outcomes surface as snackbars without launching during composition.
    androidx.compose.runtime.LaunchedEffect(export) {
        when (export) {
            BackupViewModel.ExportState.Done -> {
                snackbarHostState.showSnackbar(exportDone)
                viewModel.dismissExport()
            }
            BackupViewModel.ExportState.WriteError -> {
                snackbarHostState.showSnackbar(exportFailed)
                viewModel.dismissExport()
            }
            else -> Unit
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.writeExportTo(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.startImport(uri) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(R.string.backup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = viewModel::startExportPreview) {
                    Text(stringResource(R.string.backup_export))
                }
                TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Text(stringResource(R.string.backup_import))
                }
            }
            Text(
                text = stringResource(R.string.backup_relogin_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    when (val state = export) {
        is BackupViewModel.ExportState.Previewing -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissExport,
                title = { Text(stringResource(R.string.backup_preview_title)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.backup_preview_profiles, state.preview.profiles))
                    Text(stringResource(R.string.backup_preview_queues, state.preview.savedQueues))
                    Text(stringResource(R.string.backup_preview_downloads, state.preview.downloadItems))
                    CheckRow(
                        label = stringResource(R.string.backup_preview_preferences),
                        uncheckedLabel = stringResource(R.string.backup_preview_no_preferences),
                        checked = state.includePreferences,
                        onCheckedChange = viewModel::setIncludePreferences)
                } },
                confirmButton = {
                    TextButton(onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                            .format(Date(System.currentTimeMillis()))
                        exportLauncher.launch("walktape-backup-$stamp.json")
                    }) { Text(stringResource(R.string.backup_export)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissExport) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
        else -> Unit
    }

    when (val state = import) {
        is BackupViewModel.ImportState.Ready -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissImport,
                title = { Text(stringResource(R.string.backup_import_title)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.backup_preview_profiles, state.profiles))
                    Text(stringResource(R.string.backup_preview_queues, state.queues))
                    Text(stringResource(R.string.backup_preview_downloads, state.downloads))
                    if (state.backup.preferences != null) {
                        CheckRow(
                            label = stringResource(R.string.backup_import_apply_preferences),
                            uncheckedLabel = stringResource(R.string.backup_import_apply_preferences),
                            checked = state.applyPreferences,
                            onCheckedChange = viewModel::setApplyPreferences)
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.backup_import_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                } },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmImport) {
                        Text(stringResource(R.string.backup_import_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissImport) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
        is BackupViewModel.ImportState.Applied -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissImport,
                title = { Text(stringResource(R.string.backup_result_title)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.summary.results.forEach { result ->
                        Text(stringResource(if (result.created) R.string.backup_result_created
                            else R.string.backup_result_merged, result.displayName))
                        if (result.queueApplied) {
                            Text(stringResource(R.string.backup_result_queue),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.queueSkippedActive) {
                            Text(stringResource(R.string.backup_import_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (result.downloadsAdded > 0) {
                            Text(stringResource(R.string.backup_result_downloads_added, result.downloadsAdded),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (result.downloadsKept > 0) {
                            Text(stringResource(R.string.backup_result_downloads_kept, result.downloadsKept),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.summary.failed.forEach { name ->
                        Text(stringResource(R.string.backup_result_profile_error, name),
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (state.summary.preferencesApplied) {
                        Text(stringResource(R.string.backup_result_preferences),
                            style = MaterialTheme.typography.bodySmall)
                    }
                } },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissImport) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }
        is BackupViewModel.ImportState.Failed -> {
            val message = when (state.reason) {
                com.walktape.app.core.backup.BackupCodec.Reason.NOT_JSON,
                com.walktape.app.core.backup.BackupCodec.Reason.NOT_BACKUP ->
                    stringResource(R.string.backup_error_not_backup)
                com.walktape.app.core.backup.BackupCodec.Reason.UNSUPPORTED_VERSION ->
                    stringResource(R.string.backup_error_version)
                else -> stringResource(R.string.backup_error_invalid, state.reason.name)
            }
            AlertDialog(
                onDismissRequest = viewModel::dismissImport,
                title = { Text(stringResource(R.string.backup_import_title)) },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissImport) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }
        is BackupViewModel.ImportState.ReadError -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissImport,
                title = { Text(stringResource(R.string.backup_import_title)) },
                text = { Text(stringResource(R.string.backup_error_read)) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissImport) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }
        else -> Unit
    }
}

@Composable
private fun CheckRow(label: String, uncheckedLabel: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text = if (checked) label else uncheckedLabel,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp))
    }
}
