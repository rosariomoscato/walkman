package com.walktape.app.core.library

import com.walktape.app.core.db.LibraryDao
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.sync.OfflineOps
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Favorites (no stars in the UI): serialize writes and update Room only after server ACK.
 * W08-T06: while offline (or on transient failures) the toggle is applied locally and
 * recorded in the durable queue — the pending state is visible in the app — while
 * permission/auth failures remain visible errors and never count as success.
 */
class StarRepository(
    private val libraryDao: LibraryDao,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
    private val ops: OfflineOps? = null,
    private val offlineOnly: (Long) -> Boolean = { false },
    private val commitLocal: suspend (suspend () -> Unit) -> Unit = { it() },
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
        val client = clientProvider(profileId) ?: return@withLock when {
            queueStar(profileId, kind, id, starred, timestamp) -> null
            else -> ApiError.CredentialsUnavailable
        }
        if (offlineOnly(profileId)) {
            return@withLock when {
                queueStar(profileId, kind, id, starred, timestamp) -> null
                else -> ApiError.InvalidRequest
            }
        }
        val result = when (kind) {
            Kind.SONG -> if (starred) client.star(songIds = listOf(id)) else client.unstar(songIds = listOf(id))
            Kind.ALBUM -> if (starred) client.star(albumIds = listOf(id)) else client.unstar(albumIds = listOf(id))
            Kind.ARTIST -> if (starred) client.star(artistIds = listOf(id)) else client.unstar(artistIds = listOf(id))
        }
        when (result) {
            is ApiResult.Success -> { applyLocal(profileId, kind, id, timestamp); null }
            is ApiResult.Failure -> when {
                isTransient(result.error) && queueStar(profileId, kind, id, starred, timestamp) -> null
                else -> result.error
            }
        }
    }

    /** Idempotent offline favorite: local value now, durable star/unstar op for later. */
    private suspend fun queueStar(profileId: Long, kind: Kind, id: String,
                                  starred: Boolean, timestamp: String?): Boolean {
        val ops = ops ?: return false
        commitLocal {
            applyLocal(profileId, kind, id, timestamp)
            ops.queueStar(profileId, kind.name, id, starred)
        }
        return true
    }

    private fun isTransient(error: ApiError): Boolean = when (error) {
        ApiError.Unreachable, ApiError.Timeout, ApiError.Network, ApiError.Tls -> true
        is ApiError.Http -> error.status >= 500
        is ApiError.Protocol -> error.code == 0
        else -> false
    }

    private suspend fun applyLocal(profileId: Long, kind: Kind, id: String, timestamp: String?) {
        when (kind) {
            Kind.SONG -> libraryDao.setSongStarred(profileId, id, timestamp)
            Kind.ALBUM -> libraryDao.setAlbumStarred(profileId, id, timestamp)
            Kind.ARTIST -> libraryDao.setArtistStarred(profileId, id, timestamp)
        }
    }
}
