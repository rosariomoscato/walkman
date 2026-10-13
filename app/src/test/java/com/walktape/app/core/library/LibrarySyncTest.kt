package com.walktape.app.core.library

import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.core.net.Credentials
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.Dispatcher
import mockwebserver3.RecordedRequest
import mockwebserver3.QueueDispatcher
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncTest {
    private val dao = FakeLibraryDao()

    private fun client(server: MockWebServer) = OpenSubsonicClient(
        ServerAddress.parse(server.url("/").toString(), true)!!,
        Credentials("user", "secret"), OkHttpClient(),
    ) { "c19b2d" }

    private fun ok(json: String) = MockResponse.Builder().body(json).build()

    private fun enqueueLibrary(server: MockWebServer, albums: List<Pair<String, Int>>) {
        val albumJson = albums.joinToString(",") { (id, songCount) ->
            """{"id":"$id","name":"Album $id","artist":"Alpha","artistId":"a1","songCount":$songCount,"duration":${songCount * 100},"created":"2024-01-01T00:00:00Z"}"""
        }
        val details = albums.associate { (id, songCount) ->
            val songsJson = (1..songCount).joinToString(",") { track ->
                """{"id":"$id-$track","title":"Song $track","artist":"Alpha","album":"Album $id","track":$track,"discNumber":1,"duration":100,"bitRate":320,"suffix":"mp3","contentType":"audio/mpeg","size":1000,"albumId":"$id","artistId":"a1"}"""
            }
            id to """{"subsonic-response":{"status":"ok","version":"1.16.1","album":{"id":"$id","name":"Album $id","artist":"Alpha","artistId":"a1","songCount":$songCount,"duration":${songCount * 100},"song":[$songsJson]}}}"""
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/rest/getArtists.view" -> ok("""{"subsonic-response":{"status":"ok","version":"1.16.1","artists":{"index":[{"name":"A","artist":[{"id":"a1","name":"Alpha","albumCount":${albums.size}}]}]}}}""")
                "/rest/getGenres.view" -> ok("""{"subsonic-response":{"status":"ok","version":"1.16.1","genres":{"genre":[{"value":"Rock","songCount":9,"albumCount":${albums.size}}]}}}""")
                "/rest/getAlbumList2.view" -> ok("""{"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{"album":[$albumJson]}}}""")
                "/rest/getAlbum.view" -> details[request.url.queryParameter("id")]?.let(::ok) ?: MockResponse.Builder().code(404).build()
                else -> MockResponse.Builder().code(404).build()
            }
        }
    }

    @Test fun `full progressive sync stores artists albums songs and state`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            enqueueLibrary(server, listOf("al1" to 2, "al2" to 1))
            val sync = LibrarySync(dao) { client(server) }
            assertTrue(sync.sync(7))
            assertEquals(1, dao.artists.size)
            assertEquals(2, dao.albums.size)
            assertEquals(3, dao.songs.size)
            assertEquals(1, dao.genres.size)
            val state = dao.state(7)
            assertNotNull(state)
            assertEquals(3, state!!.songCount)
            assertEquals(2, state.albumCount)
            assertTrue(state.lastSyncOk)
            val progress = sync.progress.value
            assertEquals(2, progress.total)
            assertFalse(progress.running)
            assertTrue(progress.error == null)
        }
    }

    @Test fun `second sync removes albums disappeared from server`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            enqueueLibrary(server, listOf("al1" to 2, "al2" to 1))
            val sync = LibrarySync(dao) { client(server) }
            assertTrue(sync.sync(7))
            enqueueLibrary(server, listOf("al2" to 1))
            assertTrue(sync.sync(7))
            assertEquals(listOf("al2"), dao.albums.values.map { it.id })
            assertEquals(listOf("al2-1"), dao.songs.values.map { it.id })
        }
    }

    @Test fun `failure during detail fetch leaves partial data without success state`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(ok("""{"subsonic-response":{"status":"ok","version":"1.16.1","artists":{"index":[]}}}"""))
            server.enqueue(ok("""{"subsonic-response":{"status":"ok","version":"1.16.1","genres":{"genre":[]}}}"""))
            server.enqueue(ok("""{"subsonic-response":{"status":"ok","version":"1.16.1","albumList2":{"album":[{"id":"al1","name":"Album","songCount":1,"duration":100}]}}}"""))
            server.enqueue(ok("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":0,"message":"boom"}}}"""))
            val sync = LibrarySync(dao) { client(server) }
            assertFalse(sync.sync(9))
            assertTrue(sync.progress.value.error is ApiError.Protocol)
            assertEquals(0, dao.songs.size)
            assertEquals(null, dao.state(9))
        }
    }

    @Test fun `sync without credentials reports storage failure`() = runBlocking {
        val sync = LibrarySync(dao) { null }
        assertFalse(sync.sync(1))
        assertEquals(ApiError.CredentialsUnavailable, sync.progress.value.error)
    }

    @Test fun `cancel stops network indexing and leaves no success state`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(MockResponse.Builder()
                .body("""{"subsonic-response":{"status":"ok","version":"1.16.1","artists":{}}}""")
                .headersDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS).build())
            val sync = LibrarySync(dao) { client(server) }
            val task = launch { sync.sync(7) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                check(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS) != null)
            }
            sync.cancel()
            task.join()
            assertTrue(task.isCancelled)
            assertFalse(sync.progress.value.running)
            assertEquals(null, dao.state(7))
        }
    }

    @Test fun `refresh removes obsolete songs within a surviving album`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val sync = LibrarySync(dao) { client(server) }
            enqueueLibrary(server, listOf("al1" to 3))
            assertTrue(sync.sync(7))
            enqueueLibrary(server, listOf("al1" to 1))
            assertTrue(sync.sync(7))
            assertEquals(1, dao.songCount(7))
            assertEquals(listOf("al1-1"), dao.songs.values.map { it.id })
        }
    }

    @Test fun `malformed album page does not erase an existing index`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val sync = LibrarySync(dao) { client(server) }
            enqueueLibrary(server, listOf("al1" to 2))
            assertTrue(sync.sync(7))
            server.dispatcher = QueueDispatcher()
            server.enqueue(ok("""{"subsonic-response":{"status":"ok","version":"1.16.1","artists":{}}}"""))
            server.enqueue(ok("""{"subsonic-response":{"status":"ok","version":"1.16.1","genres":{}}}"""))
            server.enqueue(ok("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""))
            assertFalse(sync.sync(7))
            assertEquals(2, dao.songCount(7))
            assertEquals(1, dao.albumCount(7))
        }
    }
}
