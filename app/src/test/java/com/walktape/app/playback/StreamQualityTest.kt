package com.walktape.app.playback

import org.junit.Assert.*
import org.junit.Test

/**
 * W09-T01: representation selection is pure and local (no request, no capability probe),
 * cache keys separate originals from every reduced bitrate, and reduced file extensions
 * follow the container the server actually sent.
 */
class StreamQualityTest {

    @Test
    fun `original by default on wifi and when mobile reduction is off`() {
        assertEquals(StreamSelection.ORIGINAL, streamSelection(false, 192, true))
        assertEquals(StreamSelection.ORIGINAL, streamSelection(false, 192, false))
        assertEquals(StreamSelection.ORIGINAL, streamSelection(true, 192, false))
        assertEquals("raw", StreamSelection.ORIGINAL.format)
        assertNull(StreamSelection.ORIGINAL.maxBitRate)
    }

    @Test
    fun `reduced selection only on metered networks with the configured bitrate`() {
        val selection = streamSelection(true, 128, true)
        assertTrue(selection.reduced)
        assertEquals("opus", selection.format)
        assertEquals(128, selection.maxBitRate)
        assertEquals("opus-r128", selection.cacheSuffix)
    }

    @Test
    fun `cache keys never mix representations`() {
        val originalKey = streamCacheKey(7L, "song-1", StreamSelection.ORIGINAL)
        val reduced96 = streamCacheKey(7L, "song-1", streamSelection(true, 96, true))
        val reduced128 = streamCacheKey(7L, "song-1", streamSelection(true, 128, true))
        assertNotEquals(originalKey, reduced96)
        assertNotEquals(reduced96, reduced128)
        assertNotEquals(originalKey, reduced128)
        // different profiles keep distinct entries as before
        assertNotEquals(originalKey, streamCacheKey(8L, "song-1", StreamSelection.ORIGINAL))
        // the same representation is stable across opens
        assertEquals(reduced96, streamCacheKey(7L, "song-1", streamSelection(true, 96, true)))
    }

    @Test
    fun `reduced extensions follow the transferred content type`() {
        assertEquals("ogg", reducedExtension("audio/ogg"))
        assertEquals("ogg", reducedExtension("audio/ogg; charset=binary"))
        assertEquals("ogg", reducedExtension("Audio/Opus"))
        assertEquals("mp3", reducedExtension("audio/mpeg"))
        assertEquals("flac", reducedExtension("audio/flac"))
        assertEquals("audio", reducedExtension(null))
        assertEquals("audio", reducedExtension("application/octet-stream"))
    }

    @Test
    fun `quality monitor reports local copies and honest stream facts`() {
        val monitor = PlaybackQualityMonitor()
        monitor.local("s1", reduced = true)
        monitor.observed("s1", null, null)
        val local = monitor.playbackQuality.value!!
        assertTrue(local.local)
        assertEquals(true, local.localReduced)
        assertNull(local.transcodingUnavailable) // no stream facts to compare

        monitor.requested("s2", streamSelection(true, 96, true))
        monitor.observed("s2", "audio/ogg", 96000)
        val honest = monitor.playbackQuality.value!!
        assertTrue(honest.requestedReduced)
        assertEquals(96, honest.requestedKbps)
        assertEquals(false, honest.transcodingUnavailable)

        // server ignored the reduction and returned the original file
        monitor.observed("s2", "audio/flac", 900000)
        assertEquals(true, monitor.playbackQuality.value!!.transcodingUnavailable)
    }
}
