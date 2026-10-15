package com.walktape.app.playback

import androidx.core.net.toUri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.walktape.app.core.db.SongEntity
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Queue entries use a custom scheme: the resolved, per-request authenticated stream URL never
 * leaks into persisted state, and the profile connection can change without touching the queue.
 */
object SubsonicStream {
    const val SCHEME = "wtstream"
    private const val PREFIX = "$SCHEME://song/"

    fun uri(profileId: Long, songId: String): String {
        require(profileId > 0 && songId.isNotEmpty())
        return PREFIX + profileId + "/" + URLEncoder.encode(songId, "UTF-8").replace("+", "%20")
    }

    /** Returns the song id, or null when the uri does not belong to this scheme. */
    fun songId(uri: String, profileId: Long): String? {
        if (!uri.startsWith(PREFIX)) return null
        val path = uri.removePrefix(PREFIX)
        if (path.substringBefore('/').toLongOrNull() != profileId) return null
        val encoded = path.substringAfter('/', "")
        if (encoded.isEmpty() || encoded.contains('/')) return null
        return try {
            URLDecoder.decode(encoded, "UTF-8").ifEmpty { null }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

fun songMediaItem(song: SongEntity, queueOrigin: String? = null): MediaItem = MediaItem.Builder()
    .setMediaId(song.id)
    .setUri(SubsonicStream.uri(song.profileId, song.id).toUri())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist((song.displayArtist ?: song.artist)?.takeIf { it.isNotBlank() })
            .setAlbumTitle(song.album?.takeIf { it.isNotBlank() })
            .setDurationMs(song.duration * 1000L)
            .setExtras(Bundle().apply {
                putString(SleepTimer.ALBUM_ID, song.albumId)
                song.coverArt?.let { putString(QueueIdentity.COVER_ART, it) }
                queueOrigin?.let { putString(QueueIdentity.ORIGIN, it) }
            })
            .build()
    )
    .build()
