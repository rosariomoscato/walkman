package com.walktape.app.playback

import androidx.core.net.toUri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.SongDto
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

internal fun SongDto.toQueueItem(profileId: Long): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(SubsonicStream.uri(profileId, id).toUri())
    .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).setDurationMs(duration * 1000L)
            .setExtras(Bundle().apply { putString(SleepTimer.ALBUM_ID, albumId) }).build()
    )
    .build()
