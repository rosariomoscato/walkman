package com.walktape.app.playback

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Fixture login only; restored queues must never request streaming or automatically play. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class QueueRestoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker get() = File(context.cacheDir, "queue-process-test.ids")
    private fun queue(id: Long, prefix: String = "saved", position: Long = 45_678) = SavedQueue(id,
        listOf("a", "b", "c").map { suffix -> SavedQueueItem("queue/$prefix-$suffix",
            if (suffix == "c") "other-song" else "duplicate-song", "$prefix $suffix", "Fixture artist", "Fixture album", 90_000) },
        listOf("queue/$prefix-c", "queue/$prefix-a", "queue/$prefix-b"), "queue/$prefix-b", position, 2, true, 100)

    private suspend fun add(server: MockWebServer, name: String): Long {
        server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build())
        val result = Graph.profiles.addVerified("$name ${System.nanoTime()}", server.url("/").toString(), "fixture", "fixture-only", true)
        assertTrue("Fixture login must succeed", result is ApiResult.Success)
        return (result as ApiResult.Success).value
    }

    private suspend fun connect(): MediaController {
        val future = withContext(Dispatchers.Main) {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        }
        return future.get(20, TimeUnit.SECONDS)
    }

    private suspend fun restored(controller: MediaController, expected: SavedQueue) {
        await("Restored queue must be paused at the exact saved occurrence") {
            controller.mediaItemCount == expected.items.size && controller.currentMediaItem?.mediaId == expected.currentId &&
                controller.currentPosition == expected.positionMs && controller.repeatMode == expected.repeatMode &&
                controller.shuffleModeEnabled == expected.shuffle && !controller.playWhenReady &&
                controller.playbackState == Player.STATE_IDLE
        }
        val actual = withContext(Dispatchers.Main) {
            val ids = mutableListOf<String>()
            val timeline = controller.currentTimeline
            var i = timeline.getFirstWindowIndex(true)
            while (i != androidx.media3.common.C.INDEX_UNSET && ids.size < expected.items.size) {
                ids.add(controller.getMediaItemAt(i).mediaId)
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, true)
            }
            ids
        }
        if (expected.shuffle) assertEquals(expected.traversal, actual)
        assertEquals(expected.items.map { it.entryId }, withContext(Dispatchers.Main) {
            (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId }
        })
        delay(1000)
        assertFalse("Restore must not autoplay", withContext(Dispatchers.Main) { controller.isPlaying || controller.playWhenReady })
    }

    private suspend fun await(message: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (withContext(Dispatchers.Main) { predicate() }) return
            delay(150)
        }
        throw AssertionError(message)
    }

    @Test fun profileSwitchAndServiceRestartRestoreWithoutHttpOrAutoplay() = runBlocking {
        val previous = Graph.database.profileDao().active()?.id
        var a: Long? = null; var b: Long? = null; var controller: MediaController? = null
        MockWebServer().apply { start() }.use { server ->
            try {
                val first = add(server, "Persist A"); a = first
                val second = add(server, "Persist B"); b = second
                val savedA = queue(first)
                val savedB = queue(second, "other", 23_456).copy(shuffle = false,
                    traversal = listOf("queue/other-a", "queue/other-b", "queue/other-c"), repeatMode = 1)
                Graph.queues.enqueue(savedA); Graph.queues.enqueue(savedB); Graph.queues.flush()
                Graph.profiles.activate(first)
                val c = connect(); controller = c
                restored(c, savedA)
                withContext(Dispatchers.Main) { c.seekTo(1, 12_345); c.repeatMode = 0; c.shuffleModeEnabled = false }
                await("Seek must reach the service") { c.currentPosition == 12_345L && !c.shuffleModeEnabled }
                Graph.profiles.activate(second)
                restored(c, savedB)
                Graph.profiles.activate(first)
                val changedA = savedA.copy(positionMs = 12_345, shuffle = false, repeatMode = 0,
                    traversal = savedA.items.map { it.entryId })
                restored(c, changedA)
                // All controllers unbound + stopSelf: onDestroy's final save must precede next load.
                withContext(Dispatchers.Main) { c.release(); Graph.playback.disconnect() }
                controller = null
                context.stopService(Intent(context, PlaybackService::class.java))
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val deadline = System.currentTimeMillis() + 20_000
                while (manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == PlaybackService::class.java.name } &&
                    System.currentTimeMillis() < deadline) delay(150)
                assertFalse("Old service must actually be destroyed before testing restore", manager.getRunningServices(Int.MAX_VALUE)
                    .any { it.service.className == PlaybackService::class.java.name })
                Graph.queues.flush()
                val reopened = connect(); controller = reopened
                restored(reopened, changedA)
                withContext(Dispatchers.Main) { reopened.clearMediaItems() }
                await("Explicit clear must reach the service") { reopened.mediaItemCount == 0 }
                Graph.profiles.activate(second); restored(reopened, savedB)
                Graph.profiles.activate(first)
                await("Empty queue must remain empty after profile restore") { reopened.mediaItemCount == 0 && !reopened.playWhenReady }
                assertEquals("Only two fixture logins; no stream requests during restore", 2, server.requestCount)
            } finally {
                withContext(Dispatchers.Main) { controller?.pause(); controller?.release() }
                if (previous != null) Graph.profiles.activate(previous)
                a?.let { Graph.profiles.remove(it) }; b?.let { Graph.profiles.remove(it) }
                Graph.queues.flush()
            }
        }
    }

    @Test fun seedSeparateProcessRestore() = runBlocking {
        assumeTrue("Process fixture seed is explicitly invoked",
            InstrumentationRegistry.getArguments().getString("queueProcessPhase") == "seed")
        assertFalse("Do not overwrite a pre-existing test marker", marker.exists())
        val previous = Graph.database.profileDao().active()?.id
        MockWebServer().apply { start() }.use { server ->
            val id = add(server, "Process persistence")
            Graph.queues.enqueue(queue(id, "process")); Graph.queues.flush()
            Graph.profiles.activate(id)
            marker.writeText("$id\n${previous ?: -1}\n") // fixture ids only; inside app-private cache.
        }
    }

    @Test fun verifySeparateProcessRestoreAndCleanup() = runBlocking {
        assumeTrue("Process fixture verify is explicitly invoked",
            InstrumentationRegistry.getArguments().getString("queueProcessPhase") == "verify")
        assertTrue("Process fixture marker must exist", marker.exists())
        val ids = marker.readLines().map { it.toLong() }
        var controller: MediaController? = null
        try {
            assertEquals(ids[0], Graph.database.profileDao().active()!!.id)
            val c = connect(); controller = c
            restored(c, queue(ids[0], "process"))
            assertFalse(withContext(Dispatchers.Main) { c.isPlaying || c.playWhenReady })
        } finally {
            withContext(Dispatchers.Main) { controller?.pause(); controller?.release() }
            if (ids[1] > 0) Graph.profiles.activate(ids[1])
            Graph.profiles.remove(ids[0]); Graph.queues.flush()
            marker.delete()
        }
    }
}
