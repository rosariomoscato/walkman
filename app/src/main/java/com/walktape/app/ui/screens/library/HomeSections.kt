package com.walktape.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.R
import com.walktape.app.Graph
import com.walktape.app.core.downloads.toSongEntity
import com.walktape.app.ui.components.SectionHeader

@Composable
fun HomeSections(model: LibraryViewModel, onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit) {
    val cached by model.home.collectAsStateWithLifecycle()
    val profile by model.activeProfile.collectAsStateWithLifecycle()
    val playback by Graph.playback.state.collectAsStateWithLifecycle()
    val home = if (cached.profileId == profile?.id) cached else HomeContent()
    var historyOpen by remember(profile?.id) { mutableStateOf(false) }
    var downloadsOpen by remember(profile?.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionHeader(stringResource(R.string.home_continue)) }
        item {
            if (playback.hasMedia) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(playback.title)
                if (playback.artist.isNotBlank()) Text(playback.artist)
                TextButton(onClick = Graph.playback::togglePlayPause, enabled = playback.canPlayPause) {
                    Text(stringResource(if (playback.playWhenReady && !playback.ended) R.string.playback_pause else R.string.playback_resume))
                }
            } else HomeEmpty(R.string.home_continue_empty)
        }
        item { SectionHeader(stringResource(R.string.home_recent_added)) }
        if (home.recent.isEmpty()) item { HomeEmpty(R.string.home_section_empty) }
        items(home.recent, key = { "recent:${it.id}" }) { album ->
            AlbumRow(album, cover = { CoverArt(model, album.coverArt, 56.dp) }) { onAlbum(album.id) }
        }
        item { SectionHeader(stringResource(R.string.home_recent_played)) }
        item { TextButton(onClick = { historyOpen = true }) { Text(stringResource(R.string.listening_history)) } }
        if (home.played.isEmpty()) item { HomeEmpty(R.string.home_section_empty) }
        items(home.played, key = { "played:${it.id}" }) { album -> AlbumRow(album) { onAlbum(album.id) } }
        item { SectionHeader(stringResource(R.string.home_favorites)) }
        if (home.favoriteAlbums.isEmpty() && home.favoriteSongs.isEmpty() && home.favoriteArtists.isEmpty()) item { HomeEmpty(R.string.home_section_empty) }
        items(home.favoriteAlbums, key = { "favoriteAlbum:${it.id}" }) { album ->
            AlbumRow(album, onToggleFavorite = { model.toggleAlbumFavorite(album.profileId, album.id, album.starred == null) }) { onAlbum(album.id) }
        }
        items(home.favoriteSongs, key = { "favoriteSong:${it.id}" }) { song ->
            SongRow(song, onToggleFavorite = { model.toggleSongFavorite(song) })
        }
        items(home.favoriteArtists, key = { "favoriteArtist:${it.id}" }) { artist ->
            TextButton(onClick = { onArtist(artist.id) }, modifier = Modifier.fillMaxWidth()) { Text(artist.name) }
        }
        item { SectionHeader(stringResource(R.string.home_downloads)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { downloadsOpen = true }) { Text(stringResource(R.string.download_manage)) }
            }
        }
        if (home.downloads.isEmpty()) item { HomeEmpty(R.string.download_empty) }
        items(home.downloads, key = { "download:${it.songId}" }) { download ->
            DownloadedSongRow(download, model)
        }
        item { SectionHeader(stringResource(R.string.library_tab_playlists)) }
        if (home.playlists.isEmpty()) item { HomeEmpty(R.string.home_section_empty) }
        items(home.playlists, key = { "playlist:${it.id}" }) { PlaylistRow(it, { onPlaylist(it.id) }) }
    }
    if (historyOpen) profile?.id?.let { id ->
        com.walktape.app.ui.components.ListeningHistorySheet(id) { historyOpen = false }
    }
    if (downloadsOpen) profile?.id?.let { id ->
        com.walktape.app.ui.components.DownloadsSheet(id) { downloadsOpen = false }
    }
}

@Composable
private fun HomeEmpty(label: Int) {
    Text(stringResource(label), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** A downloaded song row: plays from the local copy via its metadata snapshot. */
@Composable
private fun DownloadedSongRow(download: com.walktape.app.core.db.DownloadEntity, model: LibraryViewModel) {
    val song = remember(download) { download.toSongEntity() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            song.artist?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        IconButton(onClick = { com.walktape.app.Graph.playback.playQueue(listOf(song), 0) }) {
            Icon(Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.selection_play_now))
        }
    }
}
