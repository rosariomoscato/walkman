package com.walktape.app.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.Graph
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * W09-T01 against a fixture server: reduced selections hit the transcoding endpoint with
 * maxBitRate, cache entries are per-representation (an original never reuses reduced
 * bytes), and the honest quality monitor records what was requested.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class Wave09QualityStreamTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun unsupportedTranscodeFallsBackOnlyAtFreshOpenAndRejectsCodecChange(): Unit = runBlocking {
        Graph.networkGate.awaitReady()
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(MockResponse.Builder().code(501).build())
            server.enqueue(MockResponse.Builder().addHeader("Content-Type", "audio/mpeg").body("original").build())
            val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!,
                com.walktape.app.core.net.Credentials("fixture", "fixture"))
            val source = SubsonicStreamDataSourceFactory(OpenSubsonicClient.streamingHttpClient(), "WalkTape-test",
                selectionFor = { streamSelection(true, 96, true) },
                clientProvider = { StreamProfile(1, client) }).createDataSource()
            val uri = android.net.Uri.parse(SubsonicStream.uri(1, "fallback"))
            try {
                assertEquals(8L, source.open(DataSpec(uri)))
                assertEquals(uri, source.uri) // authentication is never the exposed/cache URI
                source.close()
                assertEquals("opus", server.takeRequest().url.queryParameter("format"))
                assertEquals("raw", server.takeRequest().url.queryParameter("format"))
                // A later byte retry must keep the original representation; a server
                // changing codec at this offset must fail rather than concatenate.
                server.enqueue(MockResponse.Builder().addHeader("Content-Type", "audio/ogg").body("different").build())
                var rejected = false
                try {
                    source.open(DataSpec.Builder().setUri(uri).setPosition(2).build())
                } catch (_: java.io.IOException) { rejected = true }
                assertTrue(rejected)
                assertEquals("raw", server.takeRequest().url.queryParameter("format"))
            } finally { source.close() }
        }
    }

    @Test fun reducedStreamsUseSeparateRequestsAndCacheEntries(): Unit = runBlocking {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        var serveOgg = true
        val wav = TestAudioEngine.wav(TestAudioEngine.sine(4_000, 12000, 300))
        val ogg = wav.copyOf(wav.size / 2) // any non-JSON body stands in for a transcode
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val endpoint = request.url.encodedPath.substringAfterLast('/')
                if (endpoint == "stream.view") {
                    requests.computeIfAbsent(request.url.queryParameter("maxBitRate") ?: "raw") {
                        AtomicInteger() }.incrementAndGet()
                    val body = if (request.url.queryParameter("maxBitRate") != null && serveOgg) ogg else wav
                    val type = if (request.url.queryParameter("maxBitRate") != null && serveOgg)
                        "audio/ogg" else "audio/wav"
                    return MockResponse.Builder().addHeader("Content-Type", type)
                        .body(okio.Buffer().write(body)).build()
                }
                return MockResponse.Builder()
                    .body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build()
            }
        }
        server.start()
        var profileId: Long? = null
        try {
            val added = Graph.profiles.addVerified("Quality fixture ${System.nanoTime()}",
                server.url("/").toString(), "fixture", "fixture-only", true)
            assertTrue(added is ApiResult.Success)
            val profile = (added as ApiResult.Success).value
            profileId = profile
            Graph.profiles.activate(profile)
            val connection = requireNotNull(Graph.profiles.clientFor(profile))
            val reduced = streamSelection(true, 96, true)
            val original = StreamSelection.ORIGINAL
            val factory = LocalFirstDataSourceFactory(
                OpenSubsonicClient.streamingHttpClient(), "WalkTape",
                { StreamProfile(profile, connection) },
                Graph.audioCache,
                localFile = { _, _ -> null },
                offlineOnly = { false },
                selectionFor = { if (it == profile) reduced else original },
                qualityMonitor = Graph.playbackQuality,
            )
            val uri = android.net.Uri.parse(SubsonicStream.uri(profile, "song-q"))

            fun readAll(selection: StreamSelection) {
                val source = factory.createDataSource() // captures the selection at open time
                val spec = DataSpec(uri)
                source.open(spec)
                val buffer = ByteArray(512)
                while (source.read(buffer, 0, buffer.size) > 0) { /* drain */ }
                source.close()
            }
            readAll(reduced)
            assertEquals(1, requests["96"]?.get())
            assertEquals(null, requests["raw"])
            // Transcodes are intentionally not fragment-cached without a strong validator.
            readAll(reduced)
            assertEquals(2, requests["96"]?.get())
            // the original bytes are a different representation: new upstream request
            val factoryOriginal = LocalFirstDataSourceFactory(
                OpenSubsonicClient.streamingHttpClient(), "WalkTape",
                { StreamProfile(profile, connection) },
                Graph.audioCache,
                localFile = { _, _ -> null },
                offlineOnly = { false },
                selectionFor = { original },
                qualityMonitor = Graph.playbackQuality,
            )
            val source = factoryOriginal.createDataSource()
            source.open(DataSpec(uri))
            val buffer = ByteArray(512)
            while (source.read(buffer, 0, buffer.size) > 0) { }
            source.close()
            assertEquals(1, requests["raw"]?.get())
            assertEquals(2, requests["96"]?.get())

            // honest monitor: last request for this song was the original
            val monitor = Graph.playbackQuality
            monitor.observed("song-q", "audio/wav", 128000)
            assertEquals(false, monitor.playbackQuality.value?.requestedReduced)
            assertEquals(false, monitor.playbackQuality.value?.transcodingUnavailable)
            monitor.requested("song-q", reduced)
            monitor.observed("song-q", "audio/wav", 128000)
            assertEquals(true, monitor.playbackQuality.value?.transcodingUnavailable)
        } finally {
            Graph.playbackQuality.reset()
            profileId?.let { Graph.profiles.remove(it) }
            server.close()
        }
    }
}
