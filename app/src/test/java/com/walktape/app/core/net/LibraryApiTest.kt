package com.walktape.app.core.net

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LibraryApiTest {
    private fun client(server: MockWebServer) = OpenSubsonicClient(
        ServerAddress.parse(server.url("/").toString(), true)!!,
        Credentials("user", "secret"),
        OkHttpClient(),
    ) { "c19b2d" }

    private fun body(json: String) = MockResponse.Builder().body(json).build()

    @Test fun `artists parsed from nested index structure`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(body("""{"subsonic-response":{"status":"ok","version":"1.16.1","artists":{"ignoredArticles":"The ","index":[{"name":"A","artist":[{"id":"a1","name":"Alpha","coverArt":"ar-a1","albumCount":2},{"id":"a2","name":"Beta","albumCount":0}]},{"name":"C","artist":[{"id":"a3","name":"Gamma"}]}]}}}"""))
            val artists = (client(server).artists() as ApiResult.Success).value
            assertEquals(3, artists.size)
            assertEquals(ArtistDto("a1", "Alpha", "ar-a1", 2), artists[0])
            assertEquals(0, artists[1].albumCount)
            assertNull(artists[2].coverArt)
        }
    }

    @Test fun `album list parameters and parsing`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(body("""{"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{"album":[{"id":"al1","name":"First","artist":"Alpha","artistId":"a1","songCount":10,"duration":2000,"year":1999,"genre":"Rock","created":"2024-01-01T00:00:00Z","isCompilation":false},{"id":"al2","name":"Mix","artist":"Various Artists","songCount":3,"duration":600,"isCompilation":true}]}}}"""))
            val albums = (client(server).albumList("alphabeticalByName", 500, 0) as ApiResult.Success).value
            assertEquals(2, albums.size)
            assertEquals(1999, albums[0].year)
            assertTrue(albums[1].isCompilation)
            val url = server.takeRequest().url
            assertEquals("alphabeticalByName", url.queryParameter("type"))
            assertEquals("500", url.queryParameter("size"))
            assertEquals("0", url.queryParameter("offset"))
        }
    }

    @Test fun `album detail with songs keeps disc track and album artist`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(body("""{"subsonic-response":{"status":"ok","version":"1.16.1","album":{"id":"al1","name":"First","songCount":2,"duration":300,"song":[{"id":"s1","title":"One","artist":"Someone","track":1,"discNumber":2,"duration":150,"bitRate":320,"suffix":"mp3","contentType":"audio/mpeg","size":100,"albumId":"al1","albumArtists":[{"id":"a1","name":"Alpha"}]},{"id":"s2","title":"Two","artist":"Featuring","displayArtist":"Alpha feat. X","track":2,"duration":150,"albumId":"al1"}]}}}"""))
            val detail = (client(server).album("al1") as ApiResult.Success).value
            assertEquals(2, detail.songs.size)
            assertEquals(2, detail.songs[0].discNumber)
            assertEquals("Alpha", detail.songs[0].albumArtist)
            assertEquals("Someone", detail.songs[0].artist)
            assertEquals("Alpha feat. X", detail.songs[1].displayArtist)
            assertEquals(server.takeRequest().url.queryParameter("id"), "al1")
        }
    }

    @Test fun `genres without value are skipped`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(body("""{"subsonic-response":{"status":"ok","version":"1.16.1","genres":{"genre":[{"value":"Rock","songCount":10,"albumCount":3},{"songCount":1}]}}}"""))
            val genres = (client(server).genres() as ApiResult.Success).value
            assertEquals(listOf(GenreDto("Rock", 10, 3)), genres)
        }
    }

    @Test fun `cover art returns bytes and rejects json envelope on 200`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)
            val pngBody = okio.Buffer().write(png)
            server.enqueue(MockResponse.Builder().body(pngBody)
                .addHeader("Content-Type", "image/png").build())
            val result = client(server).coverArt("al-1", 300)
            assertTrue(result is ApiResult.Success)
            assertArrayEquals(png, (result as ApiResult.Success).value)
            val url = server.takeRequest().url
            assertEquals("al-1", url.queryParameter("id"))
            assertEquals("300", url.queryParameter("size"))
            server.enqueue(MockResponse.Builder()
                .body("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":70,"message":"x"}}}""")
                .addHeader("Content-Type", "application/json").build())
            assertTrue(client(server).coverArt("missing", null) is ApiResult.Failure)
        }
    }

    @Test fun `malformed album envelope maps to invalid response`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""))
            assertTrue(client(server).album("al1") is ApiResult.Failure)
        }
    }
}
