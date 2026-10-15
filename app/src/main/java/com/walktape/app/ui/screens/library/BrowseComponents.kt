package com.walktape.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.walktape.app.R
import com.walktape.app.core.db.BrowseResult
import com.walktape.app.core.db.PlaylistEntity

@Composable
fun LibraryFilters(model: LibraryViewModel) {
    val filter by model.filter.collectAsStateWithLifecycle()
    val genres by model.genres.collectAsStateWithLifecycle()
    val years by model.years.collectAsStateWithLifecycle()
    var genreMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            TextButton(onClick = { genreMenu = true }) { Text(filter.genre ?: stringResource(R.string.filter_genre)) }
            DropdownMenu(genreMenu, { genreMenu = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.filter_all)) }, onClick = {
                    model.setFilter(null, filter.year, filter.offlineOnly); genreMenu = false
                })
                genres.forEach { genre -> DropdownMenuItem(text = { Text(genre) }, onClick = {
                    model.setFilter(genre, filter.year, filter.offlineOnly); genreMenu = false
                }) }
            }
        }
        Box {
            TextButton(onClick = { yearMenu = true }) { Text(filter.year?.toString() ?: stringResource(R.string.filter_year)) }
            DropdownMenu(yearMenu, { yearMenu = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                DropdownMenuItem(text = { Text(stringResource(R.string.filter_all)) }, onClick = {
                    model.setFilter(filter.genre, null, filter.offlineOnly); yearMenu = false
                })
                years.forEach { year -> DropdownMenuItem(text = { Text(year.toString()) }, onClick = {
                    model.setFilter(filter.genre, year, filter.offlineOnly); yearMenu = false
                }) }
            }
        }
        FilterChip(selected = filter.offlineOnly, onClick = {
            model.setFilter(filter.genre, filter.year, !filter.offlineOnly)
        }, label = { Text(stringResource(R.string.filter_offline)) })
    }
}

@Composable
fun PlaylistItems(items: LazyPagingItems<Any>, profileId: Long, model: LibraryViewModel, onOpen: (String) -> Unit) {
    val busy by model.playlistBusy.collectAsStateWithLifecycle()
    val selected by model.selection.collectAsStateWithLifecycle()
    var newDialogOpen by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(onClick = { newDialogOpen = true }, enabled = !busy,
                modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.playlist_new))
            }
        }
        items(count = items.itemCount, key = items.itemKey(::libraryItemKey)) { index ->
            (items[index] as? PlaylistEntity)?.takeIf { it.profileId == profileId }?.let { playlist ->
                val selectionLabel = stringResource(R.string.download_select_playlist, playlist.name)
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(checked = playlist.id in selected,
                        onCheckedChange = { model.toggleSelection(playlist.id) },
                        modifier = Modifier.semantics { contentDescription = selectionLabel })
                    Box(Modifier.weight(1f)) { PlaylistRow(playlist) { onOpen(playlist.id) } }
                }
            }
        }
    }
    if (newDialogOpen) {
        NewPlaylistDialog(busy, onDismiss = { newDialogOpen = false }, onCreate = { name ->
            newDialogOpen = false
            model.createPlaylist(name)
        })
    }
}

@Composable
fun PlaylistRow(playlist: PlaylistEntity, onOpen: () -> Unit) {
    TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.library_album_songs, playlist.songCount) + " • " + durationLabel(playlist.duration), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SearchItems(items: LazyPagingItems<Any>, profileId: Long, onAlbum: (String) -> Unit, onArtist: (String) -> Unit,
                onGenre: (String) -> Unit, onPlaylist: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(count = items.itemCount, key = items.itemKey(::libraryItemKey)) { index ->
            (items[index] as? BrowseResult)?.takeIf { it.profileId == profileId }?.let { result ->
                val label = when (result.kind) {
                    "ALBUM" -> R.string.library_tab_albums
                    "ARTIST" -> R.string.library_tab_artists
                    "SONG" -> R.string.library_tab_songs
                    "GENRE" -> R.string.library_tab_genres
                    else -> R.string.library_tab_playlists
                }
                val contents: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(result.title, style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(stringResource(label), result.subtitle).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (result.kind == "SONG") contents() else TextButton(onClick = {
                    when (result.kind) {
                        "ALBUM" -> onAlbum(result.id)
                        "ARTIST" -> onArtist(result.id)
                        "GENRE" -> onGenre(result.title)
                        "PLAYLIST" -> onPlaylist(result.id)
                    }
                }, modifier = Modifier.fillMaxWidth()) { contents() }
            }
        }
    }
}
