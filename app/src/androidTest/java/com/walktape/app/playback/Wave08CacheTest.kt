package com.walktape.app.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Wave08CacheTest {
    @Test fun dynamicLruProtectsOpenTrackAndNeverEvictsExplicitDownloads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "cache-wave08-${UUID.randomUUID()}").apply { mkdirs() }
        val manager = AudioCacheManager(context) { File(root, "$it") }
        val key = "wtcache/1/a"
        try {
            manager.updateLimit(128 * 1024)
            val cache = manager.cacheFor(1)
            fun put(name: String) {
                val hole = cache.startReadWrite(name, 0, 64 * 1024)
                try {
                    val file = cache.startFile(name, 0, 64 * 1024)
                    file.writeBytes(ByteArray(64 * 1024))
                    cache.commitFile(file, file.length())
                } finally { cache.releaseHoleSpan(hole) }
            }
            val explicit = File(root, "1/music/original.flac").apply {
                parentFile!!.mkdirs(); writeBytes(ByteArray(300 * 1024))
            }
            put(key)
            manager.acquire(key)
            put("wtcache/1/b")
            put("wtcache/1/c")
            assertTrue(cache.isCached(key, 0, 64 * 1024))
            assertTrue(cache.cacheSpace <= 128 * 1024)
            assertFalse(manager.clear(1))
            manager.updateLimit(32 * 1024)
            assertTrue(cache.isCached(key, 0, 64 * 1024)) // leased source wins over immediate trim
            manager.release(1, key)
            assertTrue(cache.cacheSpace <= 32 * 1024)
            assertEquals(300 * 1024L, explicit.length())
            assertTrue(manager.clear(1))
            assertTrue(explicit.exists())
        } finally { manager.clear(1); root.deleteRecursively() }
    }
}
