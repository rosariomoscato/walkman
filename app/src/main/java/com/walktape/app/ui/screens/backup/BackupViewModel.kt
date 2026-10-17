package com.walktape.app.ui.screens.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.walktape.app.Graph
import com.walktape.app.core.backup.BackupCodec
import com.walktape.app.core.backup.BackupFile
import com.walktape.app.core.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * W11-T03: export/import flow with explicit previews before either direction touches
 * anything. The export writes only after the user confirms the contents; the import
 * parses and validates the whole file before applying, and reports per-profile results.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class BackupViewModel : ViewModel() {

    private val manager = Graph.backupManager

    sealed interface ExportState {
        data object Idle : ExportState
        data object Busy : ExportState
        /** Contents shown before writing; the user can still exclude preferences. */
        data class Previewing(val preview: BackupManager.BuildPreview, val includePreferences: Boolean = true) : ExportState
        data object Done : ExportState
        data object WriteError : ExportState
    }

    private val mutableExport = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = mutableExport.asStateFlow()

    sealed interface ImportState {
        data object Idle : ImportState
        data object Busy : ImportState
        data class Ready(val backup: BackupFile, val profiles: Int, val queues: Int,
                         val downloads: Int, val applyPreferences: Boolean = true) : ImportState
        data class Applied(val summary: BackupManager.ImportSummary) : ImportState
        data class Failed(val reason: BackupCodec.Reason) : ImportState
        data object ReadError : ImportState
    }

    private val mutableImport = MutableStateFlow<ImportState>(ImportState.Idle)
    val import: StateFlow<ImportState> = mutableImport.asStateFlow()

    fun startExportPreview() {
        if (mutableExport.value is ExportState.Busy) return
        viewModelScope.launch {
            mutableExport.value = ExportState.Busy
            mutableExport.value = ExportState.Previewing(manager.preview())
        }
    }

    fun setIncludePreferences(include: Boolean) {
        val current = mutableExport.value
        if (current is ExportState.Previewing) {
            mutableExport.value = current.copy(includePreferences = include)
        }
    }

    fun dismissExport() { mutableExport.value = ExportState.Idle }

    /** SAF destination chosen: build the backup and stream it into the user's file. */
    fun writeExportTo(uri: Uri) {
        val current = mutableExport.value
        if (current !is ExportState.Previewing) return
        viewModelScope.launch {
            mutableExport.value = ExportState.Busy
            val done = withContext(Dispatchers.IO) {
                try {
                    val json = BackupCodec.write(manager.build(current.includePreferences))
                    Graph.app().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } != null
                } catch (_: Exception) {
                    false
                }
            }
            mutableExport.value = if (done) ExportState.Done else ExportState.WriteError
        }
    }

    fun startImport(uri: Uri) {
        if (mutableImport.value is ImportState.Busy) return
        viewModelScope.launch {
            mutableImport.value = ImportState.Busy
            val parsed = withContext(Dispatchers.IO) {
                try {
                    val text = Graph.app().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    if (text == null) null else BackupCodec.parse(text)
                } catch (_: Exception) {
                    null
                }
            }
            mutableImport.value = when (parsed) {
                null -> ImportState.ReadError
                is BackupCodec.Parsed.Invalid -> ImportState.Failed(parsed.reason)
                is BackupCodec.Parsed.Valid -> {
                    val backup = parsed.backup
                    ImportState.Ready(
                        backup = backup,
                        profiles = backup.profiles.size,
                        queues = backup.profiles.count { it.queue != null },
                        downloads = backup.profiles.sumOf { it.downloads.size },
                    )
                }
            }
        }
    }

    fun setApplyPreferences(apply: Boolean) {
        val current = mutableImport.value
        if (current is ImportState.Ready) {
            mutableImport.value = current.copy(applyPreferences = apply)
        }
    }

    fun confirmImport() {
        val current = mutableImport.value
        if (current !is ImportState.Ready) return
        viewModelScope.launch {
            mutableImport.value = ImportState.Busy
            val summary = withContext(Dispatchers.IO) {
                manager.import(current.backup, current.applyPreferences)
            }
            // Keep runtime cache limits in sync with the applied preferences.
            Graph.audioCache.updateLimit(Graph.currentSettings.audioCacheLimitBytes)
            mutableImport.value = ImportState.Applied(summary)
        }
    }

    fun dismissImport() { mutableImport.value = ImportState.Idle }
}
