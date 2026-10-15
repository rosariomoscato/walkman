package com.walktape.app.core.library

import com.walktape.app.core.db.AlbumEntity
import com.walktape.app.core.db.ArtistEntity
import com.walktape.app.core.db.GenreEntity
import com.walktape.app.core.db.LibraryDao
import com.walktape.app.core.db.LibraryStateEntity
import com.walktape.app.core.db.SongEntity
import com.walktape.app.core.net.AlbumDto
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.ArtistDto
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.SongDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class SyncProgress(
    val running: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val error: ApiError? = null,
    val finishedAt: Long? = null,
)

/** Full progressive index of one profile's library into Room (never a server mutation). */
class LibrarySync(
    private val libraryDao: LibraryDao,
    private val playlistIndex: PlaylistIndex? = null,
    private val offlineOnly: (Long) -> Boolean = { false },
    /** Optional post-sync hook (W08-T05: reconcile downloaded playlists on unmetered networks). */
    private val afterSync: (suspend (Long) -> Unit)? = null,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
) {
    private val mutableProgress = MutableStateFlow(SyncProgress())
    val progress = mutableProgress.asStateFlow()

    private val lock = Mutex()
    @Volatile private var activeJob: Job? = null

    suspend fun sync(profileId: Long): Boolean {
        if (!lock.tryLock()) return false
        if (offlineOnly(profileId)) {
            mutableProgress.value = SyncProgress(error = com.walktape.app.core.net.ApiError.Unreachable)
            lock.unlock()
            return false
        }
        return try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    activeJob = currentCoroutineContext()[Job]
                    index(profileId)
                }
            }
        } finally {
            activeJob = null
            if (mutableProgress.value.running) {
                mutableProgress.value = mutableProgress.value.copy(running = false)
            }
            lock.unlock()
        }
    }

    private suspend fun index(profileId: Long): Boolean {
        val client = clientProvider(profileId) ?: run {
            mutableProgress.value = SyncProgress(error = ApiError.CredentialsUnavailable)
            return false
        }
        mutableProgress.value = SyncProgress(running = true)
        val artistsResult = client.artists()
        if (artistsResult is ApiResult.Failure) {
            mutableProgress.value = SyncProgress(error = artistsResult.error)
            return false
        }
        val artistRows = (artistsResult as ApiResult.Success).value.map { it.toEntity(profileId) }
        libraryDao.upsertArtists(artistRows)

        val genresResult = client.genres()
        if (genresResult is ApiResult.Failure) {
            mutableProgress.value = SyncProgress(error = genresResult.error)
            return false
        }
        val genreRows = (genresResult as ApiResult.Success).value.map {
            GenreEntity(profileId, it.name, it.name, it.songCount, it.albumCount)
        }
        libraryDao.upsertGenres(genreRows)

        val allAlbums = mutableListOf<AlbumDto>()
        var offset = 0
        while (true) {
            val page = client.albumList("alphabeticalByName", PAGE_SIZE, offset)
            if (page is ApiResult.Failure) {
                mutableProgress.value = SyncProgress(error = page.error)
                return false
            }
            val albums = (page as ApiResult.Success).value
            if (albums.isEmpty()) break
            allAlbums.addAll(albums)
            libraryDao.upsertAlbums(albums.map { it.toEntity(profileId) })
            if (albums.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }

        val done = AtomicInteger(0)
        val total = allAlbums.size
        mutableProgress.value = SyncProgress(running = true, processed = 0, total = total)
        val failure = AtomicReference<ApiError?>(null)
        coroutineScope {
            for (batch in allAlbums.chunked(PARALLELISM)) {
                if (failure.get() != null) break
                batch.map { album ->
                async {
                        when (val detail = client.album(album.id)) {
                            is ApiResult.Success -> {
                                libraryDao.replaceAlbum(profileId, detail.value.album.toEntity(profileId).copy(played = detail.value.album.played ?: album.played),
                                    detail.value.songs.map { song ->
                                        song.toEntity(profileId).copy(
                                            albumId = song.albumId ?: album.id,
                                            albumArtist = song.albumArtist ?: detail.value.album.artist)
                                    })
                            }
                            is ApiResult.Failure -> failure.compareAndSet(null, detail.error)
                        }
                        val processedNow = done.incrementAndGet()
                        if (processedNow % PROGRESS_STEP == 0) {
                            mutableProgress.value = SyncProgress(running = true, processed = processedNow, total = total)
                        }
                }
                }.awaitAll()
            }
        }
        val error = failure.get()
        if (error != null) {
            mutableProgress.value = SyncProgress(processed = done.get(), total = total, error = error)
            return false
        }

        val keep = allAlbums.map { it.id }
        libraryDao.missingAlbumIds(profileId, keep).takeIf { it.isNotEmpty() }?.let { missing ->
            libraryDao.deleteSongsInAlbums(profileId, missing)
            libraryDao.deleteAlbums(profileId, missing)
        }

        val songs = libraryDao.songCount(profileId)
        val albums = libraryDao.albumCount(profileId)
        libraryDao.replaceArtistsAndGenres(profileId, artistRows, genreRows)
        playlistIndex?.refresh(profileId, client)?.let { error ->
            mutableProgress.value = SyncProgress(processed = done.get(), total = total, error = error)
            return false
        }
        libraryDao.upsertState(LibraryStateEntity(
            profileId = profileId,
            lastSyncAt = System.currentTimeMillis(),
            lastSyncOk = true,
            artistCount = artistRows.size,
            albumCount = albums,
            songCount = songs,
        ))
        mutableProgress.value = SyncProgress(processed = done.get(), total = total, finishedAt = System.currentTimeMillis())
        afterSync?.invoke(profileId)
        return true
    }

    fun cancel() { activeJob?.cancel() }

    private fun ArtistDto.toEntity(profileId: Long) = ArtistEntity(profileId, id, name, coverArt, albumCount, starred)

    private fun AlbumDto.toEntity(profileId: Long) = AlbumEntity(
        profileId = profileId, id = id, name = name, artist = artist, artistId = artistId,
        coverArt = coverArt, songCount = songCount, duration = duration, year = year,
        genre = genre, created = created, isCompilation = isCompilation, starred = starred, played = played,
    )

    private fun SongDto.toEntity(profileId: Long) = SongEntity(
        profileId = profileId, id = id, parent = parent, albumId = albumId, artistId = artistId,
        title = title, album = album, artist = artist, albumArtist = albumArtist,
        displayArtist = displayArtist ?: artist, track = track, discNumber = discNumber,
        year = year, genre = genre, coverArt = coverArt, duration = duration, bitRate = bitRate,
        suffix = suffix, contentType = contentType, size = size, path = path, created = created,
        starred = starred, type = type,
    )

    companion object {
        private const val PAGE_SIZE = 500
        private const val PARALLELISM = 4
        private const val PROGRESS_STEP = 10
    }
}
