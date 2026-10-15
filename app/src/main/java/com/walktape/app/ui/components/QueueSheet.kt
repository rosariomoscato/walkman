package com.walktape.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.playback.PlaybackQueueEntry
import java.util.Locale

/**
 * Editable queue: tap to play, remove, reorder through the row menu or accessibility actions.
 * Shuffle keeps the current track and, when switched off, restores the order saved before
 * shuffling. The same list content is embedded in the expanded player pane. The queue can be
 * named (mixtape label) and saved as a server playlist in the order actually shown.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun QueueSheet(onDismiss: () -> Unit) {
    val state by Graph.playback.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        QueueListContent(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), maxListHeight = 420.dp)
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun QueueListContent(modifier: Modifier = Modifier, maxListHeight: Dp? = 420.dp) {
    val state by Graph.playback.state.collectAsStateWithLifecycle()
    var namingOpen by remember { mutableStateOf(false) }
    var savingOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val profiles by Graph.profiles.profiles.collectAsStateWithLifecycle(emptyList())
    val owner = profiles.firstOrNull { it.isActive }?.id
    LaunchedEffect(owner) { namingOpen = false; savingOpen = false }
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    Column(modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.queue_title), style = MaterialTheme.typography.titleLarge)
                state.mixtapeName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.queue_count, state.queue.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp))
            val shuffleLabel = stringResource(if (state.shuffleOn) R.string.queue_shuffle_off else R.string.queue_shuffle_on)
            IconButton(onClick = Graph.playback::toggleShuffle, enabled = state.canShuffle,
                modifier = Modifier.size(48.dp).semantics { contentDescription = shuffleLabel }) {
                Icon(Icons.Default.Shuffle, contentDescription = null,
                    tint = if (state.shuffleOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.queue.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                TextButton(onClick = { namingOpen = true }, enabled = owner != null && state.profileId == owner) { Text(stringResource(R.string.mixtape_name)) }
                TextButton(onClick = { savingOpen = true }, enabled = owner != null && state.profileId == owner && state.canEditQueue) { Text(stringResource(R.string.queue_save_playlist)) }
            }
        }
        if (state.shuffleOn) Text(stringResource(R.string.queue_reorder_hint),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        if (state.queue.isEmpty()) {
            Text(stringResource(R.string.queue_empty), Modifier.padding(vertical = 24.dp))
        } else {
            val listModifier = if (maxListHeight != null) Modifier.fillMaxWidth().heightIn(max = maxListHeight)
            else Modifier.fillMaxWidth()
            LazyColumn(listModifier) {
                itemsIndexed(state.queue, key = { index, entry ->
                    if (entry.entryId.startsWith(com.walktape.app.playback.QueueIdentity.PREFIX)) entry.entryId
                    else "pending:$index:${entry.entryId}"
                }) { index, entry ->
                    QueueRow(
                        entry = entry,
                        enabled = state.canEditQueue,
                        isFirst = index == 0,
                        isLast = index == state.queue.lastIndex,
                        onClick = { Graph.playback.playQueueEntry(entry.entryId) },
                        onRemove = { Graph.playback.removeQueueItem(entry.entryId) },
                        onMove = { steps ->
                            val from = state.queue.indexOfFirst { it.entryId == entry.entryId }
                            if (from >= 0) Graph.playback.moveQueueItem(entry.entryId,
                                state.queue[(from + steps).coerceIn(0, state.queue.lastIndex)].entryId)
                        },
                    )
                }
            }
        }
    }
    if (namingOpen) {
        MixtapeNameDialog(current = state.mixtapeName ?: "", onDismiss = { namingOpen = false }) { name ->
            namingOpen = false
            val capturedProfile = owner
            scope.launch {
                if (capturedProfile != null && Graph.database.profileDao().active()?.id == capturedProfile &&
                    Graph.playback.state.value.profileId == capturedProfile)
                    Graph.queueNaming.value = name?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
    }
    if (savingOpen && state.queue.isNotEmpty()) {
        SaveQueueDialog(defaultName = state.mixtapeName ?: "", onDismiss = { savingOpen = false }) { name ->
            savingOpen = false
            val songIds = state.queue.map { it.mediaId }
            val capturedProfile = owner ?: return@SaveQueueDialog
            scope.launch {
                val profile = Graph.database.profileDao().active() ?: return@launch
                if (profile.id != capturedProfile) return@launch
                val error = Graph.playlistRepository().create(profile.id, name, songIds)
                val message = if (error == null) resources.getString(R.string.queue_saved_as_playlist, name)
                    else resources.getString(R.string.queue_save_failed)
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun MixtapeNameDialog(current: String, onDismiss: () -> Unit, onApply: (String?) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mixtape_name)) },
        text = {
            androidx.compose.foundation.layout.Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.mixtape_name_hint)) },
                    modifier = Modifier.fillMaxWidth())
                if (current.isNotBlank()) TextButton(onClick = { onApply(null) }) {
                    Text(stringResource(R.string.mixtape_name_clear))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(name) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Saves the queue, in the order shown (the actual listening order with shuffle on). */
@Composable
private fun SaveQueueDialog(defaultName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.queue_save_playlist)) },
        text = {
            Column {
                Text(stringResource(R.string.queue_save_order))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.playlist_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun QueueRow(
    entry: PlaybackQueueEntry,
    enabled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    /** Move by a relative number of positions. */
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val move by rememberUpdatedState(onMove)
    val moveUpLabel = stringResource(R.string.queue_move_up)
    val moveDownLabel = stringResource(R.string.queue_move_down)
    val removeLabel = stringResource(R.string.queue_remove)
    val currentLabel = stringResource(R.string.queue_current)
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("queue-row-${entry.entryId.replace('/', '_')}")
            .heightIn(min = 56.dp)
            .semantics {
                if (entry.isCurrent) stateDescription = currentLabel
                val actions = mutableListOf<CustomAccessibilityAction>()
                if (enabled) {
                    if (!isFirst) actions.add(CustomAccessibilityAction(moveUpLabel) { move(-1); true })
                    if (!isLast) actions.add(CustomAccessibilityAction(moveDownLabel) { move(1); true })
                    actions.add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                }
                customActions = actions
            }
            .let { modifier -> if (enabled) modifier.clickable(onClick = onClick) else modifier },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.queue_row_actions))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(moveUpLabel) }, enabled = !isFirst,
                    onClick = { menuOpen = false; move(-1) })
                DropdownMenuItem(text = { Text(moveDownLabel) }, enabled = !isLast,
                    onClick = { menuOpen = false; move(1) })
                DropdownMenuItem(text = { Text(removeLabel) }, onClick = { menuOpen = false; onRemove() })
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                entry.title.ifBlank { stringResource(R.string.playback_unknown_title) },
                style = if (entry.isCurrent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                color = if (entry.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (entry.artist.isNotBlank()) {
                Text(entry.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        entry.durationMs?.let { Text(timeLabel(it), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = removeLabel)
        }
    }
}
