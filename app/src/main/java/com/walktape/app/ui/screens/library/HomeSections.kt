package com.walktape.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.walktape.app.R
import com.walktape.app.ui.components.SectionHeader

@Composable
fun HomeSections(model: LibraryViewModel, onAlbum: (String) -> Unit, onArtist: (String) -> Unit, onPlaylist: (String) -> Unit) {
    val cached by model.home.collectAsStateWithLifecycle()
    val profile by model.activeProfile.collectAsStateWithLifecycle()
    val home = if (cached.profileId == profile?.id) cached else HomeContent()
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionHeader(stringResource(R.string.home_continue)) }
        item { HomeEmpty(R.string.home_continue_empty) }
        item { SectionHeader(stringResource(R.string.home_recent_added)) }
        if (home.recent.isEmpty()) item { HomeEmpty(R.string.home_section_empty) }
        items(home.recent, key = { "recent:${it.id}" }) { album ->
            AlbumRow(album, cover = { CoverArt(model, album.coverArt, 56.dp) }) { onAlbum(album.id) }
        }
        item { SectionHeader(stringResource(R.string.home_recent_played)) }
        if (home.played.isEmpty()) item { HomeEmpty(R.string.home_section_empty) }
        items(home.played, key = { "played:${it.id}" }) { album -> AlbumRow(album) { onAlbum(album.id) } }
        item { SectionHeader(stringResource(R.string.home_favorites)) }
        if (home.favoriteAlbums.isEmpty() && home.favoriteSongs.isEmpty() && home.favoriteArtists.isEmpty()) item { HomeEmpty(R.string.home_section_empty) }
        items(home.favoriteAlbums, key = { "favoriteAlbum:${it.id}" }) { album -> AlbumRow(album) { onAlbum(album.id) } }
        items(home.favoriteSongs, key = { "favoriteSong:${it.id}" }) { SongRow(it) }
        items(home.favoriteArtists, key = { "favoriteArtist:${it.id}" }) { artist ->
            TextButton(onClick = { onArtist(artist.id) }, modifier = Modifier.fillMaxWidth()) { Text(artist.name) }
        }
        item { SectionHeader(stringResource(R.string.home_downloads)) }
        if (home.localSongs.isEmpty()) item { HomeEmpty(R.string.home_downloads_empty) }
        items(home.localSongs, key = { "local:${it.id}" }) { SongRow(it) }
        item { SectionHeader(stringResource(R.string.library_tab_playlists)) }
        if (home.playlists.isEmpty()) item { HomeEmpty(R.string.home_section_empty) }
        items(home.playlists, key = { "playlist:${it.id}" }) { PlaylistRow(it, { onPlaylist(it.id) }) }
    }
}

@Composable
private fun HomeEmpty(label: Int) {
    Text(stringResource(label), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
