package com.walktape.app.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import com.walktape.app.R
import com.walktape.app.core.db.AlbumEntity
import com.walktape.app.core.db.ArtistEntity
import com.walktape.app.core.db.GenreEntity
import com.walktape.app.core.db.SongEntity
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.BrowseResult
import com.walktape.app.core.library.SyncProgress
import com.walktape.app.ui.components.EmptyState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun LibraryScreen(
    onOpenProfiles: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val state by viewModel.libraryState.collectAsStateWithLifecycle()
    val progress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val offlineMode by viewModel.offlineMode.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sorts by viewModel.sorts.collectAsStateWithLifecycle()
    val items = viewModel.pager.collectAsLazyPagingItems()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val queueBusy by viewModel.queueBusy.collectAsStateWithLifecycle()
    val queueError by viewModel.queueError.collectAsStateWithLifecycle()
    val playlistBusy by viewModel.playlistBusy.collectAsStateWithLifecycle()
    val playlistError by viewModel.playlistError.collectAsStateWithLifecycle()
    val downloadBusy by viewModel.downloadBusy.collectAsStateWithLifecycle()
    val favoriteError by viewModel.favoriteError.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var addDialogOpen by remember { mutableStateOf(false) }
    var pendingOpen by remember(profile?.id) { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        Text(
            text = stringResource(R.string.nav_home),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
        )
        when {
            profile == null -> NoProfileState(onOpenProfiles)
            else -> {
                if (offlineMode) Text(stringResource(R.string.download_offline_banner),
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
                SyncBanner(progress, state?.takeIf { it.profileId == profile?.id }?.let { it.artistCount to it.songCount }, viewModel::sync, viewModel::cancelSync)
                val pendingEdits by viewModel.pendingEdits.collectAsStateWithLifecycle()
                if (pendingEdits > 0) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.download_pending_edits_count, pendingEdits),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { pendingOpen = true }) {
                            Text(stringResource(R.string.offline_edits_manage))
                        }
                    }
                }
                if (favoriteError) {
                    Text(stringResource(R.string.favorite_failed),color = MaterialTheme.colorScheme.error,modifier = Modifier.padding(horizontal = 16.dp))
                    TextButton(onClick = viewModel::dismissFavoriteError) { Text(stringResource(R.string.action_cancel)) }
                }
                if (playlistError && selection.isEmpty()) MutationErrorLine(true, viewModel::dismissPlaylistError)
                SearchAndSort(
                    query = query,
                    onQuery = viewModel::setQuery,
                    tab = tab,
                    sort = sorts[tab] ?: tab.defaultSort,
                    onSort = { viewModel.setSort(tab, it) },
                )
                if (tab != LibraryTab.HOME) LibraryFilters(viewModel)
                ScrollableTabRow(selectedTabIndex = LibraryTab.entries.indexOf(tab), edgePadding = 16.dp,
                    modifier = Modifier.testTag("library-tabs")) {
                    LibraryTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry,
                            onClick = { viewModel.setTab(entry) },
                            text = { Text(stringResource(entry.label())) }
                        )
                    }
                }
                if (selection.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.library_selected, selection.size), modifier = Modifier.weight(1f))
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_clear_selection))
                            }
                        }
                        if (tab == LibraryTab.SONGS || tab == LibraryTab.ALBUMS) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.enqueueSelection(
                                    LibraryViewModel.QueueInsert.NOW, items.itemSnapshotList.items) }, enabled = !queueBusy) {
                                    Text(stringResource(R.string.selection_play_now))
                                }
                                TextButton(onClick = { viewModel.enqueueSelection(
                                    LibraryViewModel.QueueInsert.NEXT, items.itemSnapshotList.items) }, enabled = !queueBusy) {
                                    Text(stringResource(R.string.selection_play_next))
                                }
                                TextButton(onClick = { viewModel.enqueueSelection(
                                    LibraryViewModel.QueueInsert.APPEND, items.itemSnapshotList.items) }, enabled = !queueBusy) {
                                    Text(stringResource(R.string.selection_add_to_queue))
                                }
                                TextButton(onClick = { addDialogOpen = true }, enabled = !queueBusy && !playlistBusy) {
                                    Text(stringResource(R.string.selection_add_to_playlist))
                                }
                                TextButton(onClick = { viewModel.downloadSelection(items.itemSnapshotList.items) },
                                    enabled = !downloadBusy) {
                                    Text(stringResource(R.string.download_action))
                                }
                            }
                            if (queueBusy || playlistBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (queueError) Text(stringResource(R.string.selection_queue_unavailable),
                                color = MaterialTheme.colorScheme.error)
                            if (playlistError) MutationErrorLine(true, viewModel::dismissPlaylistError)
                        }
                        if (tab == LibraryTab.PLAYLISTS) {
                            TextButton(onClick = { viewModel.downloadSelection(items.itemSnapshotList.items) },
                                enabled = !downloadBusy) { Text(stringResource(R.string.download_action)) }
                        }
                    }
                }
                if (tab != LibraryTab.HOME && items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
                    Text(stringResource(R.string.library_no_results), modifier = Modifier.padding(16.dp))
                }
                key(profile?.id, tab, query, sorts[tab], filter) {
                    when (tab) {
                        LibraryTab.HOME -> HomeSections(viewModel, onOpenAlbum, onOpenArtist, onOpenPlaylist)
                        LibraryTab.ALBUMS -> AlbumItems(items, viewModel, onOpenAlbum)
                        LibraryTab.ARTISTS -> ArtistItems(items, viewModel, onOpenArtist)
                        LibraryTab.SONGS -> SongItems(items, viewModel)
                        LibraryTab.GENRES -> GenreItems(items, profile!!.id, onOpenGenre)
                        LibraryTab.PLAYLISTS -> PlaylistItems(items, profile!!.id, viewModel, onOpenPlaylist)
                        LibraryTab.SEARCH -> SearchItems(items, profile!!.id, onOpenAlbum, onOpenArtist, onOpenGenre, onOpenPlaylist)
                    }
                }
            }
        }
    }
    if (pendingOpen) profile?.id?.let { id ->
        com.walktape.app.ui.components.OfflineEditsSheet(id) { pendingOpen = false }
    }
    if (addDialogOpen) {
        AddToPlaylistDialog(
            model = viewModel,
            busy = playlistBusy,
            onChoose = { playlistId, newName ->
                addDialogOpen = false
                viewModel.addSelectionToPlaylist(playlistId, newName, items.itemSnapshotList.items)
            },
            onDismiss = { addDialogOpen = false },
        )
    }
}

@Composable
private fun NoProfileState(onOpenProfiles: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyState(
            icon = Icons.Filled.LibraryMusic,
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
        )
        TextButton(onClick = onOpenProfiles) { Text(stringResource(R.string.profiles_title)) }
    }
}

@Composable
private fun SyncBanner(
    progress: SyncProgress,
    counts: Pair<Int, Int>?,
    onSync: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            when {
                progress.running -> {
                    Text(stringResource(R.string.library_sync_running, progress.processed, progress.total))
                    LinearProgressIndicator(
                        progress = { if (progress.total > 0) progress.processed.toFloat() / progress.total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                }
                progress.error != null -> {
                    Text(stringResource(R.string.library_sync_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onSync) { Text(stringResource(R.string.action_retry)) }
                }
                counts != null -> {
                    Text(stringResource(R.string.library_sync_done, counts.first, counts.second),
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onSync) { Text(stringResource(R.string.library_sync_update)) }
                }
                else -> {
                    Text(stringResource(R.string.library_never_synced), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onSync) { Text(stringResource(R.string.library_sync_update)) }
                }
            }
        }
    }
}

@Composable
private fun SearchAndSort(
    query: String,
    onQuery: (String) -> Unit,
    tab: LibraryTab,
    sort: String,
    onSort: (String) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_clear_search))
                }
            },
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            modifier = Modifier.weight(1f)
        )
        if (tab == LibraryTab.ALBUMS || tab == LibraryTab.SONGS || tab == LibraryTab.PLAYLISTS) {
            TextButton(onClick = { sortMenu = true }) { Text(stringResource(sortLabel(tab, sort))) }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                sortOptions(tab).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sortLabel(tab, option))) },
                        onClick = { onSort(option); sortMenu = false }
                    )
                }
            }
        }
    }
}

private fun sortOptions(tab: LibraryTab): List<String> = when (tab) {
    LibraryTab.ALBUMS -> listOf("name", "artist", "year", "recent")
    LibraryTab.SONGS -> listOf("title", "artist", "album", "duration")
    LibraryTab.PLAYLISTS -> listOf("name", "changed")
    else -> listOf("name")
}

private fun sortLabel(tab: LibraryTab, sort: String): Int = when (tab) {
    LibraryTab.ALBUMS -> when (sort) {
        "artist" -> R.string.sort_album_artist
        "year" -> R.string.sort_album_year
        "recent" -> R.string.sort_album_recent
        else -> R.string.sort_album_name
    }
    LibraryTab.SONGS -> when (sort) {
        "artist" -> R.string.sort_song_artist
        "album" -> R.string.sort_song_album
        "duration" -> R.string.sort_song_duration
        else -> R.string.sort_song_title
    }
    LibraryTab.PLAYLISTS -> if (sort == "changed") R.string.sort_playlist_changed else R.string.sort_playlist_name
    else -> R.string.sort_album_name
}

private fun LibraryTab.label(): Int = when (this) {
    LibraryTab.HOME -> R.string.library_tab_home
    LibraryTab.ALBUMS -> R.string.library_tab_albums
    LibraryTab.ARTISTS -> R.string.library_tab_artists
    LibraryTab.SONGS -> R.string.library_tab_songs
    LibraryTab.GENRES -> R.string.library_tab_genres
    LibraryTab.PLAYLISTS -> R.string.library_tab_playlists
    LibraryTab.SEARCH -> R.string.library_tab_all
}

@Composable
private fun AlbumItems(items: LazyPagingItems<Any>, viewModel: LibraryViewModel, onOpen: (String) -> Unit) {
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), state = libraryListState(items)) {
        items(count = items.itemCount, key = items.itemKey(::libraryItemKey)) { index ->
            (items[index] as? AlbumEntity)?.takeIf { it.profileId == profile?.id }?.let { album ->
                AlbumRow(album,
                    selected = album.id in selection,
                    selectionMode = selection.isNotEmpty(),
                    onSelect = { viewModel.toggleSelection(album.id) },
                    cover = { CoverArt(viewModel, album.coverArt, 56.dp) }) { onOpen(album.id) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumRow(album: AlbumEntity, selected: Boolean = false, selectionMode: Boolean = false,
             onSelect: (() -> Unit)? = null, cover: @Composable () -> Unit = {},
             onToggleFavorite: (() -> Unit)? = null, onClick: () -> Unit) {
    val favoriteLabel = stringResource(
        if (album.starred == null) R.string.favorite_add else R.string.favorite_remove)
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("library-album-${album.id}")
            .combinedClickable(onClick = { if (selectionMode) onSelect?.invoke() else onClick() }, onLongClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onSelect?.invoke() })
        }
        cover()
        Column(Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(album.artist, album.year?.toString()).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (album.starred == null) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                    contentDescription = favoriteLabel,
                    tint = if (album.starred == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(durationLabel(album.duration), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArtistItems(items: LazyPagingItems<Any>, viewModel: LibraryViewModel, onOpen: (String) -> Unit) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), state = libraryListState(items)) {
        items(count = items.itemCount, key = items.itemKey(::libraryItemKey)) { index ->
            (items[index] as? ArtistEntity)?.takeIf { it.profileId == profile?.id }?.let { artist ->
                TextButton(onClick = { onOpen(artist.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(artist.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.library_artist_albums, artist.albumCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SongItems(items: LazyPagingItems<Any>, viewModel: LibraryViewModel) {
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), state = libraryListState(items)) {
        items(count = items.itemCount, key = items.itemKey(::libraryItemKey)) { index ->
            (items[index] as? SongEntity)?.takeIf { it.profileId == profile?.id }?.let { song ->
                SongRow(song, song.id in selection, selection.isNotEmpty(), { viewModel.toggleSelection(song.id) },
                    onToggleFavorite = { viewModel.toggleSongFavorite(song) })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun SongRow(song: SongEntity, selected: Boolean = false, selectionMode: Boolean = false,
            onSelect: (() -> Unit)? = null, onToggleFavorite: (() -> Unit)? = null) {
    val favoriteLabel = stringResource(
        if (song.starred == null) R.string.favorite_add else R.string.favorite_remove)
    Row(
        Modifier.fillMaxWidth()
            .testTag("library-song-${song.id}")
            .then(if (onSelect != null) Modifier.combinedClickable(onClick = onSelect, onLongClick = onSelect) else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onSelect?.invoke() })
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(song.artist ?: song.displayArtist, song.album).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (song.starred == null) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                    contentDescription = favoriteLabel,
                    tint = if (song.starred == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(durationLabel(song.duration), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GenreItems(items: LazyPagingItems<Any>, profileId: Long, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), state = libraryListState(items)) {
        items(count = items.itemCount, key = items.itemKey(::libraryItemKey)) { index ->
            (items[index] as? GenreEntity)?.takeIf { it.profileId == profileId }?.let { genre ->
                TextButton(onClick = { onOpen(genre.name) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(genre.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.library_genre_counts, genre.albumCount, genre.songCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal fun libraryItemKey(item: Any): String = when (item) {
    is AlbumEntity -> "album:${item.profileId}:${item.id}"
    is ArtistEntity -> "artist:${item.profileId}:${item.id}"
    is SongEntity -> "song:${item.profileId}:${item.id}"
    is GenreEntity -> "genre:${item.profileId}:${item.id}"
    is PlaylistEntity -> "playlist:${item.profileId}:${item.id}"
    is BrowseResult -> "${item.kind}:${item.profileId}:${item.id}"
    else -> error("Unsupported library item")
}

@Composable
private fun libraryListState(items: LazyPagingItems<Any>): androidx.compose.foundation.lazy.LazyListState {
    val state = rememberLazyListState()
    val firstKey = items.itemSnapshotList.items.firstOrNull()?.let(::libraryItemKey)
    LaunchedEffect(firstKey) { state.scrollToItem(0) }
    return state
}

fun durationLabel(seconds: Int): String =
    if (seconds <= 0) "" else {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
