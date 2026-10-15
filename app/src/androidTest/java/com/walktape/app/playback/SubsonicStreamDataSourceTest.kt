package com.walktape.app.playback

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.net.Credentials
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubsonicStreamDataSourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun waitForLoadedAppNetworkPolicy(): Unit = runBlocking {
        kotlinx.coroutines.withTimeout(10_000) { com.walktape.app.Graph.networkGate.awaitReady() }
    }

    private fun md5(value: String) = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun factory(server: MockWebServer, client: OpenSubsonicClient?) =
        SubsonicStreamDataSourceFactory(
            OpenSubsonicClient.streamingHttpClient(), "WalkTape-test"
        ) { client?.let { StreamProfile(1, it) } }

    private fun clientFor(server: MockWebServer) = OpenSubsonicClient(
        ServerAddress.parse(server.url("/").toString(), true)!!,
        Credentials("user", "password"),
    )

    @Test fun resolvesQueueUriIntoAuthenticatedRawStreamRequest() {
        MockWebServer().apply { start() }.use { server ->
            // Mirrors the real Navidrome pass-through behavior verified in W05-T01 (206 + Content-Range).
            server.enqueue(
                MockResponse.Builder().code(206)
                    .addHeader("Content-Type", "audio/mpeg")
                    .addHeader("Content-Range", "bytes 1000-1007/5000")
                    .addHeader("Content-Length", "8")
                    .body("01234567").build()
            )
            val source = factory(server, clientFor(server)).createDataSource()
            val dataSpec = DataSpec.Builder()
                .setUri(SubsonicStream.uri(1, "mf-42"))
                .setPosition(1000)
                .setLength(8)
                .build()
            assertEquals(8L, source.open(dataSpec))
            val buffer = ByteArray(8)
            assertEquals(8, source.read(buffer, 0, buffer.size))
            assertEquals("01234567", String(buffer))
            source.close()

            val request = server.takeRequest()
            val url: HttpUrl = request.url
            assertEquals("/rest/stream.view", url.encodedPath)
            assertEquals("user", url.queryParameter("u"))
            assertEquals(md5("password" + url.queryParameter("s")), url.queryParameter("t"))
            assertEquals("raw", url.queryParameter("format"))
            assertEquals("mf-42", url.queryParameter("id"))
            assertEquals("bytes=1000-1007", request.headers["Range"])
        }
    }

    @Test fun eachRequestCarriesAFreshSalt() {
        MockWebServer().apply { start() }.use { server ->
            repeat(2) {
                server.enqueue(
                    MockResponse.Builder().addHeader("Content-Type", "audio/mpeg")
                        .addHeader("Content-Length", "1").body("x").build()
                )
            }
            val client = clientFor(server)
            val source = factory(server, client).createDataSource()
            val spec = DataSpec.Builder().setUri(SubsonicStream.uri(1, "a")).build()
            source.open(spec); source.close()
            source.open(spec); source.close()
            val first = server.takeRequest().url.queryParameter("s")!!
            val second = server.takeRequest().url.queryParameter("s")!!
            assertNotEquals(first, second)
        }
    }

    @Test fun jsonErrorEnvelopeIsRejected() {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(
                MockResponse.Builder().addHeader("Content-Type", "application/json")
                    .body("""{"subsonic-response":{"status":"failed","version":"1.16.1"}}""").build()
            )
            val source = factory(server, clientFor(server)).createDataSource()
            val spec = DataSpec.Builder().setUri(SubsonicStream.uri(1, "missing")).build()
            val rejected = try {
                source.open(spec); false
            } catch (_: HttpDataSource.InvalidContentTypeException) {
                true
            }
            assertTrue(rejected)
            source.close()
        }
    }

    @Test fun missingActiveProfileConnectionFailsCleanly() {
        MockWebServer().apply { start() }.use { server ->
            val source = factory(server, null).createDataSource()
            val spec = DataSpec.Builder().setUri(SubsonicStream.uri(1, "a")).build()
            val rejected = try {
                source.open(spec); false
            } catch (e: java.io.IOException) {
                assertEquals("No active profile connection", e.message); true
            }
            assertTrue(rejected)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun anotherProfilesQueueEntryNeverReachesTheServer() {
        MockWebServer().apply { start() }.use { server ->
            val source = factory(server, clientFor(server)).createDataSource()
            val spec = DataSpec.Builder().setUri(SubsonicStream.uri(2, "a")).build()
            var rejected = false
            try { source.open(spec) } catch (_: java.io.IOException) { rejected = true }
            finally { source.close() }
            assertTrue(rejected)
            assertEquals(0, server.requestCount)
        }
    }
}
