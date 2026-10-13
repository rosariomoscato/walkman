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
    ALBUMS("sort_album", "name"),
    ARTISTS("sort_artist", "name"),
    SONGS("sort_song", "title"),
    GENRES("sort_genre", "name");
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

    private val mutableTab = MutableStateFlow(LibraryTab.ALBUMS)
    val tab = mutableTab.asStateFlow()
    private val mutableQuery = MutableStateFlow("")
    val query = mutableQuery.asStateFlow()
    private val mutableSorts = MutableStateFlow(
        LibraryTab.entries.associateWith { it.defaultSort }
    )
    val sorts = mutableSorts.asStateFlow()
    private val mutableSelection = MutableStateFlow<Set<String>>(emptySet())
    val selection = mutableSelection.asStateFlow()

    fun toggleSelection(id: String) {
        mutableSelection.value = mutableSelection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() { mutableSelection.value = emptySet() }

    val pager: Flow<PagingData<Any>> = combine(activeProfile, mutableTab, mutableQuery, mutableSorts) { profile, tab, query, sorts ->
        LibraryRequest(profile?.id, tab, query, sorts[tab] ?: tab.defaultSort)
    }.flatMapLatest { request ->
        val (profileId, tab, rawQuery, sort) = request
        if (profileId == null) return@flatMapLatest flowOf(PagingData.empty<Any>())
        val pattern = "%${rawQuery.trim()}%"
        when (tab) {
            LibraryTab.ALBUMS -> paged { database.libraryDao().albums(profileId, sort, pattern) }
            LibraryTab.ARTISTS -> paged { database.libraryDao().artists(profileId, pattern) }
            LibraryTab.SONGS -> paged { database.libraryDao().songs(profileId, sort, pattern) }
            LibraryTab.GENRES -> paged { database.libraryDao().genres(profileId, pattern) }
        }
    }.cachedIn(viewModelScope)

    private fun <T : Any> paged(source: () -> PagingSource<Int, T>): Flow<PagingData<Any>> {
        return Pager(PagingConfig(pageSize = 80, initialLoadSize = 160, enablePlaceholders = false)) {
            source()
        }.flow.map { paging -> paging.map { it as Any } }
    }

    fun setTab(tab: LibraryTab) { clearSelection(); mutableTab.value = tab }
    fun setQuery(query: String) { mutableQuery.value = query }
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

private data class LibraryRequest(val profileId: Long?, val tab: LibraryTab, val query: String, val sort: String)
