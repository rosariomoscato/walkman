package com.walktape.app.playback

import android.content.ComponentName
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.core.net.Credentials
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Continuation suggestions: gated by the opt-in setting, parsed from getSimilarSongs2,
 * offered only when the server actually provides them and applied through the real
 * session so the queue identity and shuffle reconciliation stay intact.
 */
@RunWith(AndroidJUnit4::class)
class ContinuationSuggestionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun similarBody(songsJson: String) =
        """{"subsonic-response":{"status":"ok","version":"1.16.1","similarSongs2":{"song":[$songsJson]}}}"""

    private fun fixtureItem(profileId: Long, songId: String): MediaItem = MediaItem.Builder()
        .setMediaId(songId)
        .setUri(SubsonicStream.uri(profileId, songId).toUri())
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Fixture").build())
        .build()

    private suspend fun connect(): MediaController {
        val future = withContext(Dispatchers.Main) {
            MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
        }
        return future.get(20, TimeUnit.SECONDS)
    }

    @Test
    fun suggestionIsGatedParsedAndAppliedThroughTheSession() = runBlocking {
        val originalSetting = Graph.settingsRepository.settings.first().suggestionsEnabled
        val previous = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(MockResponse.Builder()
                .body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build())
            val added = Graph.profiles.addVerified("Suggestion fixture ${System.nanoTime()}",
                server.url("/").toString(), "fixture", "fixture-only", true)
            assertTrue("Fixture login must succeed", added is ApiResult.Success)
            val id = (added as ApiResult.Success).value
            var controller: MediaController? = null
            try {
                val client = Graph.profiles.clientFor(id)!!

                // Disabled setting: the server is never asked.
                Graph.settingsRepository.setSuggestionsEnabled(false)
                Graph.suggestion.evaluate(client, id, "s1", "Source title")
                delay(700)
                assertEquals("A disabled setting must not query the server", 1, server.requestCount)
                assertTrue(Graph.suggestion.state.value is ContinuationSuggestion.State.Idle)

                // Enabled: parse, drop the source song and unusable entries.
                Graph.settingsRepository.setSuggestionsEnabled(true)
                server.enqueue(MockResponse.Builder().body(similarBody(
                    """{"id":"s1","title":"Self"},{"id":"s2","title":"Keep","duration":200},{"id":"s3","title":"Zero","duration":0}"""
                )).build())
                Graph.suggestion.evaluate(client, id, "s1", "Source title")
                awaitState("Server songs must become a suggestion") { it is ContinuationSuggestion.State.Available }
                val available = Graph.suggestion.state.value as ContinuationSuggestion.State.Available
                assertEquals(listOf("s2"), available.items.map { it.mediaId })
                assertEquals("Source title", available.sourceTitle)

                // Any failure means "not available", never an error surface.
                server.enqueue(MockResponse.Builder().body(
                    """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":"0","message":"no integration"}}}"""
                ).build())
                Graph.suggestion.evaluate(client, id, "s1", "Source title")
                awaitState("Failure must be reported as unavailable") { it is ContinuationSuggestion.State.Unavailable }

                // Apply through the real session on a single-item queue.
                Graph.profiles.activate(id)
                val c = connect()
                controller = c
                withContext(Dispatchers.Main) { c.setMediaItems(listOf(fixtureItem(id, "s0"))) }
                await("Fixture queue normalized before requesting suggestions") {
                    c.mediaItemCount == 1 && c.getMediaItemAt(0).mediaId.startsWith(QueueIdentity.PREFIX)
                }
                server.enqueue(MockResponse.Builder().body(similarBody(
                    """{"id":"s1","title":"Self"},{"id":"s2","title":"Keep","duration":200},{"id":"s4","title":"Also keep","duration":250}"""
                )).build())
                Graph.suggestion.evaluate(client, id, "s1", "Source title")
                awaitState("Second suggestion ready") {
                    it is ContinuationSuggestion.State.Available && it.items.size == 2
                }
                // The apply path drives the process-wide UI bridge, exactly like the panel does.
                withContext(Dispatchers.Main) { Graph.playback.connect() }
                await("Playback bridge connected") { Graph.playback.controller.value != null }
                withContext(Dispatchers.Main) { Graph.playback.applyContinuationSuggestion() }
                delay(700)
                assertTrue("A suggestion must not apply to a queue that has not ended",
                    withContext(Dispatchers.Main) { c.mediaItemCount == 1 && !c.playWhenReady })
                Graph.settingsRepository.setSuggestionsEnabled(false)
                awaitState("Disabling suggestions clears the offered result") { it is ContinuationSuggestion.State.Idle }
            } finally {
                withContext(Dispatchers.Main) { Graph.playback.disconnect(); controller?.pause(); controller?.release() }
                Graph.suggestion.reset()
                Graph.settingsRepository.setSuggestionsEnabled(originalSetting)
                if (previous != null) Graph.profiles.activate(previous)
                Graph.profiles.remove(id)
                Graph.queues.flush()
            }
        }
    }

    @Test
    fun realQueueEndQueriesContinuationWithoutStartingIt() = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue("Opt-in server configuration required", env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val originalSetting = Graph.settingsRepository.settings.first().suggestionsEnabled
        val previous = Graph.profiles.profiles.first().firstOrNull { it.isActive }?.id
        val added = Graph.profiles.addVerified("Suggestion real ${System.nanoTime()}",
            config.getValue("ND_URL").replace("localhost", "10.0.2.2"),
            config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
        assertTrue("Real login must succeed (details suppressed)", added is ApiResult.Success)
        val id = (added as ApiResult.Success).value
        var controller: MediaController? = null
        try {
            Graph.settingsRepository.setSuggestionsEnabled(true)
            Graph.profiles.activate(id)
            val songs = playbackCandidates(Graph.profiles.clientFor(id)!!)
            assertTrue("Need one real track", songs.isNotEmpty())
            val c = connect()
            controller = c
            withContext(Dispatchers.Main) {
                c.setMediaItems(listOf(songs.first().toQueueItem(id)))
                c.prepare()
                c.play()
            }
            await("Real stream must be playing") { c.isPlaying && c.duration > 0 }
            withContext(Dispatchers.Main) { c.seekTo((c.duration - 4_500).coerceAtLeast(0)) }
            await("Queue must reach its end") { c.playbackState == androidx.media3.common.Player.STATE_ENDED }
            // Whatever the real server answers, the evaluation completes and playback stays stopped.
            awaitState("Suggestion evaluation must complete at queue end") {
                it is ContinuationSuggestion.State.Unavailable || it is ContinuationSuggestion.State.Available
            }
            assertFalse("No playback restart at queue end", withContext(Dispatchers.Main) { c.isPlaying })
            // Positive recommendation fixture containing a REAL FLAC id. Audio still comes
            // from Navidrome, not the fixture; no fake bytes can count as playback proof.
            MockWebServer().apply { start() }.use { recommendations ->
                val nextSong = songs.last()
                recommendations.enqueue(MockResponse.Builder().body(similarBody(Gson().toJson(mapOf(
                    "id" to nextSong.id, "title" to nextSong.title, "duration" to nextSong.duration,
                    "albumId" to nextSong.albumId, "album" to nextSong.album, "artist" to nextSong.artist
                )))).build())
                val fixtureClient = OpenSubsonicClient(ServerAddress.parse(recommendations.url("/").toString(), true)!!,
                    Credentials("fixture", "fixture-only"))
                withContext(Dispatchers.Main) {
                    Graph.suggestion.evaluate(fixtureClient, id, songs.first().id, "Real source")
                    Graph.playback.connect()
                }
                awaitState("Positive fixture recommendation must be available") { it is ContinuationSuggestion.State.Available }
                await("UI bridge connected") { Graph.playback.controller.value != null }
                withContext(Dispatchers.Main) {
                    Graph.playback.applyContinuationSuggestion()
                    Graph.playback.applyContinuationSuggestion()
                }
                await("Accepted continuation must actually stream the real FLAC once") {
                    c.mediaItemCount == 2 && c.currentMediaItemIndex == 1 && c.isPlaying && c.currentPosition > 500 &&
                        c.currentMediaItem?.mediaMetadata?.extras?.getString(QueueIdentity.SONG) == nextSong.id
                }
                assertTrue(withContext(Dispatchers.Main) { c.getMediaItemAt(1).mediaId.startsWith(QueueIdentity.PREFIX) })
            }
        } finally {
            withContext(Dispatchers.Main) { Graph.playback.disconnect(); controller?.pause(); controller?.release() }
            Graph.suggestion.reset()
            Graph.settingsRepository.setSuggestionsEnabled(originalSetting)
            if (previous != null) Graph.profiles.activate(previous)
            Graph.profiles.remove(id)
            Graph.queues.flush()
        }
    }

    private suspend fun awaitState(message: String, predicate: (ContinuationSuggestion.State) -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate(Graph.suggestion.state.value)) return
            delay(150)
        }
        throw AssertionError(message)
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
