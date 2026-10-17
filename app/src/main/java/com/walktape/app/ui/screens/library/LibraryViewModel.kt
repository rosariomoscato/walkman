package com.walktape.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.map
import com.walktape.app.Graph
import com.walktape.app.core.db.AlbumEntity
import com.walktape.app.core.db.ArtistEntity
import com.walktape.app.core.db.GenreEntity
import com.walktape.app.core.db.LibraryStateEntity
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.db.SongEntity
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.library.BrowseKind
import com.walktape.app.core.library.BrowseQueries
import com.walktape.app.core.library.LibraryFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab(val sortKey: String, val defaultSort: String) {
    HOME("sort_home", "name"),
    ALBUMS("sort_album", "name"),
    ARTISTS("sort_artist", "name"),
    SONGS("sort_song", "title"),
    GENRES("sort_genre", "name"),
    PLAYLISTS("sort_playlist", "name"),
    SEARCH("sort_search", "name");
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel : ViewModel() {
    private val database = Graph.database
    private val profiles = Graph.profiles
    private val syncEngine = Graph.librarySyncEngine

    val activeProfile: StateFlow<ProfileEntity?> = profiles.profiles
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val libraryState: StateFlow<LibraryStateEntity?> = activeProfile.flatMapLatest { profile ->
        profile?.let { database.libraryDao().observeState(it.id) } ?: flowOf<LibraryStateEntity?>(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val syncProgress = syncEngine.progress
    val offlineMode = activeProfile.flatMapLatest { profile ->
        profile?.let { database.downloadDao().observeSettings(it.id).map { it?.offlineOnly == true } }
            ?: flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Offline edits waiting for the network (playlist/favorites queue, W08-T06). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingEdits: StateFlow<Int> = activeProfile.flatMapLatest { profile ->
        profile?.let { Graph.database.downloadDao().observeUnsyncedCount(it.id) } ?: flowOf(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun syncOfflineOps() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch { Graph.offlineOps.drain(profileId) }
    }

    private val mutableTab = MutableStateFlow(LibraryTab.HOME)
    val tab = mutableTab.asStateFlow()
    private val mutableQuery = MutableStateFlow("")
    val query = mutableQuery.asStateFlow()
    private val mutableFilter = MutableStateFlow(LibraryFilter())
    val filter = mutableFilter.asStateFlow()
    val genres = activeProfile.flatMapLatest { profile ->
        profile?.let { database.browseDao().genreNames(it.id) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val years = activeProfile.flatMapLatest { profile ->
        profile?.let { database.browseDao().years(it.id) } ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val home = activeProfile.flatMapLatest { profile ->
        if (profile == null) flowOf(HomeContent()) else {
            val dao = database.browseDao()
            val albums = combine(dao.recentAlbums(profile.id), dao.playedAlbums(profile.id), dao.favoriteAlbums(profile.id)) { recent, played, favorites ->
                HomeAlbums(recent, played, favorites)
            }
            combine(albums, dao.favoriteSongs(profile.id), dao.favoriteArtists(profile.id),
                database.downloadDao().completedDownloads(profile.id), dao.homePlaylists(profile.id)) { a, songs, artists, downloads, playlists ->
                HomeContent(a.recent, a.played, a.favorites, songs, artists, downloads, playlists, profile.id)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeContent())
    private val mutableSorts = MutableStateFlow(
        LibraryTab.entries.associateWith { it.defaultSort }
    )
    val sorts = mutableSorts.asStateFlow()
    private val mutableSelection = MutableStateFlow<Set<String>>(emptySet())
    val selection = mutableSelection.asStateFlow()
    private var selectionRevision = 0L
    private val mutableQueueBusy = MutableStateFlow(false)
    val queueBusy = mutableQueueBusy.asStateFlow()
    private val mutableQueueError = MutableStateFlow(false)
    val queueError = mutableQueueError.asStateFlow()

    fun toggleSelection(id: String) {
        selectionRevision++
        mutableQueueError.value = false
        mutableSelection.value = mutableSelection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() { selectionRevision++; mutableSelection.value = emptySet(); mutableQueueError.value = false }

    /** Queue actions from the multi-selection; only tabs whose rows resolve to songs. */
    enum class QueueInsert { NOW, NEXT, APPEND }

    private val mutablePlaylistBusy = MutableStateFlow(false)
    val playlistBusy = mutablePlaylistBusy.asStateFlow()
    private val mutablePlaylistError = MutableStateFlow(false)
    val playlistError = mutablePlaylistError.asStateFlow()
    private val mutableFavoriteError = MutableStateFlow(false)
    val favoriteError = mutableFavoriteError.asStateFlow()
    private val mutableDownloadBusy = MutableStateFlow(false)
    val downloadBusy = mutableDownloadBusy.asStateFlow()

    /** Own playlists only: server-side edits are refused for other owners' playlists. */
    val editablePlaylists: StateFlow<List<PlaylistEntity>> = combine(activeProfile, profiles.profiles) { active, _ -> active }
        .flatMapLatest { profile ->
            profile?.let { database.browseDao().editablePlaylists(it.id, it.username) } ?: flowOf(emptyList())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun enqueueSelection(insert: QueueInsert, visibleItems: List<Any>) {
        val profileId = activeProfile.value?.id ?: return
        if (mutableSelection.value.isEmpty() || mutableQueueBusy.value) return
        val selectedTab = tab.value
        val revision = selectionRevision
        mutableQueueBusy.value = true
        viewModelScope.launch {
            try {
                val resolved = resolveSelectionSongs(profileId, selectedTab, visibleItems) ?: return@launch
                if (revision != selectionRevision || activeProfile.value?.id != profileId) return@launch
                if (!resolved.complete) { mutableQueueError.value = true; return@launch }
                when (insert) {
                    QueueInsert.NOW -> Graph.playback.playNow(resolved.songs)
                    QueueInsert.NEXT -> Graph.playback.playNext(resolved.songs)
                    QueueInsert.APPEND -> Graph.playback.appendToQueue(resolved.songs)
                }
                clearSelection()
            } finally { mutableQueueBusy.value = false }
        }
    }

    /** Adds the selection to an existing own playlist, or creates a new one with these songs. */
    fun addSelectionToPlaylist(playlistId: String?, newName: String, visibleItems: List<Any>) {
        val profileId = activeProfile.value?.id ?: return
        if (mutableSelection.value.isEmpty() || mutablePlaylistBusy.value) return
        val selectedTab = tab.value
        val revision = selectionRevision
        val name = if (playlistId == null) newName.trim() else null
        if (playlistId == null && name.isNullOrEmpty()) { mutablePlaylistError.value = true; return }
        mutablePlaylistBusy.value = true
        viewModelScope.launch {
            try {
                val resolved = resolveSelectionSongs(profileId, selectedTab, visibleItems) ?: return@launch
                if (revision != selectionRevision || activeProfile.value?.id != profileId) return@launch
                if (!resolved.complete || resolved.songs.isEmpty()) { mutablePlaylistError.value = true; return@launch }
                val repository = Graph.playlistRepository()
                val error = if (playlistId == null) {
                    repository.create(profileId, name!!, resolved.songs.map { it.id })
                } else {
                    repository.addSongs(profileId, playlistId, resolved.songs.map { it.id })
                }
                if (error != null) mutablePlaylistError.value = true else clearSelection()
            } finally { mutablePlaylistBusy.value = false }
        }
    }

    fun createPlaylist(name: String) {
        val profileId = activeProfile.value?.id ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || mutablePlaylistBusy.value) return
        mutablePlaylistBusy.value = true
        viewModelScope.launch {
            try {
                if (Graph.playlistRepository().create(profileId, trimmed, emptyList()) != null) {
                    mutablePlaylistError.value = true
                }
            } finally { mutablePlaylistBusy.value = false }
        }
    }

    fun toggleSongFavorite(song: SongEntity) {
        viewModelScope.launch {
            if (Graph.starRepository().setStarred(song.profileId,
                    com.walktape.app.core.library.StarRepository.Kind.SONG, song.id, song.starred == null) != null) {
                mutableFavoriteError.value = true
            }
        }
    }

    fun toggleAlbumFavorite(profileId: Long, albumId: String, starred: Boolean) {
        viewModelScope.launch {
            if (Graph.starRepository().setStarred(profileId,
                    com.walktape.app.core.library.StarRepository.Kind.ALBUM, albumId, starred) != null) {
                mutableFavoriteError.value = true
            }
        }
    }

    fun toggleArtistFavorite(profileId: Long, artistId: String, starred: Boolean) {
        viewModelScope.launch {
            if (Graph.starRepository().setStarred(profileId,
                    com.walktape.app.core.library.StarRepository.Kind.ARTIST, artistId, starred) != null) {
                mutableFavoriteError.value = true
            }
        }
    }

    fun dismissFavoriteError() { mutableFavoriteError.value = false }
    fun dismissPlaylistError() { mutablePlaylistError.value = false }

    /** Downloads the multi-selection (songs or whole albums) as single-song downloads. */
    fun downloadSelection(visibleItems: List<Any>) {
        val profileId = activeProfile.value?.id ?: return
        if (mutableSelection.value.isEmpty() || mutableDownloadBusy.value) return
        val selectedTab = tab.value
        val revision = selectionRevision
        mutableDownloadBusy.value = true
        viewModelScope.launch {
            try {
                if (selectedTab == LibraryTab.PLAYLISTS) {
                    val ids = mutableSelection.value.toList()
                    for (id in ids) {
                        if (revision != selectionRevision || activeProfile.value?.id != profileId) return@launch
                        Graph.downloadStore.enqueuePlaylist(profileId, id)
                    }
                    Graph.downloadEngine.schedule(profileId)
                    clearSelection()
                    return@launch
                }
                val resolved = resolveSelectionSongs(profileId, selectedTab, visibleItems) ?: return@launch
                if (revision != selectionRevision || activeProfile.value?.id != profileId) return@launch
                if (!resolved.complete || resolved.songs.isEmpty()) return@launch
                if (selectedTab == LibraryTab.ALBUMS) {
                    resolved.songs.groupBy { it.albumId }.forEach { (albumId, songs) ->
                        Graph.downloadStore.enqueueSongs(profileId, songs,
                            com.walktape.app.core.downloads.DownloadStore.albumOrigin(albumId.orEmpty()))
                    }
                } else Graph.downloadStore.enqueueSongs(profileId, resolved.songs,
                    com.walktape.app.core.downloads.DownloadStore.ORIGIN_SINGLE)
                Graph.downloadEngine.schedule(profileId)
                clearSelection()
            } finally { mutableDownloadBusy.value = false }
        }
    }

    /** Downloads a whole album with an album origin (one audio file per song). */
    fun downloadAlbum(albumId: String, songs: List<SongEntity>) {
        val profileId = activeProfile.value?.id ?: return
        if (songs.isEmpty() || mutableDownloadBusy.value) return
        mutableDownloadBusy.value = true
        viewModelScope.launch {
            try {
                Graph.downloadStore.enqueueSongs(profileId, songs,
                    com.walktape.app.core.downloads.DownloadStore.albumOrigin(albumId))
                Graph.downloadEngine.schedule(profileId)
            } finally { mutableDownloadBusy.value = false }
        }
    }

    /** Downloads a cached playlist in order (duplicates never duplicate the audio file). */
    fun downloadPlaylist(profileId: Long, playlistId: String) {
        if (mutableDownloadBusy.value) return
        mutableDownloadBusy.value = true
        viewModelScope.launch {
            try {
                Graph.downloadStore.enqueuePlaylist(profileId, playlistId)
                Graph.downloadEngine.schedule(profileId)
            } finally { mutableDownloadBusy.value = false }
        }
    }

    private class ResolvedSelection(val songs: List<SongEntity>, val complete: Boolean)

    private suspend fun resolveSelectionSongs(profileId: Long, selectedTab: LibraryTab, visibleItems: List<Any>): ResolvedSelection? {
        val ids = mutableSelection.value
        if (ids.isEmpty()) return null
        if (selectedTab != LibraryTab.SONGS && selectedTab != LibraryTab.ALBUMS) return null
        val displayedIds = when (selectedTab) {
            LibraryTab.SONGS -> visibleItems.filterIsInstance<SongEntity>().filter { it.profileId == profileId }.map { it.id }
            LibraryTab.ALBUMS -> visibleItems.filterIsInstance<AlbumEntity>().filter { it.profileId == profileId }.map { it.id }
            else -> return null
        }
        val orderedIds = displayedIds.filter { it in ids } + ids.filter { it !in displayedIds }
        val dao = database.libraryDao()
        val songs = if (selectedTab == LibraryTab.SONGS) {
            val rows = orderedIds.chunked(500).flatMap { dao.songsByIds(profileId, it) }.associateBy { it.id }
            orderedIds.mapNotNull { rows[it] }
        } else {
            val rows = orderedIds.chunked(500).flatMap { dao.songsInAlbums(profileId, it) }.groupBy { it.albumId }
            orderedIds.flatMap { rows[it].orEmpty() }
        }
        val complete = if (selectedTab == LibraryTab.SONGS) songs.size == ids.size
            else ids.all { id -> songs.any { it.albumId == id } }
        return ResolvedSelection(songs, complete && songs.isNotEmpty())
    }

    val pager: Flow<PagingData<Any>> = combine(activeProfile, mutableTab, mutableQuery, mutableSorts, mutableFilter) { profile, tab, query, sorts, filter ->
        LibraryRequest(profile?.id, tab, filter.copy(query = query), sorts[tab] ?: tab.defaultSort)
    }.flatMapLatest { request ->
        val (profileId, tab, filter, sort) = request
        if (profileId == null) return@flatMapLatest flowOf(PagingData.empty<Any>())
        val dao = database.browseDao()
        when (tab) {
            LibraryTab.HOME -> flowOf(PagingData.empty())
            LibraryTab.ALBUMS -> paged { dao.albums(BrowseQueries.list(BrowseKind.ALBUM, profileId, filter, sort)) }
            LibraryTab.ARTISTS -> paged { dao.artists(BrowseQueries.list(BrowseKind.ARTIST, profileId, filter, sort)) }
            LibraryTab.SONGS -> paged { dao.songs(BrowseQueries.list(BrowseKind.SONG, profileId, filter, sort)) }
            LibraryTab.GENRES -> paged { dao.genres(BrowseQueries.list(BrowseKind.GENRE, profileId, filter, sort)) }
            LibraryTab.PLAYLISTS -> paged { dao.playlists(BrowseQueries.list(BrowseKind.PLAYLIST, profileId, filter, sort)) }
            LibraryTab.SEARCH -> paged { dao.search(BrowseQueries.search(profileId, filter)) }
        }
    }.cachedIn(viewModelScope)

    private fun <T : Any> paged(source: () -> PagingSource<Int, T>): Flow<PagingData<Any>> {
        return Pager(PagingConfig(pageSize = 80, initialLoadSize = 160, enablePlaceholders = false)) {
            source()
        }.flow.map { paging -> paging.map { it as Any } }
    }

    fun setTab(tab: LibraryTab) { clearSelection(); mutableTab.value = tab }
    fun setQuery(query: String) {
        clearSelection()
        mutableQuery.value = query
        if (query.isNotBlank() && mutableTab.value == LibraryTab.HOME) mutableTab.value = LibraryTab.SEARCH
    }
    fun setFilter(genre: String?, year: Int?, offlineOnly: Boolean) {
        clearSelection()
        mutableFilter.value = LibraryFilter(genre = genre, year = year, offlineOnly = offlineOnly)
    }
    fun setSort(tab: LibraryTab, sort: String) {
        mutableSorts.value = mutableSorts.value + (tab to sort)
        viewModelScope.launch { Graph.settingsRepository.setLibrarySort(tab.sortKey, sort) }
    }

    fun sync() {
        if (syncProgress.value.running) return
        viewModelScope.launch {
            val id = profiles.profiles.first().firstOrNull { it.isActive }?.id
            if (id != null) syncEngine.sync(id)
        }
    }

    fun cancelSync() { syncEngine.cancel() }

    init {
        viewModelScope.launch {
            var previous: Long? = null
            activeProfile.collect { profile ->
                if (profile?.id != previous) {
                    clearSelection()
                    mutableFilter.value = LibraryFilter()
                    mutableQuery.value = ""
                    syncEngine.cancel()
                    previous = profile?.id
                }
            }
        }
        viewModelScope.launch {
            val stored = Graph.settingsRepository.librarySorts()
            val merged = mutableSorts.value.toMutableMap()
            LibraryTab.entries.forEach { tab ->
                stored[tab.sortKey]?.let { merged[tab] = it }
            }
            mutableSorts.value = merged
        }
    }

    override fun onCleared() {
        syncEngine.cancel()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LibraryViewModel() as T
        }
    }
}

private data class LibraryRequest(val profileId: Long?, val tab: LibraryTab, val filter: LibraryFilter, val sort: String)

data class HomeContent(val recent: List<AlbumEntity> = emptyList(), val played: List<AlbumEntity> = emptyList(),
    val favoriteAlbums: List<AlbumEntity> = emptyList(), val favoriteSongs: List<SongEntity> = emptyList(),
    val favoriteArtists: List<ArtistEntity> = emptyList(), val downloads: List<com.walktape.app.core.db.DownloadEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(), val profileId: Long? = null)
private data class HomeAlbums(val recent: List<AlbumEntity>, val played: List<AlbumEntity>, val favorites: List<AlbumEntity>)
