package com.walktape.app.sync

import com.walktape.app.core.net.Credentials
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.core.db.PendingOpState
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import com.walktape.app.core.db.SongEntity
import com.walktape.app.core.library.FakeLibraryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * JVM tests of the offline mutation queue (W08-T06) against a MockWebServer: idempotent
 * replay after lost ACKs, preflight of non-idempotent creates, explicit conflicts and
 * honest failures. Request counting distinguishes "reconciled" from "blindly resent".
 */
class OfflineOpsTest {

    private fun client(server: MockWebServer) = OpenSubsonicClient(
        ServerAddress.parse(server.url("/").toString(), true)!!,
        Credentials("user", "pass"))

    private fun okPlaylists(vararg names: Pair<String, String>) =
        """{"subsonic-response":{"status":"ok","version":"1.16.1","playlists":{"playlist":[""" +
            names.joinToString(",") { (id, name) ->
                """{"id":"$id","name":"$name","owner":"user","songCount":0,"duration":0}"""
            } + """]}}}"""

    private fun okPlaylist(id: String, name: String, changed: String, vararg songs: String) =
        """{"subsonic-response":{"status":"ok","version":"1.16.1","playlist":{"id":"$id","name":"$name","owner":"user","songCount":${songs.size},"duration":0,"changed":"$changed","entry":[""" +
            songs.joinToString(",") { song -> """{"id":"$song","title":"t"}""" } + """]}}}"""

    private suspend fun pollUntil(timeoutMs: Long = 5_000, condition: suspend () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!condition()) delay(25)
        }
    }

    @Test
    fun `ambiguous create without pre-send baseline is preserved as conflict`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val downloadDao = FakeDownloadDao()
            val browseDao = FakeBrowseDao()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            // Same name alone cannot prove this is our previous create.
            server.enqueue(MockResponse.Builder().body(okPlaylists("srv1" to "Mix")).build())
            val engine = OfflineOps(downloadDao, browseDao, FakeLibraryDao(),
                clientProvider = { client(server) }, usernameProvider = { "user" },
                offlineOnly = { false }, scope = scope, autoSync = false)
            engine.queueCreate(1L, "local-abc", "Mix", listOf("a", "b"), attempted = true)
            assertFalse(engine.drain(1L))
            assertEquals(PendingOpState.CONFLICT.name, downloadDao.opsOnce(1L).single().state)
            scope.cancel()
            var creates = 0
            var requests = 0
            while (true) {
                val request = server.takeRequest(100, TimeUnit.MILLISECONDS) ?: break
                requests++
                if (request.url.encodedPath.contains("createPlaylist")) creates++
            }
            assertEquals(0, creates)
            assertEquals(1, requests) // preflight only, neither blind resend nor arbitrary adoption
            assertNull(browseDao.playlistOnce(1L, "local-abc"))
            assertNull(browseDao.playlistOnce(1L, "srv1"))
        }
    }

    @Test
    fun `create with lost ack creates once and adopts on retry`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val downloadDao = FakeDownloadDao()
            val browseDao = FakeBrowseDao()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            // First drain: empty list -> send create, but the ACK looks lost (HTTP 500).
            server.enqueue(MockResponse.Builder().body(okPlaylists()).build())
            server.enqueue(MockResponse.Builder().code(500).build())
            val engine = OfflineOps(downloadDao, browseDao, FakeLibraryDao(),
                clientProvider = { client(server) }, usernameProvider = { "user" },
                offlineOnly = { false }, scope = scope, autoSync = false)
            engine.queueCreate(1L, "local-x", "Mix", listOf("a"))
            assertFalse(engine.drain(1L))
            pollUntil { downloadDao.opsOnce(1L).any { it.state == PendingOpState.PENDING.name && it.attempts > 0 } }
            scope.cancel()

            // Second drain: the server now owns the playlist (created by the lost attempt):
            // the preflight adopts it without a second createPlaylist.
            server.enqueue(MockResponse.Builder().body(okPlaylists("srv9" to "Mix")).build())
            server.enqueue(MockResponse.Builder().body(okPlaylist("srv9", "Mix", "c", "a")).build())
            assertTrue(engine.drain(1L))
            assertTrue(downloadDao.opsOnce(1L).isEmpty())
            var creates = 0
            while (true) {
                val request = server.takeRequest(100, TimeUnit.MILLISECONDS) ?: break
                if (request.url.encodedPath.contains("createPlaylist")) creates++
            }
            assertEquals(1, creates)
        }
    }

    @Test
    fun `set content equal on server is done without resending`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val downloadDao = FakeDownloadDao()
            server.enqueue(MockResponse.Builder().body(okPlaylist("p1", "Mix", "c1", "a", "b")).build())
            val engine = OfflineOps(downloadDao, FakeBrowseDao(), FakeLibraryDao(),
                clientProvider = { client(server) }, usernameProvider = { "user" },
                offlineOnly = { false }, scope = CoroutineScope(SupervisorJob()))
            engine.queueSetContent(1L, "p1", listOf("a", "b"), "c0", listOf("a"))
            assertTrue(engine.drain(1L))
            assertTrue(downloadDao.opsOnce(1L).isEmpty())
            val path = server.takeRequest(100, TimeUnit.MILLISECONDS)?.url?.encodedPath
            assertTrue(path!!.contains("getPlaylist"))
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS)) // no updatePlaylist
        }
    }

    @Test
    fun `concurrent server change becomes an explicit conflict`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val downloadDao = FakeDownloadDao()
            val browseDao = FakeBrowseDao()
            browseDao.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null,
                1, 100, null, "c0", 0L))
            browseDao.insertEntries(listOf(PlaylistEntryEntity(1L, "p1", 0, "a", "t", null, null, 100, null, null)))
            // server changed both timestamp and content since our baseline
            server.enqueue(MockResponse.Builder().body(okPlaylist("p1", "Mix", "c9", "z")).build())
            val engine = OfflineOps(downloadDao, browseDao, FakeLibraryDao(),
                clientProvider = { client(server) }, usernameProvider = { "user" },
                offlineOnly = { false }, scope = CoroutineScope(SupervisorJob()))
            engine.queueSetContent(1L, "p1", listOf("a", "b"), "c0", listOf("a"))
            assertFalse(engine.drain(1L))
            val op = downloadDao.opsOnce(1L).single()
            assertEquals(PendingOpState.CONFLICT.name, op.state)
            assertEquals(true, browseDao.playlistOnce(1L, "p1")?.pendingConflict)
            server.takeRequest(100, TimeUnit.MILLISECONDS) // getPlaylist preflight
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS)) // server untouched
        }
    }

    @Test
    fun `delete of a missing playlist is done without resurrecting`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val downloadDao = FakeDownloadDao()
            val browseDao = FakeBrowseDao()
            browseDao.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null,
                0, 0, null, null, 0L))
            server.enqueue(MockResponse.Builder().body(
                """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":70,"message":"gone"}}}""").build())
            val engine = OfflineOps(downloadDao, browseDao, FakeLibraryDao(),
                clientProvider = { client(server) }, usernameProvider = { "user" },
                offlineOnly = { false }, scope = CoroutineScope(SupervisorJob()))
            engine.queueDelete(1L, "p1")
            assertTrue(engine.drain(1L))
            assertTrue(downloadDao.opsOnce(1L).isEmpty())
            server.takeRequest(100, TimeUnit.MILLISECONDS) // getPlaylist
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS)) // no deletePlaylist
        }
    }

    @Test
    fun `delete refused by permissions fails honestly`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val downloadDao = FakeDownloadDao()
            val browseDao = FakeBrowseDao()
            browseDao.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null,
                0, 0, null, null, 0L))
            // the playlist now belongs to someone else on the server
            server.enqueue(MockResponse.Builder().body(
                """{"subsonic-response":{"status":"ok","version":"1.16.1","playlist":{"id":"p1","name":"Mix","owner":"other","songCount":0,"duration":0}}}""").build())
            val engine = OfflineOps(downloadDao, browseDao, FakeLibraryDao(),
                clientProvider = { client(server) }, usernameProvider = { "user" },
                offlineOnly = { false }, scope = CoroutineScope(SupervisorJob()))
            engine.queueDelete(1L, "p1")
            engine.drain(1L)
            val op = downloadDao.opsOnce(1L).single()
            assertEquals(PendingOpState.FAILED.name, op.state)
            assertEquals("FORBIDDEN", op.lastError)
        }
    }

    @Test
    fun `offline-only mode drains nothing`() = runBlocking {
        val downloadDao = FakeDownloadDao()
        val engine = OfflineOps(downloadDao, FakeBrowseDao(), FakeLibraryDao(),
            clientProvider = { throw IllegalStateException("no network call expected") },
            usernameProvider = { "user" }, offlineOnly = { true },
            scope = CoroutineScope(SupervisorJob()))
        engine.queueStar(1L, "SONG", "a", true)
        val drained = engine.drain(1L)
        assertFalse(drained)
        assertEquals(PendingOpState.PENDING.name, downloadDao.opsOnce(1L).single().state)
    }

    @Test
    fun `star op sends exactly one idempotent request`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val downloadDao = FakeDownloadDao()
            server.enqueue(MockResponse.Builder().body(
                """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build())
            val engine = OfflineOps(downloadDao, FakeBrowseDao(), FakeLibraryDao(),
                clientProvider = { client(server) }, usernameProvider = { "user" },
                offlineOnly = { false }, scope = CoroutineScope(SupervisorJob()))
            engine.queueStar(1L, "SONG", "a", true)
            assertTrue(engine.drain(1L))
            assertTrue(downloadDao.opsOnce(1L).isEmpty())
            assertTrue(server.takeRequest(100, TimeUnit.MILLISECONDS)!!.url.encodedPath.contains("star"))
        }
    }

    @Test
    fun `syncing rows orphaned by process death are retried`() = runBlocking {
        val downloadDao = FakeDownloadDao()
        val engine = OfflineOps(downloadDao, FakeBrowseDao(), FakeLibraryDao(),
            clientProvider = { throw IllegalStateException("drain must stop before client use") },
            usernameProvider = { "user" }, offlineOnly = { true },
            scope = CoroutineScope(SupervisorJob()))
        engine.queueStar(1L, "SONG", "a", true)
        // simulate a dead process leaving the op in SYNCING
        val orphan = downloadDao.opsOnce(1L).single()
        downloadDao.upsertOp(orphan.copy(state = PendingOpState.SYNCING.name))
        val drained = engine.drain(1L) // still offline: recovered to PENDING, no requests
        assertFalse(drained)
        assertEquals(PendingOpState.PENDING.name, downloadDao.opsOnce(1L).single().state)
    }

    @Test
    fun `keeping server resolves the whole local playlist version not just the first edit`() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val dao = FakeDownloadDao()
            val browse = FakeBrowseDao()
            browse.insertPlaylist(PlaylistEntity(1L, "p1", "Local name", "user", false, null,
                null, 2, 0, null, "old", 0, pendingSync = true))
            browse.insertEntries(listOf(PlaylistEntryEntity(1L, "p1", 0, "a", "A", null, null, 1, null, null),
                PlaylistEntryEntity(1L, "p1", 1, "a", "A", null, null, 1, null, null)))
            val engine = OfflineOps(dao, browse, FakeLibraryDao(), { client(server) }, { "user" },
                { false }, CoroutineScope(SupervisorJob()), autoSync = false)
            engine.queueSetContent(1L, "p1", listOf("a", "a"), "old", listOf("a"))
            engine.queueRename(1L, "p1", "Old name", "Local name", "old")
            server.enqueue(MockResponse.Builder().body(okPlaylist("p1", "Server name", "new", "z")).build())
            assertFalse(engine.drain(1L))
            assertEquals(2, dao.opsOnce(1L).size)
            val conflict = dao.opsOnce(1L).first { it.state == PendingOpState.CONFLICT.name }
            server.enqueue(MockResponse.Builder().body(okPlaylist("p1", "Server name", "new", "z")).build())
            assertTrue(engine.resolveConflictKeepServer(1L, conflict.opId))
            assertTrue(dao.opsOnce(1L).isEmpty())
            assertEquals("Server name", browse.playlistOnce(1L, "p1")!!.name)
            assertEquals(listOf("z"), browse.queueEntries(1L, "p1").map { it.songId })
            assertEquals(2, server.requestCount) // no postponed rename of the server copy
        }
    }
}
