package com.walktape.app.core.library

import com.walktape.app.core.db.BrowseDao
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ProtocolErrorKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Mutating playlist operations against the server, updating the local cache after each
 * acknowledged change. Order and duplicate occurrences are preserved: adds append in the
 * given order and reordering rebuilds the whole list (updatePlaylist has no move).
 */
class PlaylistRepository(
    private val dao: BrowseDao,
    private val clientProvider: suspend (Long) -> OpenSubsonicClient?,
    private val ownerProvider: suspend (Long) -> String? = { null },
) {
    private val mutex = Mutex()
    suspend fun create(profileId: Long, name: String, songIds: List<String>): ApiError? {
        val client = clientProvider(profileId) ?: return ApiError.CredentialsUnavailable
        return when (val result = client.createPlaylist(name, songIds)) {
            is ApiResult.Failure -> result.error
            is ApiResult.Success -> {
                val p = result.value.playlist
                dao.replacePlaylist(PlaylistEntity(profileId, p.id, p.name, p.owner, p.isPublic,
                    p.comment, p.coverArt, p.songCount, p.duration, p.created, p.changed,
                    System.currentTimeMillis(), p.readOnly),
                    result.value.entries.mapIndexed { position, song ->
                        PlaylistEntryEntity(profileId, p.id, position, song.id, song.title,
                            song.displayArtist?.takeIf { it.isNotBlank() } ?: song.artist,
                            song.album, song.duration, song.year, song.genre)
                    })
                null
            }
        }
    }

    suspend fun rename(profileId: Long, playlistId: String, newName: String): ApiError? =
        mutateAndRefresh(profileId, playlistId) { it.updatePlaylist(playlistId, name = newName) }

    suspend fun delete(profileId: Long, playlistId: String): ApiError? {
        val client = clientProvider(profileId) ?: return ApiError.CredentialsUnavailable
        val p = when (val result = client.playlist(playlistId)) {
            is ApiResult.Failure -> return result.error
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
        return mutateAndRefresh(profileId, playlistId) {
            it.updatePlaylist(playlistId, songIdsToAdd = songIds)
        }
    }

    suspend fun removeEntry(profileId: Long, playlistId: String, position: Int): ApiError? =
        mutateAndRefresh(profileId, playlistId, positional = true) {
            it.updatePlaylist(playlistId, songIndexesToRemove = listOf(position))
        }

    /** Manual reorder rebuilds the list server-side: remove all indexes, re-add in the new order. */
    suspend fun reorder(profileId: Long, playlistId: String, orderedSongIds: List<String>): ApiError? {
        val entries = dao.queueEntries(profileId, playlistId)
        if (entries.size != orderedSongIds.size) return ApiError.InvalidRequest
        if (entries.groupingBy { it.songId }.eachCount() != orderedSongIds.groupingBy { it }.eachCount())
            return ApiError.InvalidRequest
        return mutateAndRefresh(profileId, playlistId, positional = true) {
            it.updatePlaylist(playlistId, songIdsToAdd = orderedSongIds,
                songIndexesToRemove = entries.indices.toList())
        }
    }

    private suspend fun mutateAndRefresh(
        profileId: Long,
        playlistId: String,
        positional: Boolean = false,
        mutation: suspend (OpenSubsonicClient) -> ApiResult<Unit>,
    ): ApiError? = mutex.withLock {
        val client = clientProvider(profileId) ?: return@withLock ApiError.CredentialsUnavailable
        val current = when (val result = client.playlist(playlistId)) {
            is ApiResult.Failure -> return@withLock result.error
            is ApiResult.Success -> result.value
        }
        if (current.playlist.readOnly || ownerProvider(profileId)?.let { it != current.playlist.owner } == true)
            return@withLock ApiError.Protocol(50, ProtocolErrorKind.FORBIDDEN)
        if (positional) {
            val cached = dao.playlist(profileId, playlistId).first()
            if (cached?.changed != current.playlist.changed ||
                dao.queueEntries(profileId, playlistId).map { it.songId } != current.entries.map { it.id })
                return@withLock ApiError.InvalidRequest
        }
        when (val result = mutation(client)) {
            is ApiResult.Failure -> result.error
            is ApiResult.Success -> when (val refreshed = client.playlist(playlistId)) {
                is ApiResult.Failure -> refreshed.error
                is ApiResult.Success -> {
                    val p = refreshed.value.playlist
                    dao.replacePlaylist(PlaylistEntity(profileId, p.id, p.name, p.owner, p.isPublic,
                        p.comment, p.coverArt, p.songCount, p.duration, p.created, p.changed,
                        System.currentTimeMillis(), p.readOnly),
                        refreshed.value.entries.mapIndexed { position, song ->
                            PlaylistEntryEntity(profileId, p.id, position, song.id, song.title,
                                song.displayArtist?.takeIf { it.isNotBlank() } ?: song.artist,
                                song.album, song.duration, song.year, song.genre)
                        })
                    null
                }
            }
        }
    }
}
