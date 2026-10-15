package com.walktape.app.playback

import androidx.core.net.toUri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.AlbumDto
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.SongDto
import com.walktape.app.core.net.parseAlbum
import com.walktape.app.core.net.parseSong

/** Shared helpers for real-server playback tests: never embeds or prints credentials. */

internal suspend fun playbackCandidates(client: OpenSubsonicClient): List<SongDto> {
    val found = mutableMapOf<String, SongDto>()
    repeat(5) {
        val result = client.request("getRandomSongs", listOf("size" to "500"))
        if (result is ApiResult.Success) {
            result.value.getAsJsonObject("randomSongs")?.getAsJsonArray("song")?.forEach {
                val song = parseSong(it.asJsonObject)
                if (song.duration > 30 && song.suffix in listOf("mp3", "flac")) found.putIfAbsent(song.suffix!!, song)
            }
        }
        if (found.size == 2) return listOf(found.getValue("mp3"), found.getValue("flac"))
    }
    return emptyList()
}

/** A real album with several playable songs, for the cassette label and cover checks. */
internal suspend fun albumSongs(client: OpenSubsonicClient): Pair<AlbumDto, List<SongDto>>? {
    repeat(3) {
        val result = client.request("getAlbumList2", listOf("type" to "newest", "size" to "40"))
        if (result is ApiResult.Success) {
            val albums = result.value.getAsJsonObject("albumList2")?.getAsJsonArray("album") ?: return null
            for (index in 0 until albums.size()) {
                val album = parseAlbum(albums.get(index).asJsonObject)
                if (album.songCount !in 2..12) continue
                val detail = client.request("getAlbum", listOf("id" to album.id))
                if (detail is ApiResult.Success) {
                    val songs = detail.value.getAsJsonObject("album")?.getAsJsonArray("song")
                        ?.map { parseSong(it.asJsonObject) }.orEmpty()
                    val usable = songs.filter {
                        it.suffix in listOf("mp3", "flac") && it.duration > 60 &&
                            !it.album.isNullOrBlank() && !it.coverArt.isNullOrBlank()
                    }
                    if (usable.size >= 2) return album to usable
                }
            }
        }
    }
    return null
}

internal fun SongDto.toSongEntity(profileId: Long) = com.walktape.app.core.db.SongEntity(
    profileId = profileId, id = id, parent = parent, albumId = albumId, artistId = artistId,
    title = title, album = album, artist = artist, albumArtist = albumArtist,
    displayArtist = displayArtist ?: artist, track = track, discNumber = discNumber,
    year = year, genre = genre, coverArt = coverArt, duration = duration, bitRate = bitRate,
    suffix = suffix, contentType = contentType, size = size, path = path, created = created,
    starred = starred, type = type,
)

internal fun SongDto.toQueueItem(profileId: Long): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(SubsonicStream.uri(profileId, id).toUri())
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setDurationMs(duration * 1000L)
            .setExtras(Bundle().apply { putString(SleepTimer.ALBUM_ID, albumId) }).build()
    )
    .build()
