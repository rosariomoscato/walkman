package com.walktape.app.core.library

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.walktape.app.core.db.AlbumEntity
import com.walktape.app.core.db.ArtistEntity
import com.walktape.app.core.db.GenreEntity
import com.walktape.app.core.db.LibraryDao
import com.walktape.app.core.db.LibraryStateEntity
import com.walktape.app.core.db.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Pure in-memory LibraryDao for JVM tests: no Android framework involved. */
class FakeLibraryDao : LibraryDao {
    val artists = java.util.concurrent.ConcurrentHashMap<Pair<Long, String>, ArtistEntity>()
    val albums = java.util.concurrent.ConcurrentHashMap<Pair<Long, String>, AlbumEntity>()
    val songs = java.util.concurrent.ConcurrentHashMap<Pair<Long, String>, SongEntity>()
    val genres = java.util.concurrent.ConcurrentHashMap<Pair<Long, String>, GenreEntity>()
    private val states = MutableStateFlow<Map<Long, LibraryStateEntity>>(emptyMap())

    override suspend fun upsertArtists(list: List<ArtistEntity>) { list.forEach { artists[it.profileId to it.id] = it } }
    override suspend fun upsertAlbums(list: List<AlbumEntity>) { list.forEach { albums[it.profileId to it.id] = it } }
    override suspend fun upsertSongs(list: List<SongEntity>) { list.forEach { songs[it.profileId to it.id] = it } }
    override suspend fun upsertGenres(list: List<GenreEntity>) { list.forEach { genres[it.profileId to it.id] = it } }
    override suspend fun upsertState(state: LibraryStateEntity) { states.value = states.value + (state.profileId to state) }
    override fun observeState(profileId: Long): Flow<LibraryStateEntity?> = states.map { it[profileId] }
    override suspend fun state(profileId: Long): LibraryStateEntity? = states.value[profileId]

    override fun albumsByName(profileId: Long, query: String) = page(albums.values.filter { it.profileId == profileId })
    override fun albumsByArtist(profileId: Long, query: String) = page(albums.values.filter { it.profileId == profileId })
    override fun albumsByYear(profileId: Long, query: String) = page(albums.values.filter { it.profileId == profileId })
    override fun albumsByRecent(profileId: Long, query: String) = page(albums.values.filter { it.profileId == profileId })
    override fun artistsByName(profileId: Long, query: String) = page(artists.values.filter { it.profileId == profileId })
    override fun songsByTitle(profileId: Long, query: String) = page(songs.values.filter { it.profileId == profileId })
    override fun songsByArtist(profileId: Long, query: String) = page(songs.values.filter { it.profileId == profileId })
    override fun songsByAlbum(profileId: Long, query: String) = page(songs.values.filter { it.profileId == profileId })
    override fun songsByDuration(profileId: Long, query: String) = page(songs.values.filter { it.profileId == profileId })
    override fun genresByName(profileId: Long, query: String) = page(genres.values.filter { it.profileId == profileId })

    override suspend fun album(profileId: Long, id: String): AlbumEntity? = albums[profileId to id]
    override fun observeAlbum(profileId: Long, id: String): Flow<AlbumEntity?> = MutableStateFlow(albums[profileId to id])
    override fun observeAlbumSongs(profileId: Long, albumId: String): Flow<List<SongEntity>> =
        MutableStateFlow(songs.values.filter { it.profileId == profileId && it.albumId == albumId })
    override fun observeArtistAlbums(profileId: Long, artistId: String): Flow<List<AlbumEntity>> =
        MutableStateFlow(albums.values.filter { it.profileId == profileId && it.artistId == artistId })
    override suspend fun songsInAlbums(profileId: Long, albumIds: List<String>): List<SongEntity> =
        songs.values.filter { it.profileId == profileId && it.albumId in albumIds }
    override suspend fun songsByIds(profileId: Long, ids: List<String>): List<SongEntity> =
        ids.mapNotNull { songs[profileId to it] }
    override suspend fun artist(profileId: Long, id: String): ArtistEntity? = artists[profileId to id]
    override fun observeGenreSongs(profileId: Long, genre: String): Flow<List<SongEntity>> =
        MutableStateFlow(songs.values.filter { it.profileId == profileId && it.genre == genre })

    override fun genreSongsPaging(profileId: Long, genre: String) =
        page(songs.values.filter { it.profileId == profileId && it.genre == genre })

    override suspend fun songCount(profileId: Long): Int = songs.values.count { it.profileId == profileId }
    override suspend fun albumCount(profileId: Long): Int = albums.values.count { it.profileId == profileId }

    override suspend fun deleteSongsFor(profileId: Long) { songs.keys.removeAll { it.first == profileId } }
    override suspend fun deleteAlbumsFor(profileId: Long) { albums.keys.removeAll { it.first == profileId } }
    override suspend fun deleteArtistsFor(profileId: Long) { artists.keys.removeAll { it.first == profileId } }
    override suspend fun deleteGenresFor(profileId: Long) { genres.keys.removeAll { it.first == profileId } }
    override suspend fun deleteStateFor(profileId: Long) { states.value = states.value - profileId }

    override suspend fun missingAlbumIds(profileId: Long, keep: List<String>): List<String> =
        albums.values.filter { it.profileId == profileId && it.id !in keep }.map { it.id }
    override suspend fun deleteSongsInAlbums(profileId: Long, albumIds: List<String>) {
        songs.keys.removeAll { it.first == profileId && songs[it]?.albumId in albumIds }
    }
    override suspend fun deleteAlbums(profileId: Long, albumIds: List<String>) {
        albums.keys.removeAll { it.first == profileId && it.second in albumIds }
    }

    private fun <T : Any> page(items: List<T>) = object : PagingSource<Int, T>() {
        override fun getRefreshKey(state: PagingState<Int, T>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> =
            LoadResult.Page(items, null, null)
    }
}
