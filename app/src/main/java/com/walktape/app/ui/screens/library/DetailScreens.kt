package com.walktape.app.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.walktape.app.core.db.SongEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class DetailViewModel(profileId: Long, key: String) : ViewModel() {
    private val dao = Graph.database.libraryDao()

    val albumDetail = dao.observeAlbum(profileId, key)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val albumSongs = dao.observeAlbumSongs(profileId, key)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val artistAlbums = dao.observeArtistAlbums(profileId, key)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val artistDetail = dao.observeArtist(profileId, key)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val genreSongs = Pager(PagingConfig(pageSize = 80, enablePlaceholders = false)) {
        dao.genreSongsPaging(profileId, key)
    }.flow.cachedIn(viewModelScope)

    companion object {
        fun factory(profileId: Long, key: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DetailViewModel(profileId, key) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBack: () -> Unit,
    libraryModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val profile by libraryModel.activeProfile.collectAsStateWithLifecycle()
    if (profile == null) return
    val model: DetailViewModel = viewModel(
        key = "album/$albumId/${profile!!.id}",
        factory = DetailViewModel.factory(profile!!.id, albumId)
    )
    val album by model.albumDetail.collectAsStateWithLifecycle()
    val songs by model.albumSongs.collectAsStateWithLifecycle()
    val favoriteError by libraryModel.favoriteError.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(album?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                album?.let { current ->
                    val favoriteLabel = stringResource(
                        if (current.starred == null) R.string.favorite_add else R.string.favorite_remove)
                    IconButton(onClick = {
                        libraryModel.toggleAlbumFavorite(current.profileId, current.id, current.starred == null)
                    }) {
                        Icon(
                            if (current.starred == null) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                            contentDescription = favoriteLabel,
                            tint = if (current.starred == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        )
        if (favoriteError) MutationErrorLine(true, libraryModel::dismissFavoriteError)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CoverArt(libraryModel, album?.coverArt, 96.dp)
                        Column(Modifier.weight(1f)) {
                            Text(album?.name ?: "", style = MaterialTheme.typography.titleLarge)
                            Text(album?.artist ?: "", style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    album?.year?.toString(),
                                    album?.genre,
                                    album?.songCount?.let { stringResource(R.string.library_album_songs, it) },
                                    album?.let { durationLabel(it.duration) },
                                    album?.takeIf { it.isCompilation }?.let { stringResource(R.string.library_compilation) }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (songs.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { Graph.playback.playQueue(songs, 0) },
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Text(stringResource(R.string.playback_play_album))
                            }
                            FilledTonalButton(
                                onClick = { libraryModel.downloadAlbum(albumId, songs) },
                                modifier = Modifier.padding(top = 12.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Text(stringResource(R.string.download_album))
                            }
                        }
                    }
                }
            }
            val discs = songs.map { it.discNumber }.distinct()
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongDetailRow(song, discs.size > 1,
                    onToggleFavorite = { libraryModel.toggleSongFavorite(song) }) { Graph.playback.playQueue(songs, index) }
            }
        }
    }
}

@Composable
private fun SongDetailRow(song: SongEntity, multiDisc: Boolean,
                          onToggleFavorite: (() -> Unit)? = null, onClick: () -> Unit) {
    val favoriteLabel = stringResource(
        if (song.starred == null) R.string.favorite_add else R.string.favorite_remove)
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (song.track > 0) song.track.toString() else "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val artistLabel = song.displayArtist ?: song.artist
            if (!artistLabel.isNullOrBlank()) {
                Text(artistLabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (multiDisc) {
            Text(stringResource(R.string.library_disc, song.discNumber),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.heightIn(min = 48.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    libraryModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val profile by libraryModel.activeProfile.collectAsStateWithLifecycle()
    if (profile == null) return
    val model: DetailViewModel = viewModel(
        key = "artist/$artistId/${profile!!.id}",
        factory = DetailViewModel.factory(profile!!.id, artistId)
    )
    val albums by model.artistAlbums.collectAsStateWithLifecycle()
    val artist by model.artistDetail.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(artist?.name ?: stringResource(R.string.library_tab_artists),
                maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            actions = {
                artist?.let { current ->
                    val favoriteLabel = stringResource(
                        if (current.starred == null) R.string.favorite_add else R.string.favorite_remove)
                    IconButton(onClick = {
                        libraryModel.toggleArtistFavorite(current.profileId, current.id, current.starred == null)
                    }) {
                        Icon(
                            if (current.starred == null) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                            contentDescription = favoriteLabel,
                            tint = if (current.starred == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(albums, key = { it.id }) { album -> AlbumRow(album) { onOpenAlbum(album.id) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    genre: String,
    onBack: () -> Unit,
    libraryModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val profile by libraryModel.activeProfile.collectAsStateWithLifecycle()
    if (profile == null) return
    val model: DetailViewModel = viewModel(
        key = "genre/$genre/${profile!!.id}",
        factory = DetailViewModel.factory(profile!!.id, genre)
    )
    val songs = model.genreSongs.collectAsLazyPagingItems()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(genre, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(count = songs.itemCount, key = songs.itemKey { it.id }) { index ->
                songs[index]?.let { song ->
                    SongRow(song, onToggleFavorite = { libraryModel.toggleSongFavorite(song) })
                }
            }
        }
    }
}
