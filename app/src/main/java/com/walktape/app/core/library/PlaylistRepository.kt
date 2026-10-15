package com.walktape.app.core.library

import com.walktape.app.core.db.BrowseDao
import com.walktape.app.core.db.LibraryDao
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ProtocolErrorKind
import com.walktape.app.sync.OfflineOps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mutating playlist operations against the server, updating the local cache after each
 * acknowledged change. Order and duplicate occurrences are preserved: adds append in the
 * given order and reordering rebuilds the whole list (updatePlaylist has no move).
 *
 * W08-T06: while offline (or on transient network failures) edits are applied to the
 * local cache and recorded in the durable per-profile queue; the UI shows the pending
 * state. Permission/auth failures are never treated as success and stay visible errors.
 */
class PlaylistRepository(
    private val dao: BrowseDao,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
    private val ownerProvider: suspend (Long) -> String? = { null },
    private val libraryDao: LibraryDao? = null,
    private val ops: OfflineOps? = null,
    private val offlineOnly: (Long) -> Boolean = { false },
    private val commitLocal: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    private val mutex = Mutex()

    suspend fun create(profileId: Long, name: String, songIds: List<String>): ApiError? {
        if (offlineOnly(profileId)) {
            return if (queueCreateOffline(profileId, name, songIds)) null else ApiError.InvalidRequest
        }
        val client = clientProvider(profileId) ?: run {
            return if (queueCreateOffline(profileId, name, songIds)) null
            else ApiError.CredentialsUnavailable
        }
        if (offlineOnly(profileId)) {
            return if (queueCreateOffline(profileId, name, songIds)) null
            else ApiError.InvalidRequest
        }
        return when (val result = client.createPlaylist(name, songIds)) {
            is ApiResult.Failure ->
                if (isTransient(result.error) && queueCreateOffline(profileId, name, songIds, attempted = true)) null
                else result.error
            is ApiResult.Success -> {
                val p = result.value.playlist
                dao.replacePlaylist(entityOf(profileId, p), result.value.entries.mapIndexed { position, song ->
                    entryOf(profileId, p.id, position, song.id, song.title,
                        song.displayArtist?.takeIf { it.isNotBlank() } ?: song.artist,
                        song.album, song.duration, song.year, song.genre)
                })
                null
            }
        }
    }

    suspend fun rename(profileId: Long, playlistId: String, newName: String): ApiError? {
        val cached = dao.playlistOnce(profileId, playlistId)
        if (cached?.pendingSync == true || cached?.let(::isLocalPendingOnly) == true || offlineOnly(profileId)) {
            return renameOffline(profileId, playlistId, newName, cached)
        }
        return mutateAndRefresh(profileId, playlistId, fallback = {
            renameOffline(profileId, playlistId, newName, cached)
        }) { it.updatePlaylist(playlistId, name = newName) }
    }

    suspend fun delete(profileId: Long, playlistId: String): ApiError? {
        val cached = dao.playlistOnce(profileId, playlistId)
        if (cached?.pendingSync == true || cached?.let(::isLocalPendingOnly) == true || offlineOnly(profileId)) {
            return deleteOffline(profileId, playlistId)
        }
        val client = clientProvider(profileId) ?: return ApiError.CredentialsUnavailable
        val p = when (val result = client.playlist(playlistId)) {
            is ApiResult.Failure ->
                return when {
                    isTransient(result.error) && deleteOffline(profileId, playlistId) == null -> null
                    else -> result.error
                }
            is ApiResult.Success -> result.value.playlist
        }
        if (p.readOnly || ownerProvider(profileId)?.let { it != p.owner } == true)
            return ApiError.Protocol(50, ProtocolErrorKind.FORBIDDEN)
        return when (val result = client.deletePlaylist(playlistId)) {
            is ApiResult.Failure -> result.error
            is ApiResult.Success -> { dao.deletePlaylist(profileId, playlistId); null }
        }
    }

    suspend fun addSongs(profileId: Long, playlistId: String, songIds: List<String>): ApiError? {
        if (songIds.isEmpty()) return null
        val cached = dao.playlistOnce(profileId, playlistId)
        val baseline = dao.queueEntries(profileId, playlistId).map { it.songId }
        if (cached?.pendingSync == true || cached?.let(::isLocalPendingOnly) == true || offlineOnly(profileId)) {
            return setContentOffline(profileId, playlistId, cached, baseline, baseline + songIds)
        }
        return mutateAndRefresh(profileId, playlistId, fallback = {
            setContentOffline(profileId, playlistId, cached, baseline, baseline + songIds)
        }) {
            it.updatePlaylist(playlistId, songIdsToAdd = songIds)
        }
    }

    suspend fun removeEntry(profileId: Long, playlistId: String, position: Int): ApiError? {
        val cached = dao.playlistOnce(profileId, playlistId)
        val baseline = dao.queueEntries(profileId, playlistId).map { it.songId }
        if (position !in baseline.indices) return ApiError.InvalidRequest
        val edited = baseline.toMutableList().apply { removeAt(position) }
        if (cached?.pendingSync == true || cached?.let(::isLocalPendingOnly) == true || offlineOnly(profileId)) {
            return setContentOffline(profileId, playlistId, cached, baseline, edited)
        }
        return mutateAndRefresh(profileId, playlistId, positional = true, fallback = {
            setContentOffline(profileId, playlistId, cached, baseline, edited)
        }) {
            it.updatePlaylist(playlistId, songIndexesToRemove = listOf(position))
        }
    }

    /** Manual reorder rebuilds the list server-side: remove all indexes, re-add in the new order. */
    suspend fun reorder(profileId: Long, playlistId: String, orderedSongIds: List<String>): ApiError? {
        val cached = dao.playlistOnce(profileId, playlistId)
        val baseline = dao.queueEntries(profileId, playlistId).map { it.songId }
        if (baseline.size != orderedSongIds.size) return ApiError.InvalidRequest
        if (baseline.groupingBy { it }.eachCount() != orderedSongIds.groupingBy { it }.eachCount())
            return ApiError.InvalidRequest
        if (cached?.pendingSync == true || cached?.let(::isLocalPendingOnly) == true || offlineOnly(profileId)) {
            return setContentOffline(profileId, playlistId, cached, baseline, orderedSongIds)
        }
        return mutateAndRefresh(profileId, playlistId, positional = true, fallback = {
            setContentOffline(profileId, playlistId, cached, baseline, orderedSongIds)
        }) {
            it.updatePlaylist(playlistId, songIdsToAdd = orderedSongIds,
                songIndexesToRemove = baseline.indices.toList())
        }
    }

    // --- offline application (local truth + durable queue entry) ---

    /** A local placeholder created offline has no server id yet. */
    private fun isLocalPendingOnly(playlist: PlaylistEntity): Boolean = playlist.id.startsWith("local-")

    private suspend fun queueCreateOffline(profileId: Long, name: String, songIds: List<String>, attempted: Boolean = false): Boolean {
        val ops = ops ?: return false
        val library = libraryDao ?: return false
        val localId = OfflineOps.localPlaylistId()
        val now = System.currentTimeMillis()
        val username = ownerProvider(profileId)
        val metadata = songIds.chunked(500).flatMap { library.songsByIds(profileId, it) }.associateBy { it.id }
        if (songIds.any { it !in metadata }) return false // nothing silently invented
        commitLocal {
        dao.replacePlaylist(PlaylistEntity(profileId, localId, name, username, false,
            null, null, songIds.size, songIds.sumOf { metadata.getValue(it).duration.toLong() }.toInt(),
            null, null, now, false, pendingSync = true),
            songIds.mapIndexed { position, song ->
                val meta = metadata.getValue(song)
                PlaylistEntryEntity(profileId, localId, position, song, meta.title,
                    meta.displayArtist ?: meta.artist, meta.album, meta.duration, meta.year, meta.genre)
            })
        ops.queueCreate(profileId, localId, name, songIds, attempted)
        }
        return true
    }

    private suspend fun renameOffline(profileId: Long, playlistId: String, newName: String,
                                       cached: PlaylistEntity?): ApiError? {
        val ops = ops ?: return ApiError.InvalidRequest
        val playlist = cached ?: return ApiError.InvalidRequest
        if (playlist.readOnly || playlist.owner != ownerProvider(profileId))
            return ApiError.Protocol(50, ProtocolErrorKind.FORBIDDEN)
        commitLocal {
        dao.replacePlaylist(playlist.copy(name = newName, pendingSync = true),
            dao.queueEntries(profileId, playlistId))
        ops.queueRename(profileId, playlistId, playlist.name, newName, playlist.changed)
        }
        return null
    }

    private suspend fun deleteOffline(profileId: Long, playlistId: String): ApiError? {
        val ops = ops ?: return ApiError.InvalidRequest
        val local = dao.playlistOnce(profileId, playlistId) ?: return ApiError.InvalidRequest
        if (local.readOnly || local.owner != ownerProvider(profileId))
            return ApiError.Protocol(50, ProtocolErrorKind.FORBIDDEN)
        commitLocal {
        ops.queueDelete(profileId, playlistId)
        dao.deletePlaylist(profileId, playlistId)
        }
        return null
    }

    /**
     * Applies the intended full order locally and queues a SET_CONTENT op: replaying the
     * same intended list is idempotent in effect, which keeps lost-ACK retries safe.
     * The baseline is the pre-edit cached content captured by the caller.
     */
    private suspend fun setContentOffline(profileId: Long, playlistId: String,
                                          cached: PlaylistEntity?, baselineSongIds: List<String>,
                                          orderedSongIds: List<String>): ApiError? {
        val ops = ops ?: return ApiError.InvalidRequest
        val library = libraryDao ?: return ApiError.InvalidRequest
        val playlist = cached ?: return ApiError.InvalidRequest
        if (playlist.readOnly || playlist.owner != ownerProvider(profileId))
            return ApiError.Protocol(50, ProtocolErrorKind.FORBIDDEN)
        val metadata = orderedSongIds.distinct().chunked(500)
            .flatMap { library.songsByIds(profileId, it) }.associateBy { it.id }
        val cachedEntries = dao.queueEntries(profileId, playlistId).associateBy { it.songId }
        if (orderedSongIds.any { it !in metadata && it !in cachedEntries }) return ApiError.InvalidRequest
        commitLocal {
        dao.replacePlaylist(playlist.copy(songCount = orderedSongIds.size, pendingSync = true),
            orderedSongIds.mapIndexed { position, songId ->
                val meta = metadata[songId]
                if (meta == null) cachedEntries.getValue(songId).copy(position = position)
                else PlaylistEntryEntity(profileId, playlistId, position, songId, meta.title,
                    meta.displayArtist ?: meta.artist, meta.album, meta.duration, meta.year, meta.genre)
            })
        ops.queueSetContent(profileId, playlistId, orderedSongIds, playlist.changed, baselineSongIds)
        }
        return null
    }

    private fun isTransient(error: ApiError): Boolean = when (error) {
        ApiError.Unreachable, ApiError.Timeout, ApiError.Network, ApiError.Tls -> true
        is ApiError.Http -> error.status >= 500
        is ApiError.Protocol -> error.code == 0
        else -> false
    }

    private suspend fun mutateAndRefresh(
        profileId: Long,
        playlistId: String,
        positional: Boolean = false,
        fallback: (suspend () -> ApiError?)? = null,
        mutation: suspend (OpenSubsonicClient) -> ApiResult<Unit>,
    ): ApiError? = mutex.withLock {
        val client = clientProvider(profileId) ?: return@withLock ApiError.CredentialsUnavailable
        val current = when (val result = client.playlist(playlistId)) {
            is ApiResult.Failure -> return@withLock when {
                isTransient(result.error) && fallback != null -> fallback()
                else -> result.error
            }
            is ApiResult.Success -> result.value
        }
        if (current.playlist.readOnly || ownerProvider(profileId)?.let { it != current.playlist.owner } == true)
            return@withLock ApiError.Protocol(50, ProtocolErrorKind.FORBIDDEN)
        if (positional) {
            val cached = dao.playlistOnce(profileId, playlistId)
            if (cached?.changed != current.playlist.changed ||
                dao.queueEntries(profileId, playlistId).map { it.songId } != current.entries.map { it.id })
                return@withLock ApiError.InvalidRequest
        }
        when (val result = mutation(client)) {
            is ApiResult.Failure -> return@withLock when {
                isTransient(result.error) && fallback != null -> fallback()
                else -> result.error
            }
            is ApiResult.Success -> when (val refreshed = client.playlist(playlistId)) {
                is ApiResult.Failure -> when {
                    isTransient(refreshed.error) -> null // ACK ok, cache retried later
                    else -> refreshed.error
                }
                is ApiResult.Success -> {
                    val p = refreshed.value.playlist
                    dao.replacePlaylist(entityOf(profileId, p), refreshed.value.entries.mapIndexed { position, song ->
                        entryOf(profileId, p.id, position, song.id, song.title,
                            song.displayArtist?.takeIf { it.isNotBlank() } ?: song.artist,
                            song.album, song.duration, song.year, song.genre)
                    })
                    null
                }
            }
        }
    }

    private fun entityOf(profileId: Long, p: com.walktape.app.core.net.PlaylistDto) =
        PlaylistEntity(profileId, p.id, p.name, p.owner, p.isPublic, p.comment, p.coverArt,
            p.songCount, p.duration, p.created, p.changed, System.currentTimeMillis(), p.readOnly)

    private fun entryOf(profileId: Long, playlistId: String, position: Int, songId: String,
                        title: String, artist: String?, album: String?, duration: Int,
                        year: Int?, genre: String?) =
        PlaylistEntryEntity(profileId, playlistId, position, songId, title, artist, album, duration, year, genre)
}
