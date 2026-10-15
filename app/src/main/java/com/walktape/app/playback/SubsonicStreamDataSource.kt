package com.walktape.app.playback

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.randomSalt
import java.io.IOException

/**
 * Resolves wtstream:// queue uris into per-request authenticated stream URLs (fresh token+salt
 * on every open, D17) and rejects JSON error envelopes via the content-type predicate, so the
 * player reports a playback error instead of feeding markup to the decoders. While the profile
 * is in offline-only mode the resolution fails: no network request leaves the app (W08-T04).
 */
data class StreamProfile(val id: Long, val client: OpenSubsonicClient)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SubsonicStreamDataSourceFactory(
    http: okhttp3.OkHttpClient,
    userAgent: String,
    private val offlineOnly: (Long) -> Boolean = { false },
    private val clientProvider: () -> StreamProfile?,
) : DataSource.Factory {

    private val baseFactory = OkHttpDataSource.Factory(http)
        .setUserAgent(userAgent)
        .setContentTypePredicate { contentType -> !contentType.contains("json", ignoreCase = true) }

    override fun createDataSource(): DataSource =
        ResolvingDataSource(baseFactory.createDataSource(), ::resolve)

    private fun resolve(dataSpec: DataSpec): DataSpec {
        val profile = clientProvider() ?: throw IOException("No active profile connection")
        if (offlineOnly(profile.id)) throw IOException("Profile is in offline-only mode")
        val songId = SubsonicStream.songId(dataSpec.uri.toString(), profile.id)
            ?: throw IOException("Unsupported playback uri")
        val streamUrl = profile.client.streamUrl(songId, randomSalt()).toString()
        return dataSpec.withUri(Uri.parse(streamUrl))
    }
}
