package com.walktape.app.playback

import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.net.ApiResult
import kotlinx.coroutines.*
import mockwebserver3.*
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Real service/ExoPlayer decoding PCM audio; fixture HTTP server, not Navidrome/hardware. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class ScrobbleServiceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun await(message: String, predicate: suspend () -> Boolean) {
        withTimeout(45_000) { while (!predicate()) delay(150) }
    }
    private suspend fun connect(): MediaController {
        val future = withContext(Dispatchers.Main) {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        }
        return future.get(30, TimeUnit.SECONDS)
    }
    private fun wav(): ByteArray {
        val samples = 20 * 8_000
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { i -> buffer.putShort((kotlin.math.sin(i * Math.PI * 2 * 220 / 8_000) * 300).toInt().toShort()) }
        return buffer.array()
    }

    @Test fun serviceListeningIgnoresSeekPauseAndRebindRecoversOfflineAndRepeat(): Unit = runBlocking {
        val previous = Graph.database.profileDao().active()?.id
        val online = AtomicBoolean(false)
        val acknowledged = AtomicInteger(0)
        val audio = wav().toByteString()
        var id: Long? = null
        var controller: MediaController? = null
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath.substringAfterLast('/')) {
                "stream.view" -> MockResponse.Builder().addHeader("Content-Type","audio/wav").body(okio.Buffer().write(audio)).build()
                "scrobble.view" -> if (online.get()) {
                    acknowledged.incrementAndGet()
                    MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build()
                } else MockResponse.Builder().code(503).build()
                else -> MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build()
            }
        }
        server.start()
        try {
            val added = Graph.profiles.addVerified("Scrobble fixture ${System.nanoTime()}", server.url("/").toString(), "fixture", "fixture-only", true)
            assertTrue(added is ApiResult.Success)
            val profile = (added as ApiResult.Success).value; id = profile
            Graph.queues.enqueue(SavedQueue(profile, listOf(SavedQueueItem("queue/scrobble-fixture", "song", "PCM fixture", "Fixture", null,20_000)),
                listOf("queue/scrobble-fixture"), "queue/scrobble-fixture",0,0,false,1))
            Graph.queues.flush()
            Graph.profiles.activate(profile)
            val c = connect(); controller = c
            await("restore") { withContext(Dispatchers.Main) { c.currentMediaItem?.mediaId == "queue/scrobble-fixture" } }
            assertEquals(0,Graph.database.scrobbleDao().count(profile))
            withContext(Dispatchers.Main) { c.prepare(); c.play() }
            await("playing") { withContext(Dispatchers.Main) { c.isPlaying } }
            delay(2_000)
            withContext(Dispatchers.Main) { c.pause() }
            delay(2_000)
            assertEquals(0,Graph.database.scrobbleDao().count(profile))
            withContext(Dispatchers.Main) { c.seekTo(16_000); c.play() }
            await("end after seek") { withContext(Dispatchers.Main) { c.playbackState == Player.STATE_ENDED } }
            assertEquals("Skipped audio and paused wall time must not earn a listen",0,Graph.database.scrobbleDao().count(profile))
            withContext(Dispatchers.Main) { c.seekTo(0); c.play() }
            await("first earned listen") { Graph.database.scrobbleDao().count(profile) == 1 }
            withContext(Dispatchers.Main) { c.pause() }
            assertEquals(1,Graph.database.scrobbleDao().pending(profile,10).size)
            val first = Graph.database.scrobbleDao().pending(profile,10).single()
            withContext(Dispatchers.Main) { c.release(); Graph.playback.disconnect() }
            controller = null
            context.stopService(Intent(context,PlaybackService::class.java))
            delay(2_000)
            val restored = connect(); controller = restored
            await("restored occurrence") { withContext(Dispatchers.Main) { restored.currentMediaItem?.mediaId == "queue/scrobble-fixture" } }
            assertFalse(withContext(Dispatchers.Main) { restored.playWhenReady })
            withContext(Dispatchers.Main) { restored.prepare(); restored.play() }
            delay(2_000)
            assertEquals("Service recreation and controller rebind must not duplicate the earned pass",1,Graph.database.scrobbleDao().count(profile))
            assertEquals(first.eventId,Graph.database.scrobbleDao().pending(profile,10).single().eventId)
            online.set(true)
            Graph.scrobbleSender.trigger()
            await("ack") { Graph.database.scrobbleDao().pending(profile,10).isEmpty() }
            assertEquals(1,acknowledged.get())
            Graph.scrobbleSender.trigger(); delay(500)
            assertEquals(1,acknowledged.get())
            withContext(Dispatchers.Main) { restored.repeatMode = Player.REPEAT_MODE_ONE; restored.seekTo(19_000); restored.play() }
            await("repeat earns independent pass") { Graph.database.scrobbleDao().count(profile) == 2 }
            withContext(Dispatchers.Main) { restored.pause() }
            await("second ack") { acknowledged.get() == 2 }
        } finally {
            withContext(Dispatchers.Main) { controller?.pause(); controller?.release() }
            if (previous != null) Graph.profiles.activate(previous)
            id?.let { Graph.profiles.remove(it) }
            server.close()
        }
    }
}
