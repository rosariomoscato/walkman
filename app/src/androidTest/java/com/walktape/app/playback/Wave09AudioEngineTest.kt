package com.walktape.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.playback.dsp.ReplayGainProcessor
import com.walktape.app.playback.dsp.TapeProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * W09-T06: measures the PCM the real Media3 pipeline renders through the W09 DSP and
 * crossfade engine, using an in-memory sink with real-time throttling. WAV fixtures mean
 * codec delay/padding are absent: these measure the player/engine behaviour, not the
 * MP3 LAME-tag trimming (covered by native extractors) nor device/hardware audio.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class Wave09AudioEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun awaitTrue(message: String, timeoutMs: Long = 30_000, predicate: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!predicate()) delay(50) }
    }

    private fun rms(samples: ShortArray, from: Int = 0, until: Int = samples.size): Double {
        if (until <= from) return 0.0
        var sum = 0.0
        for (i in from until until) sum += (samples[i] / 32768.0) * (samples[i] / 32768.0)
        return sqrt(sum / (until - from))
    }

    @Test fun gaplessWavTransitionHasNoSilenceGap(): Unit = runBlocking {
        val dir = File(context.cacheDir, "wave09-gapless-${System.nanoTime()}")
        val fileA = TestAudioEngine.writeWav(dir, "a.wav", TestAudioEngine.sine(24_000, 12000, 300)) // 3 s
        val fileB = TestAudioEngine.writeWav(dir, "b.wav", TestAudioEngine.sine(16_000, 12000, 500)) // 2 s
        val capture = CapturingAudioOutput()
        val engine = TestAudioEngine(context, capture)
        val player = engine.newPlayer()
        try {
            withContext(Dispatchers.Main) {
                player.setMediaItems(listOf(MediaItem.fromUri(android.net.Uri.fromFile(fileA)),
                    MediaItem.fromUri(android.net.Uri.fromFile(fileB))))
                player.prepare()
                player.play()
            }
            awaitTrue("second item playing") {
                withContext(Dispatchers.Main) { player.currentMediaItemIndex == 1 && player.isPlaying }
            }
            awaitTrue("playback ended") {
                withContext(Dispatchers.Main) { player.playbackState == Player.STATE_ENDED }
            }
            assertFalse(capture.failed)
            // Compare the actual samples across the seam, not player flags or timestamps
            // (Media3 can use TIME_UNSET for draining processor buffers).
            val pcm = capture.pcm()
            val expected = TestAudioEngine.sine(24_000, 12000, 300) +
                TestAudioEngine.sine(16_000, 12000, 500)
            for (i in 23_200 until 24_800) {
                assertEquals("PCM seam at frame $i", expected[i].toInt(), pcm[i].toInt())
            }
            // full audio of both files reached the sink (no truncation at the seam)
            assertEquals((24_000 + 16_000).toLong(), capture.totalFrames)
        } finally {
            withContext(Dispatchers.Main) { player.release() }
            dir.deleteRecursively()
        }
    }

    @Test fun replayGainAttenuatesTheRenderedSignal(): Unit = runBlocking {
        val dir = File(context.cacheDir, "wave09-rg-${System.nanoTime()}")
        val file = TestAudioEngine.writeWav(dir, "a.wav", TestAudioEngine.sine(24_000, 16000, 300))
        val capture = CapturingAudioOutput()
        val gain = ReplayGainProcessor()
        val engine = TestAudioEngine(context, capture, gain = gain)
        val player = engine.newPlayer()
        try {
            gain.enabled = true
            gain.targetGainDb = -6.0206f // exact half amplitude
            withContext(Dispatchers.Main) {
                player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                player.prepare()
                player.play()
            }
            awaitTrue("ended") { withContext(Dispatchers.Main) { player.playbackState == Player.STATE_ENDED } }
            val pcm = capture.pcm()
            // skip the ramp, then amplitude must be halved: rms ≈ half of 16000/32768
            val steadyRms = rms(pcm, 4_000, pcm.size)
            assertTrue("expected ~0.173 rms, got $steadyRms", steadyRms in 0.16..0.19)
        } finally {
            withContext(Dispatchers.Main) { player.release() }
            dir.deleteRecursively()
        }
    }

    @Test fun tapeEffectAddsQuietHissToSilenceAndBypassStaysClean(): Unit = runBlocking {
        val dir = File(context.cacheDir, "wave09-tape-${System.nanoTime()}")
        val file = TestAudioEngine.writeWav(dir, "silence.wav", ShortArray(24_000))
        // enabled: a quiet hiss floor must appear in the rendered PCM
        val captureOn = CapturingAudioOutput()
        val tapeOn = TapeProcessor().apply { enabled = true }
        val playerOn = TestAudioEngine(context, captureOn, tape = tapeOn).newPlayer()
        // disabled: byte-exact bypass, digital silence stays digital silence
        val captureOff = CapturingAudioOutput()
        val playerOff = TestAudioEngine(context, captureOff).newPlayer()
        try {
            withContext(Dispatchers.Main) {
                playerOn.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                playerOn.prepare(); playerOn.play()
                playerOff.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                playerOff.prepare(); playerOff.play()
            }
            awaitTrue("both ended") {
                withContext(Dispatchers.Main) {
                    playerOn.playbackState == Player.STATE_ENDED && playerOff.playbackState == Player.STATE_ENDED
                }
            }
            val hissRms = rms(captureOn.pcm())
            val bypassRms = rms(captureOff.pcm())
            assertTrue("hiss too loud: $hissRms", hissRms in 0.0005..0.006)
            assertEquals("bypass must stay perfectly silent", 0.0, bypassRms, 1e-9)
        } finally {
            withContext(Dispatchers.Main) { playerOn.release(); playerOff.release() }
            dir.deleteRecursively()
        }
    }

    @Test fun crossfadeRendersTwoDeckRampsAndResumesIncomingItem(): Unit = runBlocking {
        val dir = File(context.cacheDir, "wave09-xf-${System.nanoTime()}")
        val fileA = TestAudioEngine.writeWav(dir, "a.wav", TestAudioEngine.sine(40_000, 12000, 300)) // 5 s
        val fileB = TestAudioEngine.writeWav(dir, "b.wav", TestAudioEngine.sine(40_000, 12000, 500)) // 5 s
        val captureA = CapturingAudioOutput()
        val captureB = CapturingAudioOutput()
        val engineA = TestAudioEngine(context, captureA)
        val deckEngine = { TestAudioEngine(context, captureB).newPlayer() }
        val initial = engineA.newPlayer()
        lateinit var main: androidx.media3.exoplayer.ExoPlayer
        var coordinator: CrossfadeCoordinator? = null
        try {
            withContext(Dispatchers.Main) {
                coordinator = CrossfadeCoordinator(kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + Dispatchers.Main.immediate), initial,
                    deckBuilder = deckEngine, enabled = { true }, durationMs = { 2_000 })
                main = coordinator!!.player
                main.setMediaItems(listOf(MediaItem.fromUri(android.net.Uri.fromFile(fileA)),
                    MediaItem.fromUri(android.net.Uri.fromFile(fileB))))
                main.prepare()
                coordinator!!.start()
                main.play()
            }
            // the swap must bring the main deck onto item 2 while playing
            awaitTrue("swap onto second item", 25_000) {
                withContext(Dispatchers.Main) {
                    assertNull(main.playerError)
                    main.currentMediaItemIndex == 1 && main.isPlaying
                }
            }
            awaitTrue("second item advanced past the fade window") {
                withContext(Dispatchers.Main) { main.currentPosition > 1_500 }
            }
            val handoverPosition = withContext(Dispatchers.Main) { main.currentPosition }
            delay(750)
            assertTrue(withContext(Dispatchers.Main) { main.currentPosition >= handoverPosition + 500 })
            assertEquals("incoming output must never be recreated at handover", 1, captureB.outputCreations)
            withContext(Dispatchers.Main) { main.pause(); coordinator!!.release(); main.release() }

            assertFalse(captureA.failed)
            assertFalse(captureB.failed)
            assertTrue("secondary deck must have rendered audio", captureB.totalFrames > 8_000)

            // ramp evidence on both decks, in windows of 0.5 s (4000 frames @8k)
            val a = captureA.pcm().take(40_000).toShortArray()
            val b = captureB.pcm()
            val bodyA = rms(a, 4_000, 20_000)
            val tail1 = rms(a, a.size - 8_000, a.size - 4_000)      // mid fade
            val tail2 = rms(a, a.size - 4_000, a.size)              // last fade window
            val head1 = rms(b, 0, 4_000)                            // fade-in start
            val head2 = rms(b, 4_000, 8_000)                        // fade-in mid
            assertTrue("deck A should fade out: body=$bodyA mid=$tail1 last=$tail2",
                tail1 < bodyA * 0.85 && tail2 < tail1 * 0.75)
            assertTrue("deck B should fade in: first=$head1 second=$head2",
                head2 > head1 * 1.3)
            // Match the two envelopes by projected playout time. Comparing 'last N'
            // with 'first N' would misalign by decoder startup and output buffering.
            val pairings = captureA.allChunks.mapNotNull { outChunk ->
                // AudioTrack release discards queued-but-not-played frames. They are
                // captured for diagnostics, but must not contribute to overlap energy.
                if (outChunk.projectedPlayoutNanos >= minOf(captureA.releasedAtNanos, captureB.releasedAtNanos) - 100_000_000)
                    return@mapNotNull null
                val inChunk = captureB.allChunks.minByOrNull {
                    kotlin.math.abs(it.projectedPlayoutNanos - outChunk.projectedPlayoutNanos)
                } ?: return@mapNotNull null
                val delta = kotlin.math.abs(inChunk.projectedPlayoutNanos - outChunk.projectedPlayoutNanos)
                if (inChunk.projectedPlayoutNanos >= minOf(captureA.releasedAtNanos, captureB.releasedAtNanos) - 100_000_000)
                    return@mapNotNull null
                if (delta > 60_000_000) return@mapNotNull null
                val aLevel = rms(outChunk.pcm)
                val bLevel = rms(inChunk.pcm)
                if (aLevel < bodyA * 0.15 || bLevel < bodyA * 0.15) return@mapNotNull null
                aLevel + bLevel
            }
            assertTrue("at least three measured overlapping PCM windows", pairings.size >= 3)
            // Linear complementary envelopes preserve amplitude sum; power sum dips
            // naturally for independent tones (not a constant-power crossfade).
            val averageSum = pairings.average()
            assertTrue("aligned amplitude sum=$averageSum body=$bodyA", averageSum in bodyA * 0.75..bodyA * 1.25)
        } finally {
            withContext(Dispatchers.Main) { coordinator?.release(); if (main.isPlaying) main.pause(); main.release() }
            dir.deleteRecursively()
        }
    }

    @Test fun systemEffectsControllerReportsHonestState(): Unit = runBlocking {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE)
            as android.media.AudioManager
        val session = audioManager.generateAudioSessionId()
        val controller = com.walktape.app.playback.dsp.SystemEffectsController(context)
        try {
            controller.attach(session)
            android.util.Log.i("W09Effects", controller.state.value.toString())
            when (val state = controller.state.value) {
                is com.walktape.app.playback.dsp.SystemEffectsController.State.Ready -> {
                    assertTrue(state.bands >= 1)
                    assertTrue(state.levelRangeMilliB.first < 0 && state.levelRangeMilliB.last > 0)
                    val ok = controller.apply(true, "0:500", true, 400)
                    assertTrue(ok)
                    val applied = controller.state.value
                        as com.walktape.app.playback.dsp.SystemEffectsController.State.Ready
                    assertTrue(applied.eqEnabled)
                    assertFalse(applied.bassEnabled) // Unknown route fails closed.
                    assertEquals(400, applied.bassStrength)
                    val output = Any()
                    // Route fixtures test effect application, not acoustic headphone response.
                    controller.outputRouteChanged(output,
                        com.walktape.app.playback.dsp.BassBoostRouting.Route.HEADPHONES, true)
                    fun ready() = controller.state.value as
                        com.walktape.app.playback.dsp.SystemEffectsController.State.Ready
                    assertTrue(ready().bassEnabled)
                    controller.outputRouteChanged(output,
                        com.walktape.app.playback.dsp.BassBoostRouting.Route.SPEAKER, true)
                    assertFalse(ready().bassEnabled)
                    assertTrue(ready().eqEnabled)
                    assertEquals(400, ready().bassStrength)
                    controller.outputRouteChanged(output,
                        com.walktape.app.playback.dsp.BassBoostRouting.Route.HEADPHONES, true)
                    assertTrue(ready().bassEnabled) // Request survives without another apply.
                    controller.outputReleased(output)
                    assertFalse(ready().bassEnabled)
                    val csv = controller.currentBandLevelsCsv()
                    assertTrue(csv.startsWith("0:500"))
                }
                is com.walktape.app.playback.dsp.SystemEffectsController.State.Unavailable -> {
                    // honest environment limitation (e.g. no audiofx bundle), not a failure
                    assertTrue(state.reason in com.walktape.app.playback.dsp.SystemEffectsController.State.Reason.entries)
                }
                else -> fail("unexpected idle state after attach")
            }
        } finally {
            controller.release()
            assertEquals(com.walktape.app.playback.dsp.SystemEffectsController.State.Idle, controller.state.value)
        }
    }

    @Test fun combinedGainTapeAndRuntimeBypassKeepHeadroom(): Unit = runBlocking {
        val dir = File(context.cacheDir, "wave09-combined-${System.nanoTime()}")
        val file = TestAudioEngine.writeWav(dir, "tone.wav", TestAudioEngine.sine(32_000, 26000, 300))
        val capture = CapturingAudioOutput()
        val gain = ReplayGainProcessor().apply { enabled = true; targetGainDb = -6.0206f; tagPeak = 1f }
        val tape = TapeProcessor().apply { enabled = true }
        val player = withContext(Dispatchers.Main) { TestAudioEngine(context, capture, gain, tape).newPlayer() }
        try {
            withContext(Dispatchers.Main) {
                player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                player.prepare(); player.play()
            }
            awaitTrue("three seconds processed") { capture.totalFrames >= 24_000 }
            gain.enabled = false
            tape.enabled = false
            awaitTrue("ended") { withContext(Dispatchers.Main) {
                assertNull(player.playerError)
                player.playbackState == Player.STATE_ENDED
            } }
            val pcm = capture.pcm()
            assertEquals(32_000, pcm.size)
            val processedPeak = pcm.take(16_000).maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("gain+tape must retain headroom: $processedPeak", processedPeak in 6000..13200)
            val cleanTail = pcm.takeLast(4000).toShortArray()
            assertTrue(cleanTail.maxOf { kotlin.math.abs(it.toInt()) } > 25000)
            assertTrue("runtime bypass restores clean RMS", rms(cleanTail) in 0.55..0.57)
        } finally {
            withContext(Dispatchers.Main) { player.release() }
            dir.deleteRecursively()
        }
    }
}
