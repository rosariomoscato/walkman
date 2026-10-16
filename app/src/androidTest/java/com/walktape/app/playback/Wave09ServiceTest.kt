package com.walktape.app.playback

import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.net.ApiResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import mockwebserver3.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Actual service, session, timer and listening tracker; synthetic WAV HTTP fixture. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Wave09ServiceTest {
    @Test fun timerDuringOverlapStopsBothDecksWithoutPreloadScrobbles(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Graph.database.profileDao().active()?.id
        val settings = Graph.settingsRepository.settings.first()
        val audio = TestAudioEngine.wav(TestAudioEngine.sine(160_000, 3000, 300))
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath.endsWith("stream.view") -> MockResponse.Builder()
                    .addHeader("Content-Type", "audio/wav").body(okio.Buffer().write(audio)).build()
                request.url.encodedPath.endsWith("scrobble.view") -> MockResponse.Builder().code(503).build()
                else -> MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build()
            }
        }
        server.start()
        var profile: Long? = null
        var controller: MediaController? = null
        suspend fun await(predicate: suspend () -> Boolean) {
            withTimeout(40_000) { while (!predicate()) delay(100) }
        }
        try {
            Graph.settingsRepository.setCrossfadeDurationMs(2000)
            Graph.settingsRepository.setCrossfadeEnabled(true)
            Graph.settingsRepository.setTapeEffectEnabled(true)
            Graph.settingsRepository.setReplayGainMode(com.walktape.app.core.settings.ReplayGainMode.TRACK)
            await { Graph.currentSettings.crossfadeEnabled }
            val result = Graph.profiles.addVerified("W09 service fixture", server.url("/").toString(),
                "fixture", "fixture-only", true)
            assertTrue(result is ApiResult.Success)
            val id = (result as ApiResult.Success).value
            profile = id
            Graph.queues.enqueue(SavedQueue(id, listOf(
                SavedQueueItem("queue/w09-a", "same-song", "A", "fixture", null, 20_000),
                SavedQueueItem("queue/w09-b", "same-song", "B", "fixture", null, 20_000)),
                listOf("queue/w09-a", "queue/w09-b"), "queue/w09-a", 0, 0, false, 1))
            Graph.queues.flush()
            Graph.profiles.activate(id)
            val future = withContext(Dispatchers.Main) {
                MediaController.Builder(context, SessionToken(context,
                    ComponentName(context, PlaybackService::class.java))).buildAsync()
            }
            val c = future.get(30, TimeUnit.SECONDS)
            controller = c
            await { withContext(Dispatchers.Main) { c.currentMediaItem?.mediaId == "queue/w09-a" } }
            withContext(Dispatchers.Main) { c.prepare(); c.play() }
            await { Graph.crossfadeStatus.value.let { it.active && it.incomingPlaying } }
            assertEquals("incoming preparation cannot generate a second listening pass", 1,
                Graph.database.scrobbleDao().count(id))
            withContext(Dispatchers.Main) { Graph.playbackTimer.arm(SleepTimer.Mode.DURATION, 500) }
            await { withContext(Dispatchers.Main) { !c.playWhenReady } && !Graph.crossfadeStatus.value.active }
            assertFalse(Graph.crossfadeStatus.value.incomingPlaying)
            delay(750)
            assertFalse(withContext(Dispatchers.Main) { c.isPlaying })
            assertEquals(1, Graph.database.scrobbleDao().count(id))
            withContext(Dispatchers.Main) { c.seekTo(1, 1000) }
            delay(500)
            assertFalse(withContext(Dispatchers.Main) { c.playWhenReady })
        } finally {
            withContext(Dispatchers.Main) {
                Graph.playbackTimer.cancel()
                controller?.pause(); controller?.release()
            }
            context.stopService(Intent(context, PlaybackService::class.java))
            delay(500)
            Graph.settingsRepository.setCrossfadeEnabled(settings.crossfadeEnabled)
            Graph.settingsRepository.setCrossfadeDurationMs(settings.crossfadeDurationMs)
            Graph.settingsRepository.setTapeEffectEnabled(settings.tapeEffectEnabled)
            Graph.settingsRepository.setReplayGainMode(settings.replayGainMode)
            if (previous != null) Graph.profiles.activate(previous)
            profile?.let { Graph.profiles.remove(it) }
            server.close()
        }
    }
}
