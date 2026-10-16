package com.walktape.app.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import java.io.File
import java.io.IOException

/**
 * Local-first playback source (W08-T04): a wtstream:// media first checks the completed
 * downloads of the active profile; only when no local copy exists the request continues
 * on the wrapped source (temporary cache with authenticated network upstream). Files are
 * the explicit downloads — never evicted — so playback cannot be truncated by cache trim.
 * W09-T01: the local copy is reported with its own representation, and the temporary cache
 * key carries the stream selection so different bytes never mix under the same entry.
 */
@UnstableApi
class LocalFirstDataSource(
    private val activeProfileId: () -> Long?,
    private val localFile: (Long, String) -> File?,
    private val remote: DataSource,
    private val onLocalFile: ((String, Boolean) -> Unit)? = null,
    private val localReducedLookup: ((String) -> Boolean)? = null,
) : DataSource {

    private var active: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val profileId = activeProfileId()
        val uri = dataSpec.uri
        if (profileId == null || SubsonicStream.songId(uri.toString(), profileId) == null)
            throw IOException("Playback profile mismatch")
        if (profileId != null && uri.scheme == SubsonicStream.SCHEME) {
            val songId = SubsonicStream.songId(uri.toString(), profileId)
            if (songId != null) {
                val file = localFile(profileId, songId)
                if (file != null && file.isFile && file.length() > 0) {
                    val local = FileDataSource()
                    val length = local.open(
                        dataSpec.buildUpon().setUri(Uri.fromFile(file)).build())
                    active = local
                    onLocalFile?.invoke(songId, localReducedLookup?.invoke(songId) ?: false)
                    return length
                }
            }
        }
        active = remote
        return remote.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: throw IOException("DataSource not open")

    override fun close() {
        active?.close()
        active = null
    }

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        remote.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = active?.uri
}

/** Factory resolving the active profile's cache and local files per created source. */
@UnstableApi
class LocalFirstDataSourceFactory(
    private val http: okhttp3.OkHttpClient,
    private val userAgent: String,
    private val connection: () -> StreamProfile?,
    private val cacheManager: AudioCacheManager,
    private val localFile: (Long, String) -> File?,
    private val offlineOnly: (Long) -> Boolean,
    private val selectionFor: (Long) -> StreamSelection = { StreamSelection.ORIGINAL },
    private val qualityMonitor: PlaybackQualityMonitor? = null,
    private val localReducedLookup: ((String) -> Boolean)? = null,
) : DataSource.Factory {

    private val baseFactory = SubsonicStreamDataSourceFactory(http, userAgent,
        offlineOnly = offlineOnly, clientProvider = connection,
        selectionFor = selectionFor, qualityMonitor = qualityMonitor)

    override fun createDataSource(): DataSource {
        val profile = connection()
        // Pin one representation for this source's lifetime, including Range retries.
        // A network handoff must never change the bytes underneath an extractor.
        val selection = profile?.let { selectionFor(it.id) } ?: StreamSelection.ORIGINAL
        val pinnedFactory = SubsonicStreamDataSourceFactory(http, userAgent,
            offlineOnly = offlineOnly, clientProvider = connection,
            selectionFor = { selection }, qualityMonitor = qualityMonitor)
        val remote: DataSource = if (selection.reduced) {
            // Transcodes have no strong validator and ignore Range on Navidrome.
            // Do not splice regenerated fragments, even under a distinct cache key.
            // Complete reduced downloads remain local-first and offline-playable.
            pinnedFactory.createDataSource()
        } else if (profile != null) {
            val cache = cacheManager.cacheFor(profile.id)
            val source = androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(cache)
                .setCacheKeyFactory(object : androidx.media3.datasource.cache.CacheKeyFactory {
                    override fun buildCacheKey(dataSpec: DataSpec): String =
                        cacheKey(profile.id, dataSpec, selection)
                })
                .setUpstreamDataSourceFactory(pinnedFactory)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
            object : DataSource by source {
                private var lease: String? = null
                override fun open(dataSpec: DataSpec): Long {
                    if (connection()?.id != profile.id) throw IOException("Playback profile changed")
                    val key = cacheKey(profile.id, dataSpec, selection)
                    SubsonicStream.songId(dataSpec.uri.toString(), profile.id)?.let {
                        qualityMonitor?.requested(it, selection)
                    }
                    cacheManager.acquire(key)
                    lease = key
                    try { return source.open(dataSpec) }
                    catch (error: Exception) { close(); throw error }
                }
                override fun close() {
                    try { source.close() }
                    finally { lease?.let { cacheManager.release(profile.id, it) }; lease = null }
                }
            }
        } else {
            baseFactory.createDataSource()
        }
        return LocalFirstDataSource({ connection()?.id }, localFile, remote,
            onLocalFile = { songId, reduced -> qualityMonitor?.local(songId, reduced) },
            localReducedLookup = localReducedLookup)
    }

    private fun cacheKey(profileId: Long, dataSpec: DataSpec, selection: StreamSelection): String {
        val songId = SubsonicStream.songId(dataSpec.uri.toString(), profileId)
            ?: return "wtcache/$profileId/${dataSpec.uri}"
        return streamCacheKey(profileId, songId, selection)
    }
}
