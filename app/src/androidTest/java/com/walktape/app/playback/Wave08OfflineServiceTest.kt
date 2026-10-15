package com.walktape.app.playback

import android.content.ComponentName
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.db.*
import com.walktape.app.core.downloads.DownloadStore
import com.walktape.app.core.net.*
import kotlinx.coroutines.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** PCM WAV fixture through the actual PlaybackService/session; request counter is real HTTP. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Wave08OfflineServiceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun await(message: String, predicate: () -> Boolean) = withTimeout(30_000) {
        while (!withContext(Dispatchers.Main) { predicate() }) delay(100)
    }

    @Test fun servicePlaysDownloadsSkipsMissingAndKeepsListensPendingWithZeroHttp(): Unit = runBlocking {
        val previous = Graph.database.profileDao().active()?.id
        var profile: Long? = null
        var controller: MediaController? = null
        MockWebServer().apply { start() }.use { server ->
            try {
                server.enqueue(MockResponse.Builder().body(
                    """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build())
                val result = Graph.profiles.addVerified("W08 offline fixture ${System.nanoTime()}",
                    server.url("/").toString(), "fixture", "fixture-only", true) as ApiResult.Success
                val id = result.value; profile = id
                val bytes = ByteArray(44 + 44_100 * 2 * 6)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()); putInt(bytes.size - 8); put("WAVEfmt ".toByteArray())
                    putInt(16); putShort(1); putShort(1); putInt(44_100); putInt(88_200)
                    putShort(2); putShort(16); put("data".toByteArray()); putInt(bytes.size - 44)
                }
                val local = SongEntity(id, "local-a", null, "album", null, "Local PCM fixture", "Album",
                    "Fixture", null, null, 1, 1, null, null, null, 6, 705, "wav", "audio/wav",
                    bytes.size.toLong(), null, null, null, null)
                Graph.database.libraryDao().upsertSongs(listOf(local))
                server.enqueue(MockResponse.Builder().addHeader("Content-Type", "audio/wav")
                    .body(okio.Buffer().write(bytes)).build())
                Graph.downloadStore.enqueueSongs(id, listOf(local), "single")
                Graph.database.downloadDao().claimNext(id, 1)
                assertEquals(DownloadStore.Outcome.Completed, Graph.downloadStore.transfer(id, local.id,
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
                Graph.database.downloadDao().upsertSettings(ProfileSettingsEntity(id, offlineOnly = true))
                Graph.profiles.activate(id)
                await("Offline policy must load") { Graph.networkGate.offlineOnly(id) }
                val baselineRequests = server.requestCount
                val future = withContext(Dispatchers.Main) {
                    MediaController.Builder(context, SessionToken(context,
                        ComponentName(context, PlaybackService::class.java))).buildAsync()
                }
                val c = future.get(20, TimeUnit.SECONDS); controller = c
                delay(500)
                val missing = local.copy(id = "missing", title = "Missing offline fixture")
                val skips = Graph.offlineSkips.value
                withContext(Dispatchers.Main) {
                    c.setMediaItems(listOf(songMediaItem(local), songMediaItem(missing), songMediaItem(local)))
                    c.prepare(); c.play()
                }
                await("Downloaded audio must actually play in the service") { c.isPlaying && c.currentPosition > 3500 }
                withTimeout(15_000) {
                    while (Graph.database.scrobbleDao().pending(id, 10).isEmpty()) delay(100)
                }
                withContext(Dispatchers.Main) { if (c.currentMediaItemIndex == 0) c.seekToNextMediaItem() }
                await("Unavailable occurrence must be skipped to its local successor") {
                    c.currentMediaItemIndex == 2 && c.isPlaying && Graph.offlineSkips.value > skips
                }
                assertTrue(Graph.profiles.verify(id) is ApiResult.Failure)
                assertNull(Graph.coverArtStore(id).bitmap("not-cached", 64))
                assertFalse(Graph.librarySync().sync(id))
                Graph.lyricsRepository().lyrics(id, local.id, refresh = true)
                Graph.scrobbleSender.deliverPending()
                assertTrue(Graph.database.scrobbleDao().pending(id, 10).isNotEmpty())
                assertEquals("Offline mode must not create HTTP requests", baselineRequests, server.requestCount)
                withContext(Dispatchers.Main) { c.pause(); c.stop(); c.clearMediaItems() }
                assertFalse(withContext(Dispatchers.Main) { c.playWhenReady })
            } finally {
                controller?.let { c -> withContext(Dispatchers.Main) { c.pause(); c.stop(); c.clearMediaItems(); c.release() } }
                context.stopService(Intent(context, PlaybackService::class.java))
                if (previous != null) Graph.profiles.activate(previous)
                profile?.let { Graph.profiles.remove(it) }
            }
        }
    }
}
