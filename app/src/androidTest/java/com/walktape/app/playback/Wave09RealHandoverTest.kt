package com.walktape.app.playback

import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.net.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Actual Navidrome streams/MediaCodec, no scrobbles or library mutations. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Wave09RealHandoverTest {
    @Test fun mp3FlacAndOpusKeepTheIncomingSourceAndOutputAtPromotion(): Unit = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue(env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        Graph.networkGate.awaitReady()
        val client = OpenSubsonicClient(ServerAddress.parse(config.getValue("ND_URL")
            .replace("localhost", "10.0.2.2"), true)!!,
            Credentials(config.getValue("ND_USER"), config.getValue("ND_PASS")))
        val candidates = playbackCandidates(client)
        assertEquals(2, candidates.size)
        val mp3 = candidates.first { it.suffix == "mp3" }
        val flac = candidates.first { it.suffix == "flac" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for ((a, b, reduced) in listOf(Triple(mp3, flac, false), Triple(flac, mp3, false), Triple(mp3, flac, true))) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val opens = mutableListOf<AtomicInteger>()
            val captures = mutableListOf<CapturingAudioOutput>()
            val decks = mutableListOf<ExoPlayer>()
            var status = CrossfadeCoordinator.Status()
            fun build(): ExoPlayer {
                val ordinal = decks.size
                val count = AtomicInteger().also { opens.add(it) }
                val capture = CapturingAudioOutput().also { captures.add(it) }
                val upstream = SubsonicStreamDataSourceFactory(OpenSubsonicClient.streamingHttpClient(), "WalkTape-test",
                    selectionFor = { if (reduced && ordinal > 0) streamSelection(true, 96, true) else StreamSelection.ORIGINAL },
                    clientProvider = { StreamProfile(1, client) })
                val sources = DataSource.Factory {
                    val delegate = upstream.createDataSource()
                    object : DataSource by delegate {
                        override fun open(dataSpec: DataSpec): Long {
                            count.incrementAndGet()
                            return delegate.open(dataSpec)
                        }
                    }
                }
                return TestAudioEngine(context, capture, sources = sources).newPlayer().also { decks.add(it) }
            }
            val engine = withContext(Dispatchers.Main) {
                CrossfadeCoordinator(scope, build(), ::build, { true }, { 1500 }, report = { status = it })
            }
            suspend fun await(message: String, predicate: suspend () -> Boolean) {
                try { withTimeout(40_000) { while (!predicate()) delay(50) } }
                catch (e: TimeoutCancellationException) { throw AssertionError(message, e) }
            }
            try {
                withContext(Dispatchers.Main) {
                    engine.player.setMediaItems(listOf(a.toQueueItem(1).buildUpon().setMediaId("queue/a").build(),
                        b.toQueueItem(1).buildUpon().setMediaId("queue/b").build()))
                    engine.start(); engine.player.prepare(); engine.player.play()
                }
                await("real source ready") { withContext(Dispatchers.Main) {
                    assertNull(engine.player.playerError)
                    engine.player.isPlaying && engine.player.duration > 16000
                } }
                withContext(Dispatchers.Main) { engine.player.seekTo(engine.player.duration - 15000) }
                await("real incoming audible") { withContext(Dispatchers.Main) { status.incomingPlaying } }
                val sourceOpens = opens[1].get()
                val incoming = withContext(Dispatchers.Main) { decks[1] }
                var lastIncomingPosition = 0L
                await("real incoming promoted") { withContext(Dispatchers.Main) {
                    assertNull(engine.player.playerError)
                    if (engine.player.currentMediaItem?.mediaId != "queue/b")
                        lastIncomingPosition = incoming.currentPosition
                    engine.player.currentMediaItem?.mediaId == "queue/b"
                } }
                val promotedPosition = withContext(Dispatchers.Main) { engine.player.currentPosition }
                assertTrue("incoming clock already advanced before promotion", lastIncomingPosition > 50)
                assertTrue("no backward seek at promotion", promotedPosition + 25 >= lastIncomingPosition)
                delay(750)
                withContext(Dispatchers.Main) {
                    assertSame(incoming, engine.activeDeck)
                    assertTrue(engine.player.isPlaying)
                    assertTrue("incoming clock continues at handover: $promotedPosition -> ${engine.player.currentPosition}",
                        engine.player.currentPosition >= promotedPosition + 500)
                    val mime = engine.player.currentTracks.groups.first {
                        it.getTrackFormat(0).sampleMimeType?.startsWith("audio/") == true
                    }.getTrackFormat(0).sampleMimeType
                    assertEquals(if (reduced) "audio/opus" else if (b.suffix == "mp3") "audio/mpeg" else "audio/flac", mime)
                }
                assertEquals("no source reopened at handover (${a.suffix}->${b.suffix}, reduced=$reduced)", sourceOpens, opens[1].get())
                assertEquals(1, captures[1].outputCreations)
                assertTrue(captures[1].pcm().any { it.toInt() != 0 })
            } finally { withContext(Dispatchers.Main) { engine.release(); scope.cancel() } }
        }
    }
}
