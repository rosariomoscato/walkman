package com.walktape.app.playback

import android.content.ComponentName
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.net.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Real streams: a duration countdown fades the player gain and pauses, track/queue
 * boundaries stop at the right moment, cancel leaves playback alone. The Android media
 * volume is never involved and the gain is always restored.
 */
@RunWith(AndroidJUnit4::class)
class SleepTimerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun durationTrackQueueTimersFadePauseAndCancel() = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue("Opt-in server configuration required", env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val previous = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
        val added = Graph.profiles.addVerified("Timer test ${System.nanoTime()}",
            config.getValue("ND_URL").replace("localhost", "10.0.2.2"),
            config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
        assertTrue("Real login must succeed (details suppressed)", added is ApiResult.Success)
        val id = (added as ApiResult.Success).value
        var controller: MediaController? = null
        try {
            Graph.profiles.activate(id)
            val songs = playbackCandidates(Graph.profiles.clientFor(id)!!)
            assertTrue("Need two real tracks for the boundary checks", songs.size == 2)
            val future = withContext(Dispatchers.Main) {
                MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val c = future.get(20, TimeUnit.SECONDS)
            controller = c
            withContext(Dispatchers.Main) {
                c.setMediaItems(songs.map { it.toQueueItem(id) })
                c.prepare()
                c.play()
            }
            await("Real stream must be playing") { c.isPlaying && c.currentPosition > 1_000 }

            // 1) Duration countdown: fade the gain, pause, restore the gain.
            withContext(Dispatchers.Main) { Graph.playbackTimer.arm(SleepTimer.Mode.DURATION, 2_500) }
            var minVolume = 1f
            await("Countdown must fade and pause") {
                val volume = c.volume
                if (volume < minVolume) minVolume = volume
                !c.playWhenReady && !c.isPlaying && !Graph.playbackTimer.state.value.armed
            }
            assertTrue("Fade must actually lower the gain", minVolume < 0.6f)
            assertEquals("Gain must be restored after the pause", 1f, withContext(Dispatchers.Main) { c.volume })

            // 2) Cancelling disarms without stopping playback.
            withContext(Dispatchers.Main) { c.play() }
            await("Playback resumed after the fired timer") { c.isPlaying }
            withContext(Dispatchers.Main) { Graph.playbackTimer.arm(SleepTimer.Mode.DURATION, 2_000) }
            withContext(Dispatchers.Main) { Graph.playbackTimer.cancel() }
            delay(3_200)
            assertTrue("Cancelled timer must not stop playback", withContext(Dispatchers.Main) { c.isPlaying })
            assertFalse(Graph.playbackTimer.state.value.armed)

            // 3) Track boundary: pause right after the automatic transition.
            awaitDuration(c)
            withContext(Dispatchers.Main) { c.seekTo((c.duration - 6_000).coerceAtLeast(0)) }
            await("Seek near the end applied") { c.currentPosition > c.duration - 7_500 }
            withContext(Dispatchers.Main) { Graph.playbackTimer.arm(SleepTimer.Mode.TRACK) }
            await("Track timer must pause right after the transition") {
                c.currentMediaItemIndex == 1 && !c.playWhenReady && c.volume == 1f &&
                    !Graph.playbackTimer.state.value.armed
            }
            assertTrue("Next track must not have advanced",
                withContext(Dispatchers.Main) { c.currentPosition < 3_000 })

            // 4) Queue boundary: the queue ends with the gain restored.
            awaitDuration(c)
            withContext(Dispatchers.Main) {
                c.seekTo((c.duration - 6_000).coerceAtLeast(0))
                c.play()
            }
            await("Second seek near the end applied") { c.isPlaying && c.currentPosition > c.duration - 7_500 }
            withContext(Dispatchers.Main) { Graph.playbackTimer.arm(SleepTimer.Mode.QUEUE) }
            await("Queue timer must disarm at the end of the queue") {
                c.playbackState == Player.STATE_ENDED && c.volume == 1f &&
                    !Graph.playbackTimer.state.value.armed
            }
            assertFalse("No playback after queue end", withContext(Dispatchers.Main) { c.isPlaying })
        } finally {
            withContext(Dispatchers.Main) {
                Graph.playbackTimer.cancel()
                controller?.pause()
                controller?.release()
            }
            if (previous != null) Graph.profiles.activate(previous)
            Graph.profiles.remove(id)
            Graph.queues.flush()
        }
    }

    private suspend fun awaitDuration(c: MediaController) {
        await("Duration must be known") { c.duration > 0 }
    }

    private suspend fun await(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            if (withContext(Dispatchers.Main) { condition() }) return
            delay(150)
        }
        throw AssertionError(message)
    }
}
