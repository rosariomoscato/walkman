package com.walktape.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.core.db.PendingOpState
import com.walktape.app.sync.OfflineOps
import kotlinx.coroutines.launch

/** Every durable edit stays inspectable, including rejected operations and deleted playlists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineEditsSheet(profileId: Long, onDismiss: () -> Unit) {
    val flow = remember(profileId) { Graph.offlineOps.ops(profileId) }
    val ops by flow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.download_pending_edits), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { Graph.offlineOps.triggerSync(profileId) }) {
                    Text(stringResource(R.string.download_sync_now))
                }
                if (failure) Text(stringResource(R.string.playlist_change_failed), color = MaterialTheme.colorScheme.error)
                if (ops.isEmpty()) Text(stringResource(R.string.offline_edits_empty))
            }
            items(ops, key = { it.opId }) { op ->
                Column {
                    Text(stringResource(if (op.kind == OfflineOps.KIND_STAR) R.string.home_favorites
                        else R.string.library_tab_playlists), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(when (op.state) {
                        PendingOpState.CONFLICT.name -> R.string.download_sync_conflict
                        PendingOpState.FAILED.name -> R.string.offline_edit_rejected
                        PendingOpState.SYNCING.name -> R.string.offline_edit_syncing
                        else -> R.string.download_queued
                    }))
                    if (op.state == PendingOpState.FAILED.name) TextButton(onClick = {
                        scope.launch {
                            Graph.database.downloadDao().upsertOp(op.copy(state = PendingOpState.PENDING.name))
                            Graph.offlineOps.triggerSync(profileId)
                        }
                    }) { Text(stringResource(R.string.action_retry)) }
                    if (op.state == PendingOpState.CONFLICT.name) {
                        TextButton(onClick = { scope.launch {
                            failure = !Graph.offlineOps.resolveConflictKeepLocal(profileId, op.opId)
                        } }) { Text(stringResource(R.string.download_conflict_keep_local)) }
                        TextButton(onClick = { scope.launch {
                            failure = !Graph.offlineOps.resolveConflictAsNewPlaylist(profileId, op.opId)
                        } }) { Text(stringResource(R.string.download_conflict_new_playlist)) }
                        TextButton(onClick = { scope.launch {
                            failure = !Graph.offlineOps.resolveConflictKeepServer(profileId, op.opId)
                        } }) { Text(stringResource(R.string.download_conflict_keep_server)) }
                    }
                }
            }
        }
    }
}
