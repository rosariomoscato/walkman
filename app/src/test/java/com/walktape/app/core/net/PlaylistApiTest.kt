package com.walktape.app.core.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class PlaylistApiTest {
    @Test fun `playlist reads preserve duplicate tracks and ownership`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!, Credentials("fixture", "fixture"))
            server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1","playlists":{"playlist":[{"id":"p","name":"Mix","owner":"other","public":true,"songCount":2,"duration":200,"changed":"2026-01-01T00:00:00Z"}]}}}""").build())
            val list = (client.playlists() as ApiResult.Success).value
            assertEquals("other", list.single().owner)
            assertTrue(list.single().isPublic)
            assertNull(server.takeRequest().url.queryParameter("username"))
            server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1","playlist":{"id":"p","name":"Mix","songCount":2,"duration":200,"entry":[{"id":"same","title":"First"},{"id":"same","title":"Repeat"}]}}}""").build())
            val detail = (client.playlist("p") as ApiResult.Success).value
            assertEquals(listOf("First", "Repeat"), detail.entries.map { it.title })
            assertEquals(listOf("same", "same"), detail.entries.map { it.id })
            assertEquals("p", server.takeRequest().url.queryParameter("id"))
        }
    }

    @Test fun `malformed responses are not an empty playlist collection`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!, Credentials("fixture", "fixture"))
            server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build())
            assertEquals(ApiResult.Failure(ApiError.InvalidResponse), client.playlists())
            server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1","playlist":{"id":"wrong","name":"Mix","songCount":0}}}""").build())
            assertEquals(ApiResult.Failure(ApiError.InvalidResponse), client.playlist("p"))
            server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1","playlist":{"id":"p","name":"Mix","songCount":1}}}""").build())
            assertEquals(ApiResult.Failure(ApiError.InvalidResponse), client.playlist("p"))
        }
    }

    @Test fun `unauthorized playlist returns protocol failure`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!, Credentials("fixture", "fixture"))
            server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":50,"message":"private"}}}""").build())
            assertEquals(ApiResult.Failure(ApiError.Protocol(50, ProtocolErrorKind.FORBIDDEN)), client.playlist("p"))
        }
    }
}
