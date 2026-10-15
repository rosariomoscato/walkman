package com.walktape.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(private val profileId: Long, private val id: String) : ViewModel() {
    val playlist = Graph.database.browseDao().playlist(profileId, id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val entries = Pager(PagingConfig(pageSize = 80, enablePlaceholders = false)) {
        Graph.database.browseDao().entries(profileId, id)
    }.flow.cachedIn(viewModelScope)
    val playUnavailable = MutableStateFlow(false)
    val playBusy = MutableStateFlow(false)

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
        fun factory(profileId: Long, id: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaylistDetailViewModel(profileId, id) as T
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
        factory = PlaylistDetailViewModel.factory(profileId, id))
    val playlist by model.playlist.collectAsStateWithLifecycle()
    val busy by model.playBusy.collectAsStateWithLifecycle()
    val unavailable by model.playUnavailable.collectAsStateWithLifecycle()
    val entries = model.entries.collectAsLazyPagingItems()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(playlist?.name ?: stringResource(R.string.library_tab_playlists)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)) } })
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                playlist?.let { p ->
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoverArt(libraryModel, p.coverArt, 96.dp)
                        Text(stringResource(R.string.library_album_songs, p.songCount) + " • " + durationLabel(p.duration))
                        p.owner?.let { Text(stringResource(R.string.playlist_owner, it)) }
                        p.comment?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text(stringResource(R.string.playlist_read_only), style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = { model.play(0) }, enabled = !busy && p.songCount > 0) {
                            Text(stringResource(R.string.player_play_playlist))
                        }
                        if (unavailable) Text(stringResource(R.string.selection_queue_unavailable))
                    }
                }
            }
            items(count = entries.itemCount, key = entries.itemKey { it.position }) { index ->
                entries[index]?.let { entry ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { model.play(entry.position) }
                        .heightIn(min = 56.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text((entry.position + 1).toString())
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text(listOfNotNull(entry.artist, entry.album).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(durationLabel(entry.duration))
                    }
                }
            }
        }
    }
}
