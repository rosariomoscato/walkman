package com.walktape.app.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.walktape.app.Graph
import com.walktape.app.R
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val profileId: Long,
    private val id: String,
    private val username: String = "",
    private val onDeleted: () -> Unit = {},
) : ViewModel() {
    val playlist = Graph.database.browseDao().playlist(profileId, id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val entries = Pager(PagingConfig(pageSize = 80, enablePlaceholders = false)) {
        Graph.database.browseDao().entries(profileId, id)
    }.flow.cachedIn(viewModelScope)
    val playUnavailable = MutableStateFlow(false)
    val playBusy = MutableStateFlow(false)
    val editBusy = MutableStateFlow(false)
    val editError = MutableStateFlow(false)
    val downloadBusy = MutableStateFlow(false)
    val downloadNotice = MutableStateFlow<Int?>(null)
    val conflictResolutionError = MutableStateFlow(false)

    /** Conflicting queue operations for THIS playlist, for the explicit resolution dialog. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val conflictOps = Graph.offlineOps.ops(profileId).map { ops ->
        ops.filter { it.state == com.walktape.app.core.db.PendingOpState.CONFLICT.name &&
            Graph.offlineOps.conflictPlaylistId(it) == id }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun resolveConflict(opId: String, choice: String) {
        viewModelScope.launch {
            val done = when (choice) {
                "local" -> Graph.offlineOps.resolveConflictKeepLocal(profileId, opId)
                "new" -> Graph.offlineOps.resolveConflictAsNewPlaylist(profileId, opId)
                else -> Graph.offlineOps.resolveConflictKeepServer(profileId, opId)
            }
            conflictResolutionError.value = !done
        }
    }

    /** Downloads the whole playlist (distinct songs, one audio file each, order kept in cache). */
    fun download() {
        if (downloadBusy.value) return
        downloadBusy.value = true
        viewModelScope.launch {
            try {
                Graph.downloadStore.enqueuePlaylist(profileId, id)
                Graph.downloadEngine.schedule(profileId)
            } finally { downloadBusy.value = false }
        }
    }

    /**
     * Manual update of a downloaded playlist (W08-T05): refreshes the playlist from the
     * server first, then mirrors the download set; files kept for other origins are
     * reported honestly instead of being deleted silently.
     */
    fun updateDownload() {
        if (downloadBusy.value) return
        if (Graph.networkGate.offlineOnly(profileId)) {
            downloadNotice.value = com.walktape.app.R.string.download_sync_blocked
            return
        }
        downloadBusy.value = true
        viewModelScope.launch {
            try {
                if (Graph.database.profileDao().active()?.id != profileId) return@launch
                val client = Graph.profiles.clientFor(profileId) ?: return@launch
                val error = Graph.playlistIndex().refresh(profileId, client)
                if (error != null) {
                    downloadNotice.value = com.walktape.app.R.string.playlist_change_failed
                    return@launch
                }
                val result = Graph.downloadStore.reconcilePlaylistDownload(profileId, id)
                Graph.downloadEngine.schedule(profileId)
                downloadNotice.value = when {
                    result.keptForOtherOrigins.isNotEmpty() -> com.walktape.app.R.string.download_keep_notice
                    else -> null
                }
            } finally { downloadBusy.value = false }
        }
    }

    /** Server-side write permission: own playlists only, never shared/readonly ones. */
    fun editable(owner: String?, readOnly: Boolean): Boolean =
        !readOnly && owner != null && owner == username

    private fun runEdit(action: suspend () -> com.walktape.app.core.net.ApiError?) {
        if (editBusy.value) return
        val source = playlist.value ?: return
        if (!editable(source.owner, source.readOnly)) return
        editBusy.value = true
        viewModelScope.launch {
            try {
                if (Graph.database.profileDao().active()?.id != profileId) return@launch
                if (action() != null) editError.value = true
            } finally { editBusy.value = false }
        }
    }

    fun rename(newName: String) {
        val name = newName.trim()
        if (name.isEmpty()) return
        runEdit { Graph.playlistRepository().rename(profileId, id, name) }
    }

    fun delete() = runEdit {
        val error = Graph.playlistRepository().delete(profileId, id)
        if (error == null) onDeleted()
        error
    }

    fun removeEntry(position: Int) = runEdit {
        Graph.playlistRepository().removeEntry(profileId, id, position)
    }

    fun moveEntry(from: Int, to: Int) {
        if (from == to) return
        runEdit {
            val rows = Graph.database.browseDao().queueEntries(profileId, id)
            if (to !in rows.indices) return@runEdit com.walktape.app.core.net.ApiError.InvalidRequest
            val ordered = rows.toMutableList().apply { add(to, removeAt(from)) }
            Graph.playlistRepository().reorder(profileId, id, ordered.map { it.songId })
        }
    }

    fun play(position: Int) {
        if (playBusy.value) return
        playBusy.value = true
        viewModelScope.launch {
            try {
                val source = playlist.value ?: return@launch
                val rows = Graph.database.browseDao().queueEntries(profileId, id)
                val songs = rows.map { it.songId }.distinct().chunked(500).flatMap {
                    Graph.database.libraryDao().songsByIds(profileId, it)
                }.associateBy { it.id }
                if (rows.size != source.songCount || rows.isEmpty() || rows.any { it.songId !in songs }) {
                    playUnavailable.value = true
                    return@launch
                }
                if (Graph.database.profileDao().active()?.id != profileId) return@launch
                playUnavailable.value = false
                Graph.playback.playQueue(rows.map { songs.getValue(it.songId) }, position,
                    queueOrigin = source.name)
            } finally { playBusy.value = false }
        }
    }

    companion object {
        fun factory(profileId: Long, id: String, username: String, onDeleted: () -> Unit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlaylistDetailViewModel(profileId, id, username, onDeleted) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(id: String, onBack: () -> Unit,
                         libraryModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)) {
    val profile by libraryModel.activeProfile.collectAsStateWithLifecycle()
    val profileId = profile?.id ?: return
    val model: PlaylistDetailViewModel = viewModel(key = "playlist:$profileId:$id",
        factory = PlaylistDetailViewModel.factory(profileId, id, profile?.username ?: "", onBack))
    val playlist by model.playlist.collectAsStateWithLifecycle()
    val busy by model.playBusy.collectAsStateWithLifecycle()
    val editBusy by model.editBusy.collectAsStateWithLifecycle()
    val editError by model.editError.collectAsStateWithLifecycle()
    val unavailable by model.playUnavailable.collectAsStateWithLifecycle()
    val downloadBusy by model.downloadBusy.collectAsStateWithLifecycle()
    val downloadNotice by model.downloadNotice.collectAsStateWithLifecycle()
    val conflictOps by model.conflictOps.collectAsStateWithLifecycle()
    val conflictError by model.conflictResolutionError.collectAsStateWithLifecycle()
    val pendingSync = playlist?.pendingSync == true
    val entries = model.entries.collectAsLazyPagingItems()
    val editable = playlist?.let { model.editable(it.owner, it.readOnly) } ?: false
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(playlist?.name ?: stringResource(R.string.library_tab_playlists)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)) } },
            actions = {
                if (editable) {
                    IconButton(onClick = { renameOpen = true }, enabled = !editBusy) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.playlist_rename))
                    }
                    IconButton(onClick = { deleteOpen = true }, enabled = !editBusy) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.playlist_delete))
                    }
                }
            })
        if (editError) MutationErrorLine(true) { model.editError.value = false }
        if (conflictError) MutationErrorLine(true) { model.conflictResolutionError.value = false }
        if (pendingSync) {
            Text(stringResource(R.string.download_pending_edits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                playlist?.let { p ->
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoverArt(libraryModel, p.coverArt, 96.dp)
                        Text(stringResource(R.string.library_album_songs, p.songCount) + " • " + durationLabel(p.duration))
                        if (!editable) {
                            p.owner?.let { Text(stringResource(R.string.playlist_shared_read_only, it),
                                style = MaterialTheme.typography.bodySmall) }
                        } else {
                            p.owner?.let { Text(stringResource(R.string.playlist_owner, it)) }
                        }
                        p.comment?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        FilledTonalButton(onClick = { model.play(0) }, enabled = !busy && p.songCount > 0) {
                            Text(stringResource(R.string.player_play_playlist))
                        }
                        FilledTonalButton(onClick = { model.download() }, enabled = !downloadBusy) {
                            Text(stringResource(R.string.download_playlist))
                        }
                        FilledTonalButton(onClick = { model.updateDownload() }, enabled = !downloadBusy) {
                            Text(stringResource(R.string.download_update_playlist))
                        }
                        downloadNotice?.let { notice ->
                            Text(stringResource(notice), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (unavailable) Text(stringResource(R.string.selection_queue_unavailable))
                        if (editBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            items(count = entries.itemCount, key = entries.itemKey { it.position }) { index ->
                entries[index]?.let { entry ->
                    PlaylistEntryRow(
                        position = entry.position,
                        title = entry.title,
                        subtitle = listOfNotNull(entry.artist, entry.album).joinToString(" • "),
                        duration = durationLabel(entry.duration),
                        editable = editable && !editBusy,
                        isFirst = entry.position == 0,
                        isLast = entry.position == entries.itemCount - 1,
                        onClick = { model.play(entry.position) },
                        onRemove = { model.removeEntry(entry.position) },
                        onMove = { steps ->
                            model.moveEntry(entry.position, (entry.position + steps))
                        },
                    )
                }
            }
        }
    }
    if (renameOpen && playlist != null) {
        var name by remember(playlist?.changed) { mutableStateOf(playlist?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(R.string.playlist_rename)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name_hint)) },
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { renameOpen = false; model.rename(name) },
                    enabled = name.isNotBlank() && !editBusy) {
                    Text(stringResource(R.string.playlist_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (deleteOpen && playlist != null) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.playlist_delete)) },
            text = { Text(stringResource(R.string.playlist_delete_confirm, playlist?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = { deleteOpen = false; model.delete() }, enabled = !editBusy) {
                    Text(stringResource(R.string.playlist_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    conflictOps.firstOrNull()?.let { op ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.download_sync_conflict)) },
            text = {
                Column {
                    Text(stringResource(R.string.download_conflict_body, playlist?.name ?: ""))
                }
            },
            confirmButton = {
                TextButton(onClick = { model.resolveConflict(op.opId, "local") }) {
                    Text(stringResource(R.string.download_conflict_keep_local))
                }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { model.resolveConflict(op.opId, "new") }) {
                        Text(stringResource(R.string.download_conflict_new_playlist))
                    }
                    TextButton(onClick = { model.resolveConflict(op.opId, "server") }) {
                        Text(stringResource(R.string.download_conflict_keep_server))
                    }
                }
            },
        )
    }
}

@Composable
private fun PlaylistEntryRow(
    position: Int,
    title: String,
    subtitle: String,
    duration: String,
    editable: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val moveUpLabel = stringResource(R.string.queue_move_up)
    val moveDownLabel = stringResource(R.string.queue_move_down)
    val removeLabel = stringResource(R.string.playlist_remove_song)
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .semantics {
                val actions = mutableListOf<CustomAccessibilityAction>()
                if (editable) {
                    if (!isFirst) actions.add(CustomAccessibilityAction(moveUpLabel) { onMove(-1); true })
                    if (!isLast) actions.add(CustomAccessibilityAction(moveDownLabel) { onMove(1); true })
                    actions.add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                }
                customActions = actions
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editable) {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.queue_row_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(moveUpLabel) }, enabled = !isFirst,
                        onClick = { menuOpen = false; onMove(-1) })
                    DropdownMenuItem(text = { Text(moveDownLabel) }, enabled = !isLast,
                        onClick = { menuOpen = false; onMove(1) })
                    DropdownMenuItem(text = { Text(removeLabel) },
                        onClick = { menuOpen = false; onRemove() })
                }
            }
        }
        Text((position + 1).toString())
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text(duration)
    }
}
