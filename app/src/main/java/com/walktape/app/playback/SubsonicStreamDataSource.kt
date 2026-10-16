package com.walktape.app.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.randomSalt
import java.io.IOException

/**
 * Resolves wtstream:// queue uris into per-request authenticated stream URLs (fresh token+salt
 * on every open, D17) and rejects JSON error envelopes via the content-type predicate, so the
 * player reports a playback error instead of feeding markup to the decoders. While the profile
 * is in offline-only mode the resolution fails: no network request leaves the app (W08-T04).
 * The W09-T01 selection decides original vs transcoded bytes per open; the same decision is
 * recorded for the honest playback-quality label and for cache key separation.
 */
data class StreamProfile(val id: Long, val client: OpenSubsonicClient)

/** What the streaming path actually delivered for the current song (W09-T01). */
data class PlaybackQuality(
    val songId: String,
    /** LOCAL / STREAM origin of the bytes being played. */
    val local: Boolean,
    /** The bytes representation requested for this song's last open. */
    val requestedReduced: Boolean,
    val requestedKbps: Int?,
    /** Decoder-side facts when the player has reported the tracks, else null. */
    val actualMime: String?,
    val actualBitrate: Int?,
    val localReduced: Boolean? = null,
) {
    /** True when a reduced stream was requested but the server answered with the original. */
    val transcodingUnavailable: Boolean?
        get() = if (local || actualMime == null) null else requestedReduced &&
            actualMime != "audio/ogg" && actualMime != "audio/opus"
}

/** Volatile, process-wide registry of the representation requested/used per song. */
class PlaybackQualityMonitor {
    private val requested = java.util.concurrent.ConcurrentHashMap<String, StreamSelection>()
    private val locals = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val current = kotlinx.coroutines.flow.MutableStateFlow<PlaybackQuality?>(null)
    val playbackQuality = current as kotlinx.coroutines.flow.StateFlow<PlaybackQuality?>

    fun requested(songId: String, selection: StreamSelection) {
        requested[songId] = selection
        locals.remove(songId)
    }

    fun local(songId: String, reduced: Boolean) {
        locals[songId] = reduced
    }

    /** Player facts (decoded mime/bitrate) merged with the last request for this song. */
    fun observed(songId: String, mime: String?, bitrate: Int?) {
        val localRepresentation = locals[songId]
        if (localRepresentation != null) {
            current.value = PlaybackQuality(songId, true, localRepresentation, null,
                mime, bitrate, localRepresentation)
            return
        }
        val selection = requested[songId] ?: StreamSelection.ORIGINAL
        current.value = PlaybackQuality(songId, local = false,
            requestedReduced = selection.reduced, requestedKbps = selection.maxBitRate,
            actualMime = mime, actualBitrate = bitrate)
    }

    fun reset() {
        requested.clear()
        locals.clear()
        current.value = null
    }

}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubsonicStreamDataSourceFactory(
    http: okhttp3.OkHttpClient,
    userAgent: String,
    private val offlineOnly: (Long) -> Boolean = { false },
    private val selectionFor: (Long) -> StreamSelection = { StreamSelection.ORIGINAL },
    private val qualityMonitor: PlaybackQualityMonitor? = null,
    private val clientProvider: () -> StreamProfile?,
) : DataSource.Factory {

    private val baseFactory = OkHttpDataSource.Factory(http)
        .setUserAgent(userAgent)
        .setContentTypePredicate { contentType ->
            !contentType.contains("json", ignoreCase = true) &&
                !contentType.contains("xml", ignoreCase = true) &&
                !contentType.startsWith("text/", ignoreCase = true)
        }

    override fun createDataSource(): DataSource {
        val upstream = baseFactory.createDataSource()
        return object : DataSource by upstream {
            private var logicalUri: Uri? = null
            private var requested: StreamSelection? = null
            private var delivered: StreamSelection? = null
            private var mime: String? = null
            private var owner: Long? = null

            override fun open(dataSpec: DataSpec): Long {
                val profile = clientProvider() ?: throw IOException("No active profile connection")
                if (offlineOnly(profile.id)) throw IOException("Profile is in offline-only mode")
                if (owner != null && owner != profile.id) throw IOException("Playback profile changed")
                val songId = SubsonicStream.songId(dataSpec.uri.toString(), profile.id)
                    ?: throw IOException("Unsupported playback uri")
                owner = profile.id
                logicalUri = dataSpec.uri
                val selection = requested ?: selectionFor(profile.id).also { requested = it }
                qualityMonitor?.requested(songId, selection)
                fun openAs(representation: StreamSelection): Long {
                    if (offlineOnly(profile.id)) throw IOException("Profile is in offline-only mode")
                    val url = profile.client.streamUrl(songId, randomSalt(),
                        representation.format, representation.maxBitRate)
                    return upstream.open(dataSpec.withUri(Uri.parse(url.toString())))
                }
                val chosen = delivered ?: selection
                val length = try { openAs(chosen) } catch (error: IOException) {
                    val unsupported = error is HttpDataSource.InvalidContentTypeException ||
                        (error is HttpDataSource.InvalidResponseCodeException &&
                            error.responseCode in listOf(400, 404, 415, 501))
                    // A fresh decoder may fall back to the original. A byte-offset
                    // retry must never switch representation beneath that decoder.
                    if (!chosen.reduced || dataSpec.position != 0L || !unsupported) throw error
                    upstream.close()
                    delivered = StreamSelection.ORIGINAL
                    openAs(StreamSelection.ORIGINAL)
                }
                val received = upstream.responseHeaders.entries.firstOrNull {
                    it.key.equals("Content-Type", ignoreCase = true)
                }?.value?.firstOrNull()?.substringBefore(';')?.trim()?.lowercase()
                if (mime != null && received != null && mime != received) {
                    upstream.close()
                    throw IOException("Audio representation changed during Range retry")
                }
                if (received != null) mime = received
                if (chosen.reduced && received != null && received !in listOf("audio/ogg", "audio/opus", "application/ogg")) {
                    delivered = StreamSelection.ORIGINAL
                }
                return length
            }

            // Cache metadata must never retain the authenticated upstream URL.
            override fun getUri(): Uri? = logicalUri
        }
    }
}
