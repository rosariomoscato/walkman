package com.walktape.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.db.DownloadStatus
import com.walktape.app.core.downloads.toSongEntity
import kotlinx.coroutines.launch
import java.util.Locale

/** Formats bytes with binary units; used for honest on-disk accounting in the UI. */
fun formatDownloadBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.2f GB", bytes / 1073741824.0)
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    bytes >= 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(profileId: Long, onDismiss: () -> Unit) {
    val flow = remember(profileId) { Graph.downloadStore.downloads(profileId) }
    val downloads by flow.collectAsStateWithLifecycle(emptyList())
    val groupFlow = remember(profileId) { Graph.database.downloadDao().groups(profileId) }
    val groups by groupFlow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(stringResource(R.string.download_manage), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            Graph.downloadStore.pauseAll(profileId)
                            Graph.downloadEngine.pauseWork(profileId)
                        }
                    }) { Text(stringResource(R.string.download_pause_all)) }
                    TextButton(onClick = {
                        scope.launch {
                            Graph.downloadStore.resumeAll(profileId)
                            Graph.downloadEngine.schedule(profileId)
                        }
                    }) { Text(stringResource(R.string.download_resume_all)) }
                }
                if (downloads.isEmpty()) Text(stringResource(R.string.download_empty),
                    style = MaterialTheme.typography.bodyMedium)
            }
            items(groups, key = { "group:${it.origin}" }) { group ->
                val ids = remember(group.songIds) {
                    runCatching { com.google.gson.Gson().fromJson(group.songIds, Array<String>::class.java).toList() }
                        .getOrDefault(emptyList())
                }
                val files = downloads.filter { it.status == DownloadStatus.COMPLETED.name }.associateBy { it.songId }
                Column {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    if (group.serverMissing) Text(stringResource(R.string.download_removed_from_server))
                    FlowRow {
                        TextButton(enabled = ids.isNotEmpty() && ids.all { it in files }, onClick = {
                            Graph.playback.playQueue(ids.map { files.getValue(it).toSongEntity() }, 0,
                                queueOrigin = group.name)
                        }) { Text(stringResource(R.string.player_play_playlist)) }
                        TextButton(onClick = { scope.launch {
                            Graph.downloadStore.removePlaylistDownload(profileId,
                                group.origin.removePrefix(com.walktape.app.core.downloads.DownloadStore.ORIGIN_PLAYLIST_PREFIX))
                        } }) { Text(stringResource(R.string.download_remove)) }
                    }
                }
            }
            items(downloads, key = { it.songId }) { row ->
                DownloadRow(row, onPause = {
                    scope.launch {
                        Graph.downloadStore.pause(profileId, row.songId)
                    }
                }, onResume = {
                    scope.launch {
                        Graph.downloadStore.resume(profileId, row.songId)
                        Graph.downloadEngine.schedule(profileId)
                    }
                }, onRemove = {
                    scope.launch { Graph.downloadStore.remove(profileId, row.songId) }
                })
            }
        }
    }
}

@Composable
private fun DownloadRow(row: DownloadEntity, onPause: () -> Unit, onResume: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        row.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
        if (row.serverMissing) Text(stringResource(R.string.download_removed_from_server),
            style = MaterialTheme.typography.bodySmall)
        when (row.status) {
            DownloadStatus.COMPLETED.name -> Text(stringResource(R.string.download_completed) +
                " • " + formatDownloadBytes(row.bytes), style = MaterialTheme.typography.bodySmall)
            DownloadStatus.RUNNING.name -> {
                LinearProgressIndicator(progress = { progressOf(row) },
                    modifier = Modifier.fillMaxWidth())
                Text(progressLabel(row), style = MaterialTheme.typography.bodySmall)
            }
            DownloadStatus.QUEUED.name -> Text(stringResource(R.string.download_queued),
                style = MaterialTheme.typography.bodySmall)
            DownloadStatus.PAUSED.name -> Text(stringResource(R.string.download_paused),
                style = MaterialTheme.typography.bodySmall)
            DownloadStatus.FAILED.name -> Text(
                stringResource(errorLabel(row.lastError)), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Row {
            if (row.status == DownloadStatus.COMPLETED.name) {
                IconButton(onClick = { Graph.playback.playQueue(listOf(row.toSongEntity()), 0) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.selection_play_now))
                }
            }
            when (row.status) {
                DownloadStatus.RUNNING.name, DownloadStatus.QUEUED.name ->
                    IconButton(onClick = onPause, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.download_pause))
                    }
                DownloadStatus.PAUSED.name, DownloadStatus.FAILED.name ->
                    IconButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.download_resume))
                    }
            }
            IconButton(onClick = onRemove, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.download_remove))
            }
        }
    }
}

private fun progressOf(row: DownloadEntity): Float =
    if (row.totalBytes > 0) (row.bytes.toFloat() / row.totalBytes).coerceIn(0f, 1f) else 0f

@Composable
private fun progressLabel(row: DownloadEntity): String =
    if (row.totalBytes > 0) stringResource(R.string.download_progress_bytes,
        formatDownloadBytes(row.bytes), formatDownloadBytes(row.totalBytes))
    else stringResource(R.string.download_progress_unknown, formatDownloadBytes(row.bytes))

@Composable
private fun errorLabel(error: String?): Int = when (error) {
    "NETWORK" -> R.string.download_error_NETWORK
    "SPACE" -> R.string.download_error_SPACE
    "NOT_FOUND" -> R.string.download_error_NOT_FOUND
    "FORBIDDEN" -> R.string.download_error_FORBIDDEN
    "CANCELLED" -> R.string.download_error_CANCELLED
    "INVALID" -> R.string.download_error_INVALID
    else -> R.string.download_error_NETWORK
}

/** Status label reused by the Home section rows. */
@Composable
fun downloadStatusLabel(status: String): Int = when (status) {
    DownloadStatus.RUNNING.name -> R.string.download_running
    DownloadStatus.QUEUED.name -> R.string.download_queued
    DownloadStatus.PAUSED.name -> R.string.download_paused
    DownloadStatus.FAILED.name -> R.string.download_failed
    else -> R.string.download_completed
}
