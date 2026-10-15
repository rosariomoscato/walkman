package com.walktape.app.core.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class Wave07ApiTest {
    private fun client(server: MockWebServer) = OpenSubsonicClient(
        ServerAddress.parse(server.url("/").toString(), true)!!, Credentials("fixture", "fixture"))
    private fun ok(payload: String = "") = MockResponse.Builder().body(
        """{"subsonic-response":{"status":"ok","version":"1.16.1"$payload}}""").build()

    @Test fun `playlist POST keeps order duplicates indexes unicode and reserved characters`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(ok(",\"playlist\":{\"id\":\"p\",\"name\":\"Mix\",\"songCount\":0}"))
            assertTrue(client(server).createPlaylist("Mix & città", emptyList()) is ApiResult.Success)
            val created = server.takeRequest()
            assertEquals("POST", created.method)
            assertEquals("Mix & città", ("https://fixture/?" + created.body!!.utf8()).toHttpUrl().queryParameter("name"))
            server.enqueue(ok())
            assertTrue(client(server).updatePlaylist("p", songIdsToAdd = listOf("b", "a", "a"),
                songIndexesToRemove = listOf(0, 1, 2)) is ApiResult.Success)
            val request = server.takeRequest()
            val form = ("https://fixture/?" + request.body!!.utf8()).toHttpUrl()
            assertEquals(listOf("b", "a", "a"), form.queryParameterValues("songIdToAdd"))
            assertEquals(listOf("0", "1", "2"), form.queryParameterValues("songIndexToRemove"))
            assertNull(request.url.queryParameter("songIdToAdd"))
        }
    }

    @Test fun `favorites GET parameters survive transport and use distinct entity identifiers`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(ok())
            assertTrue(client(server).star(listOf("song&1"), listOf("album"), listOf("artist")) is ApiResult.Success)
            val request = server.takeRequest()
            assertEquals("song&1", request.url.queryParameter("id"))
            assertEquals("album", request.url.queryParameter("albumId"))
            assertEquals("artist", request.url.queryParameter("artistId"))
        }
    }

    @Test fun `lyrics offset is applied and missing container is a malformed response`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(ok(",\"lyricsList\":{\"structuredLyrics\":[{\"synced\":true,\"offset\":-100,\"line\":[{\"start\":1000,\"value\":\"line\"}]}]}"))
            val result = (client(server).lyrics("song") as ApiResult.Success).value.single()
            assertTrue(result.synced)
            assertEquals(1100L, result.lines.single().startMs)
            assertEquals("song", server.takeRequest().url.queryParameter("id"))
            server.enqueue(ok())
            assertEquals(ApiResult.Failure(ApiError.InvalidResponse), client(server).lyrics("song"))
        }
    }

    @Test fun `scrobble POST retains original timestamps and explicit submission`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(ok())
            assertTrue(client(server).scrobble(listOf("a" to 1234L, "a" to 4567L)) is ApiResult.Success)
            val form = ("https://fixture/?" + server.takeRequest().body!!.utf8()).toHttpUrl()
            assertEquals(listOf("a", "a"), form.queryParameterValues("id"))
            assertEquals(listOf("1234", "4567"), form.queryParameterValues("time"))
            assertEquals("true", form.queryParameter("submission"))
        }
    }
}
