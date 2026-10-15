package com.walktape.app.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * LRU callbacks follow Media3 1.11.1 LeastRecentlyUsedCacheEvictor (Android Open Source
 * Project, Apache-2.0), extended with a dynamic limit and leases for open audio sources.
 * A pinned track can temporarily exceed the ceiling; it is trimmed only after close.
 */
@UnstableApi
internal class ProtectedCacheEvictor(
    private val limit: () -> Long,
    private val pinned: (String) -> Boolean,
) : CacheEvictor {
    private val spans = TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }
        .thenComparator { a, b -> a.compareTo(b) })
    private var bytes = 0L

    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() = Unit
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) trim(cache, length)
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span); bytes += span.length; trim(cache)
    }
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (spans.remove(span)) bytes -= span.length
    }
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan); onSpanAdded(cache, newSpan)
    }
    fun trim(cache: Cache, needed: Long = 0) {
        while (bytes + needed > limit()) {
            val candidate = spans.firstOrNull { !pinned(it.key) } ?: break
            cache.removeSpan(candidate)
        }
    }
}
