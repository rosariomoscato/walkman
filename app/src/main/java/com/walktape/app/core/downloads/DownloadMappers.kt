package com.walktape.app.core.downloads

import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.db.SongEntity

/**
 * Metadata snapshot -> queue song: a single download stays playable even when the song
 * disappears from the server or from the refreshed index (W08-T05).
 */
fun DownloadEntity.toSongEntity(): SongEntity = SongEntity(
    profileId = profileId,
    id = songId,
    parent = null,
    albumId = albumId,
    artistId = null,
    title = title,
    album = album,
    artist = artist,
    albumArtist = null,
    displayArtist = artist,
    track = 0,
    discNumber = 0,
    year = null,
    genre = null,
    coverArt = coverArt,
    duration = duration,
    bitRate = 0,
    suffix = suffix,
    contentType = contentType,
    size = serverSize.takeIf { it > 0 } ?: totalBytes.takeIf { it > 0 } ?: 0L,
    path = null,
    created = null,
    starred = null,
    type = null,
)
