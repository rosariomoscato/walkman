package com.walktape.app.core.net

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenSubsonicClientTest {
    private fun startedServer() = MockWebServer().apply { start() }
    private val ok = """{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","openSubsonic":true}}"""

    private fun client(server: MockWebServer, http: OkHttpClient = OpenSubsonicClient.defaultHttpClient()) =
        OpenSubsonicClient(ServerAddress.parse(server.url("/music/").toString(), true)!!,
            Credentials("user +&è", "sesame"), http) { "c19b2d" }

    @Test fun `token matches official vector and parameters preserve base path`() = runBlocking {
        startedServer().use { server ->
            server.enqueue(MockResponse.Builder().body(ok).build())
            assertTrue(client(server).ping() is ApiResult.Success)
            val url = server.takeRequest().url!!
            assertEquals("/music/rest/ping.view", url.encodedPath)
            assertEquals("user +&è", url.queryParameter("u"))
            assertEquals("26719a1196d2a940705a59634eb18eab", url.queryParameter("t"))
            assertEquals("c19b2d", url.queryParameter("s"))
            assertEquals("WalkTape", url.queryParameter("c"))
            assertNull(url.queryParameter("p"))
        }
    }

    @Test fun `salt changes for every call`() = runBlocking {
        startedServer().use { server ->
            repeat(2) { server.enqueue(MockResponse.Builder().body(ok).build()) }
            val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!,
                Credentials("user", "secret"))
            repeat(2) { assertTrue(client.ping() is ApiResult.Success) }
            val first = server.takeRequest().url!!
            val second = server.takeRequest().url!!
            assertNotEquals(first.queryParameter("s"), second.queryParameter("s"))
            assertNotEquals(first.queryParameter("t"), second.queryParameter("t"))
        }
    }

    @Test fun `protocol failure on HTTP 200 never exposes server message`() = runBlocking {
        startedServer().use { server ->
            server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"secret-token password"}}}""").build())
            val result = client(server).ping()
            assertEquals(ApiResult.Failure(ApiError.Protocol(40, ProtocolErrorKind.AUTHENTICATION)), result)
            assertFalse(result.toString().contains("secret-token"))
        }
    }

    @Test fun `all protocol error categories including unknown codes`() {
        val expected = mapOf(0 to ProtocolErrorKind.GENERIC, 10 to ProtocolErrorKind.MISSING_PARAMETER,
            20 to ProtocolErrorKind.CLIENT_VERSION, 30 to ProtocolErrorKind.SERVER_VERSION,
            40 to ProtocolErrorKind.AUTHENTICATION, 41 to ProtocolErrorKind.AUTH_UNSUPPORTED,
            42 to ProtocolErrorKind.AUTH_UNSUPPORTED, 43 to ProtocolErrorKind.AUTH_CONFLICT,
            44 to ProtocolErrorKind.AUTHENTICATION, 50 to ProtocolErrorKind.FORBIDDEN,
            60 to ProtocolErrorKind.LICENSE, 70 to ProtocolErrorKind.NOT_FOUND, 999 to ProtocolErrorKind.GENERIC)
        expected.forEach { (code, kind) ->
            val result = OpenSubsonicClient.decode("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":$code}}}""")
            assertEquals(ApiResult.Failure(ApiError.Protocol(code, kind)), result)
        }
    }

    @Test fun `malformed envelopes and HTML rejected`() {
        listOf("<html>login</html>", "{}", "null", "[]",
            """{"subsonic-response":{"status":"ok"}}""",
            """{"subsonic-response":{"status":"unknown","version":"1.16.1"}}""",
            """{"subsonic-response":{"status":"failed","version":"1.16.1"}}""",
            "$ok trailing", """{"subsonic-response":{"status":"ok","version":"1.16.1","error":{}}}""")
            .forEach { assertEquals(ApiResult.Failure(ApiError.InvalidResponse), OpenSubsonicClient.decode(it)) }
    }

    @Test fun `redirect is returned without following or leaking authentication`() = runBlocking {
        startedServer().use { server ->
            startedServer().use { other ->
                server.enqueue(MockResponse.Builder().code(302).addHeader("Location", other.url("/")).build())
                assertEquals(ApiResult.Failure(ApiError.Http(302)), client(server).ping())
                assertEquals(0, other.requestCount)
            }
        }
    }

    @Test fun `timeout maps to controlled error`() = runBlocking {
        startedServer().use { server ->
            server.enqueue(MockResponse.Builder().body(ok).headersDelay(300, TimeUnit.MILLISECONDS).build())
            val http = OpenSubsonicClient.defaultHttpClient().newBuilder().callTimeout(50, TimeUnit.MILLISECONDS).build()
            assertEquals(ApiResult.Failure(ApiError.Timeout), client(server, http).ping())
        }
    }

    @Test fun `cancellation does not become a network error`() = runBlocking {
        startedServer().use { server ->
            server.enqueue(MockResponse.Builder().body(ok).headersDelay(300, TimeUnit.MILLISECONDS).build())
            val task = async { client(server).ping() }
            delay(30)
            task.cancelAndJoin()
            assertTrue(task.isCancelled)
        }
    }

    @Test fun `invalid inputs and reserved auth overrides rejected`() = runBlocking {
        assertNull(ServerAddress.parse("http://localhost:4533"))
        assertNotNull(ServerAddress.parse("http://localhost:4533", true))
        assertNotNull(ServerAddress.parse("https://example.org/music"))
        listOf("https://u:p@example.org", "https://example.org/?t=token", "https://example.org/#part", "file:///data")
            .forEach { assertNull(ServerAddress.parse(it, true)) }
        startedServer().use { server ->
            assertEquals(ApiResult.Failure(ApiError.InvalidRequest), client(server).request("../ping"))
            assertEquals(ApiResult.Failure(ApiError.InvalidRequest), client(server).request("ping", listOf("p" to "value")))
            assertEquals(0, server.requestCount)
        }
        assertFalse(Credentials("user", "secret").toString().contains("secret"))
    }

    @Test fun `extensions decoded without treating unknown extensions as errors`() = runBlocking {
        startedServer().use { server ->
            server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonicExtensions":[{"name":"formPost","versions":[1]},{"name":"future","versions":[2]}]}}""").build())
            val result = client(server).extensions() as ApiResult.Success
            assertEquals(listOf(ServerExtension("formPost", listOf(1)), ServerExtension("future", listOf(2))), result.value)
        }
    }
}
