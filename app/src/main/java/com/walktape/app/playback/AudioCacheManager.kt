package com.walktape.app.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import com.walktape.app.core.settings.AppSettings
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-profile temporary audio cache (W08-T03): Media3 SimpleCache with an LRU evictor and
 * a configurable ceiling (SPEC default 1 GB). One instance per profile directory; cache
 * files are ephemeral and never mixed with explicit downloads, which live in music/.
 * The evictor only touches this directory: downloads are never evicted to recover space.
 */
@UnstableApi
class AudioCacheManager(
    context: Context,
    private val profileDirectory: (Long) -> File,
) {
    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val caches = ConcurrentHashMap<Long, SimpleCache>()
    private val evictors = ConcurrentHashMap<Long, ProtectedCacheEvictor>()
    private val leases = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    @Volatile private var limitBytes: Long = AppSettings.DEFAULT_AUDIO_CACHE_BYTES
    @Volatile private var limitChanged = false

    /** Applies a new ceiling; takes effect on the next (re)creation of each cache. */
    fun updateLimit(bytes: Long) {
        if (bytes <= 0 || bytes == limitBytes) return
        limitBytes = bytes
        caches.forEach { (id, cache) -> synchronized(cache) { evictors[id]?.trim(cache) } }
    }

    @Synchronized
    fun cacheFor(profileId: Long): SimpleCache = caches.getOrPut(profileId) {
        val evictor = ProtectedCacheEvictor({ limitBytes }) { key -> leases[key]?.get()?.let { it > 0 } == true }
        evictors[profileId] = evictor
        SimpleCache(
            File(profileDirectory(profileId), "cache/audio"),
            evictor,
            databaseProvider,
        )
    }

    fun acquire(key: String) { leases.computeIfAbsent(key) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() }
    fun release(profileId: Long, key: String) {
        leases[key]?.decrementAndGet()
        caches[profileId]?.let { cache -> synchronized(cache) { evictors[profileId]?.trim(cache) } }
    }

    /**
     * Releases and reopens the profile cache applying the current limit. The caller must
     * guarantee no open data sources on it (player idle); on init the evictor trims the
     * existing files down to the new ceiling.
     */
    @Synchronized
    fun recreate(profileId: Long): SimpleCache? {
        caches.remove(profileId)?.release()
        limitChanged = false
        return cacheFor(profileId)
    }

    fun limitPending(): Boolean = limitChanged

    /** Honest on-disk accounting for the settings screen. */
    fun bytesOnDisk(profileId: Long): Long {
        val dir = File(profileDirectory(profileId), "cache/audio")
        if (!dir.isDirectory) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Clears the ephemeral audio cache; only safe while no data source is open on it. */
    @Synchronized
    fun clear(profileId: Long): Boolean {
        if (leases.any { (key, count) -> key.startsWith("wtcache/$profileId/") && count.get() > 0 }) return false
        caches.remove(profileId)?.release()
        val dir = File(profileDirectory(profileId), "cache/audio")
        if (!dir.exists()) return true
        return dir.deleteRecursively()
    }
}
