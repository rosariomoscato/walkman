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
    private val syncEngine = Graph.librarySync()

    val activeProfile: StateFlow<ProfileEntity?> = profiles.profiles
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val libraryState: StateFlow<LibraryStateEntity?> = activeProfile.flatMapLatest { profile ->
        profile?.let { database.libraryDao().observeState(it.id) } ?: flowOf<LibraryStateEntity?>(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val syncProgress = syncEngine.progress

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
                dao.localSongs(profile.id), dao.homePlaylists(profile.id)) { a, songs, artists, local, playlists ->
                HomeContent(a.recent, a.played, a.favorites, songs, artists, local, playlists, profile.id)
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

    fun enqueueSelection(insert: QueueInsert, visibleItems: List<Any>) {
        val profileId = activeProfile.value?.id ?: return
        val ids = mutableSelection.value
        if (ids.isEmpty() || mutableQueueBusy.value) return
        val selectedTab = tab.value
        val revision = selectionRevision
        val displayedIds = when (selectedTab) {
            LibraryTab.SONGS -> visibleItems.filterIsInstance<SongEntity>().filter { it.profileId == profileId }.map { it.id }
            LibraryTab.ALBUMS -> visibleItems.filterIsInstance<AlbumEntity>().filter { it.profileId == profileId }.map { it.id }
            else -> return
        }
        val orderedIds = displayedIds.filter { it in ids } + ids.filter { it !in displayedIds }
        mutableQueueBusy.value = true
        viewModelScope.launch {
            try {
                val dao = database.libraryDao()
                val songs = if (selectedTab == LibraryTab.SONGS) {
                    val rows = orderedIds.chunked(500).flatMap { dao.songsByIds(profileId, it) }.associateBy { it.id }
                    orderedIds.mapNotNull { rows[it] }
                } else {
                    val rows = orderedIds.chunked(500).flatMap { dao.songsInAlbums(profileId, it) }.groupBy { it.albumId }
                    orderedIds.flatMap { rows[it].orEmpty() }
                }
                if (revision != selectionRevision || activeProfile.value?.id != profileId) return@launch
                val complete = if (selectedTab == LibraryTab.SONGS) songs.size == ids.size
                    else ids.all { id -> songs.any { it.albumId == id } }
                if (!complete || songs.isEmpty()) { mutableQueueError.value = true; return@launch }
                when (insert) {
                    QueueInsert.NOW -> Graph.playback.playNow(songs)
                    QueueInsert.NEXT -> Graph.playback.playNext(songs)
                    QueueInsert.APPEND -> Graph.playback.appendToQueue(songs)
                }
                clearSelection()
            } finally { mutableQueueBusy.value = false }
        }
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
    val favoriteArtists: List<ArtistEntity> = emptyList(), val localSongs: List<SongEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(), val profileId: Long? = null)
private data class HomeAlbums(val recent: List<AlbumEntity>, val played: List<AlbumEntity>, val favorites: List<AlbumEntity>)
