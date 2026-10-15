package com.walktape.app.core.library

import com.walktape.app.core.db.LibraryDao
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Favorites (no stars in the UI): serialize writes and update Room only after server ACK.
 * Network/permission failures retain the last confirmed local value.
 */
class StarRepository(
    private val libraryDao: LibraryDao,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
) {
    enum class Kind { SONG, ALBUM, ARTIST }
    private val mutex = Mutex()

    suspend fun setStarred(profileId: Long, kind: Kind, id: String, starred: Boolean): ApiError? = mutex.withLock {
        val exists = when (kind) {
            Kind.SONG -> libraryDao.songsByIds(profileId, listOf(id)).isNotEmpty()
            Kind.ALBUM -> libraryDao.album(profileId, id) != null
            Kind.ARTIST -> libraryDao.artist(profileId, id) != null
        }
        if (!exists) return@withLock ApiError.InvalidRequest
        val timestamp = if (starred) Instant.now().toString() else null
        val client = clientProvider(profileId) ?: return@withLock ApiError.CredentialsUnavailable
        val result = when (kind) {
            Kind.SONG -> if (starred) client.star(songIds = listOf(id)) else client.unstar(songIds = listOf(id))
            Kind.ALBUM -> if (starred) client.star(albumIds = listOf(id)) else client.unstar(albumIds = listOf(id))
            Kind.ARTIST -> if (starred) client.star(artistIds = listOf(id)) else client.unstar(artistIds = listOf(id))
        }
        when (result) {
            is ApiResult.Success -> { applyLocal(profileId, kind, id, timestamp); null }
            is ApiResult.Failure -> result.error
        }
    }

    private suspend fun applyLocal(profileId: Long, kind: Kind, id: String, timestamp: String?) {
        when (kind) {
            Kind.SONG -> libraryDao.setSongStarred(profileId, id, timestamp)
            Kind.ALBUM -> libraryDao.setAlbumStarred(profileId, id, timestamp)
            Kind.ARTIST -> libraryDao.setArtistStarred(profileId, id, timestamp)
        }
    }
}
