package com.walktape.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.playback.dsp.ReplayGainParser
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Encoded synthetic FLAC and LAME MP3, not Navidrome library audio or hardware output. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Wave09CodecGaplessTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun decode(file: File, copies: Int): Pair<ShortArray, ReplayGainParser.Tags> {
        val capture = CapturingAudioOutput()
        var tags = ReplayGainParser.Tags()
        val player = withContext(Dispatchers.Main) { TestAudioEngine(context, capture).newPlayer() }
        try {
            withContext(Dispatchers.Main) {
                player.addListener(object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        tracks.groups.forEach { group ->
                            if (group.getTrackFormat(0).sampleMimeType?.startsWith("audio/") == true)
                                tags = ReplayGainParser.parse(group.getTrackFormat(0).metadata)
                        }
                    }
                })
                player.setMediaItems(List(copies) { MediaItem.fromUri(android.net.Uri.fromFile(file)) })
                player.prepare(); player.play()
            }
            withTimeout(25_000) {
                while (!withContext(Dispatchers.Main) {
                    assertNull(player.playerError)
                    player.playbackState == Player.STATE_ENDED
                }) delay(50)
            }
            assertFalse(capture.failed)
            return capture.pcm() to tags
        } finally { withContext(Dispatchers.Main) { player.release() } }
    }

    private fun verifyCodec(codec: String) = runBlocking {
        val file = File("/data/local/tmp/walktape-w09-tone.$codec")
        assumeTrue("Generate-W09AudioFixtures.ps1 + adb push required", file.isFile)
        val single = decode(file, 1)
        val doubled = decode(file, 2)
        android.util.Log.i("W09Codec", "$codec single=${single.first.size} doubled=${doubled.first.size} tags=${single.second}")
        // Media3 flushes the last track's held encoder padding at queue end. The
        // interior transition must DISCARD that padding: adding a second 2-second
        // track must add exactly 88200 frames, not a second copy of the final tail.
        assertTrue(single.first.size >= 88200)
        assertEquals("interior seam adds exactly 2 seconds", single.first.size + 88200, doubled.first.size)
        if (codec == "flac") assertEquals(88200, single.first.size)
        // Compare 20 ms on either side of the seam with the independently decoded file.
        for (offset in 0 until 882) {
            assertEquals(single.first[88200 - 882 + offset], doubled.first[88200 - 882 + offset])
            // Compare the same decoded file after the cold-start sink ramp (~20ms).
            // Even a small inserted gap shifts this waveform and fails the comparison.
            assertEquals(single.first[1323 + offset], doubled.first[88200 + 1323 + offset])
        }
        // The tags really came from these encoded files, not hand-built Metadata objects.
        assertEquals(-6.0206f, requireNotNull(single.second.trackGainDb), 0.01f)
        assertEquals(-3.0103f, requireNotNull(single.second.albumGainDb), 0.01f)
    }

    @Test fun lameMp3DelayPaddingAndReplayGainTags() { verifyCodec("mp3") }
    @Test fun flacSeamAndReplayGainTags() { verifyCodec("flac") }
}
