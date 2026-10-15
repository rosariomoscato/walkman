package com.walktape.app.ui.screens.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walktape.app.R
import com.walktape.app.ui.components.MechanicalTextKey
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    viewModel: DiagnosticsViewModel = viewModel(factory = DiagnosticsViewModel.Factory)
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val exportSubject = stringResource(R.string.diagnostics_export_subject)
    val exportChooserTitle = stringResource(R.string.diagnostics_export)
    val copiedMessage = stringResource(R.string.diagnostics_copied)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                text = stringResource(R.string.diagnostics_export),
                emphasized = true,
                onClick = {
                    report?.let { text ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, exportSubject)
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, exportChooserTitle)
                        )
                    }
                }
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
