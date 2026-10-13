package com.walktape.app.core.library

import com.walktape.app.core.db.BrowseDao
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient

class PlaylistIndex(private val dao: BrowseDao) {
    suspend fun refresh(profileId: Long, client: OpenSubsonicClient): ApiError? {
        val list = client.playlists()
        if (list is ApiResult.Failure) return list.error
        val playlists = (list as ApiResult.Success).value
        if (playlists.map { it.id }.distinct().size != playlists.size) return ApiError.InvalidResponse
        for (summary in playlists) {
            when (val result = client.playlist(summary.id)) {
                is ApiResult.Failure -> return result.error
                is ApiResult.Success -> {
                    val p = result.value.playlist
                    dao.replacePlaylist(PlaylistEntity(profileId, p.id, p.name, p.owner, p.isPublic,
                        p.comment, p.coverArt, p.songCount, p.duration, p.created, p.changed, System.currentTimeMillis()),
                        result.value.entries.mapIndexed { position, song ->
                            PlaylistEntryEntity(profileId, p.id, position, song.id, song.title,
                                song.displayArtist?.takeIf { it.isNotBlank() } ?: song.artist,
                                song.album, song.duration, song.year, song.genre)
                        })
                }
            }
        }
        dao.prunePlaylists(profileId, playlists.map { it.id })
        return null
    }
}
