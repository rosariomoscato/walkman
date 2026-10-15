package com.walktape.app.core.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class OfflineNetworkGateTest {
    @Test fun offlineGateBlocksEveryMetadataMutationAndArtworkRequest() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!,
                Credentials("fixture", "fixture"), networkAllowed = { false })
            assertTrue(client.ping() is ApiResult.Failure)
            assertTrue(client.artists() is ApiResult.Failure)
            assertTrue(client.playlists() is ApiResult.Failure)
            assertTrue(client.playlist("p") is ApiResult.Failure)
            assertTrue(client.albumList("newest", 1, 0) is ApiResult.Failure)
            assertTrue(client.album("a") is ApiResult.Failure)
            assertTrue(client.lyrics("s") is ApiResult.Failure)
            assertTrue(client.coverArt("c", 512) is ApiResult.Failure)
            assertTrue(client.similarSongs("s", 10) is ApiResult.Failure)
            assertTrue(client.star(songIds = listOf("s")) is ApiResult.Failure)
            assertTrue(client.unstar(songIds = listOf("s")) is ApiResult.Failure)
            assertTrue(client.createPlaylist("test", listOf("s")) is ApiResult.Failure)
            assertTrue(client.updatePlaylist("p", name = "test") is ApiResult.Failure)
            assertTrue(client.deletePlaylist("p") is ApiResult.Failure)
            assertTrue(client.scrobble(listOf("s" to 1L)) is ApiResult.Failure)
            assertEquals(0, server.requestCount)
        }
    }
}
