package com.walktape.app.ui.screens.diagnostics

import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walktape.app.R
import com.walktape.app.core.diagnostics.DiagnosticsCollector
import com.walktape.app.ui.components.MechanicalTextKey
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModel.Factory)
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val included by viewModel.included.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val exportSubject = stringResource(R.string.diagnostics_export_subject)
    val copiedMessage = stringResource(R.string.diagnostics_copied)
    val exportedMessage = stringResource(R.string.diagnostics_exported)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun shareFile(text: String) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "walktape-diagnostics.txt")
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "com.walktape.app.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, exportSubject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, exportSubject))
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val text = report
            if (text != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
                scope.launch { snackbarHostState.showSnackbar(exportedMessage) }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(R.string.diagnostics_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.diagnostics_included_sections),
                style = MaterialTheme.typography.titleMedium
            )
            sections.sortedBy { it.id.ordinal }.forEach { section ->
                val checked = section.id in included
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = checked, role = Role.Switch,
                            onValueChange = { viewModel.toggle(section.id) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sectionLabel(section.id), style = MaterialTheme.typography.bodyMedium)
                        if (section.id == DiagnosticsCollector.SectionId.CONNECTIONS && checked) {
                            Text(
                                text = stringResource(R.string.diagnostics_connections_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Switch(checked = checked, onCheckedChange = null)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MechanicalTextKey(
                    text = stringResource(R.string.diagnostics_copy),
                    onClick = {
                        report?.let {
                            clipboard.setText(AnnotatedString(it))
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        }
                    }
                )
                MechanicalTextKey(
                    text = stringResource(R.string.diagnostics_export_file),
                    onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                            .format(Date(System.currentTimeMillis()))
                        exportLauncher.launch("walktape-diagnostics-$stamp.txt")
                    }
                )
                MechanicalTextKey(
                    text = stringResource(R.string.diagnostics_share),
                    emphasized = true,
                    onClick = { report?.let(::shareFile) }
                )
            }

            Text(
                text = report ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun sectionLabel(id: DiagnosticsCollector.SectionId): String = stringResource(
    when (id) {
        DiagnosticsCollector.SectionId.APP -> R.string.diagnostics_section_app
        DiagnosticsCollector.SectionId.DEVICE -> R.string.diagnostics_section_device
        DiagnosticsCollector.SectionId.STORAGE -> R.string.diagnostics_section_storage
        DiagnosticsCollector.SectionId.LIBRARY -> R.string.diagnostics_section_library
        DiagnosticsCollector.SectionId.DOWNLOADS -> R.string.diagnostics_section_downloads
        DiagnosticsCollector.SectionId.SYNC -> R.string.diagnostics_section_sync
        DiagnosticsCollector.SectionId.PLAYER -> R.string.diagnostics_section_player
        DiagnosticsCollector.SectionId.CONNECTIONS -> R.string.diagnostics_section_connections
        DiagnosticsCollector.SectionId.PREFERENCES -> R.string.diagnostics_section_preferences
    }
)
