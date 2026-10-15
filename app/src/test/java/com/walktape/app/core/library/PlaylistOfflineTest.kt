package com.walktape.app.core.library

import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import com.walktape.app.core.db.SongEntity
import com.walktape.app.sync.FakeBrowseDao
import com.walktape.app.sync.FakeDownloadDao
import com.walktape.app.sync.OfflineOps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Offline playlist edits (W08-T06): while offline the local cache is the working copy,
 * every edit lands in the durable queue and nothing pretends to be confirmed by the
 * server (pendingSync visible; permission failures never silently "succeed").
 */
class PlaylistOfflineTest {

    private fun song(id: String) = SongEntity(
        profileId = 1L, id = id, parent = null, albumId = "al1", artistId = null,
        title = "t-$id", album = "Album", artist = "Artist", albumArtist = null,
        displayArtist = "Artist", track = 1, discNumber = 1, year = null, genre = null,
        coverArt = "cov", duration = 100, bitRate = 128, suffix = "mp3",
        contentType = "audio/mpeg", size = 1000, path = null, created = null,
        starred = null, type = null)

    private fun repository(browse: FakeBrowseDao, library: FakeLibraryDao, dao: FakeDownloadDao,
                           offline: Boolean = true): PlaylistRepository {
        val ops = OfflineOps(dao, browse, library,
            clientProvider = { throw IllegalStateException("offline test") },
            usernameProvider = { "user" },
            offlineOnly = { offline },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        return PlaylistRepository(browse,
            clientProvider = { throw IllegalStateException("offline test") },
            ownerProvider = { "user" },
            libraryDao = library, ops = ops, offlineOnly = { offline })
    }

    @Test
    fun `offline create stores a local playlist and queues the server operation`() = runBlocking {
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao().apply { upsertSongs(listOf(song("a"), song("b"))) }
        val dao = FakeDownloadDao()
        val repository = repository(browse, library, dao)
        val error = repository.create(1L, "Mix", listOf("a", "b", "a"))
        assertNull(error)
        val local = browse.playlists.value.values.single()
        assertTrue(local.id.startsWith("local-"))
        assertTrue(local.pendingSync)
        assertEquals(3, browse.queueEntries(1L, local.id).size) // duplicates preserved
        assertEquals(OfflineOps.KIND_CREATE, dao.opsOnce(1L).single().kind)
    }

    @Test
    fun `offline addSongs applies the full new order locally and queues set content`() = runBlocking {
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao().apply { upsertSongs(listOf(song("a"), song("b"), song("c"))) }
        browse.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null,
            1, 100, null, "c0", 0L))
        browse.insertEntries(listOf(PlaylistEntryEntity(1L, "p1", 0, "a", "t", null, null, 100, null, null)))
        val dao = FakeDownloadDao()
        val repository = repository(browse, library, dao)
        assertNull(repository.addSongs(1L, "p1", listOf("b", "a")))
        assertEquals(listOf("a", "b", "a"), browse.queueEntries(1L, "p1").map { it.songId })
        assertEquals(true, browse.playlistOnce(1L, "p1")?.pendingSync)
        val op = dao.opsOnce(1L).single()
        assertEquals(OfflineOps.KIND_SET_CONTENT, op.kind)
        assertTrue(op.payload.contains("\"orderedSongIds\":[\"a\",\"b\",\"a\"]"))
        val payload = com.google.gson.Gson().fromJson(op.payload, OfflineOps.SetContentPayload::class.java)
        assertEquals(OfflineOps.signature(listOf("a")), payload.baselineContent)
    }

    @Test
    fun `offline rename updates the local row and queues with baseline`() = runBlocking {
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao()
        browse.insertPlaylist(PlaylistEntity(1L, "p1", "Old", "user", false, null, null,
            0, 0, null, "c0", 0L))
        val dao = FakeDownloadDao()
        val repository = repository(browse, library, dao)
        assertNull(repository.rename(1L, "p1", "New"))
        assertEquals("New", browse.playlistOnce(1L, "p1")?.name)
        assertEquals(true, browse.playlistOnce(1L, "p1")?.pendingSync)
        val op = dao.opsOnce(1L).single()
        assertEquals(OfflineOps.KIND_RENAME, op.kind)
        assertEquals("c0", op.baselineChanged)
    }

    @Test
    fun `offline removeEntry keeps duplicates apart by position`() = runBlocking {
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao()
        browse.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null,
            3, 300, null, null, 0L))
        browse.insertEntries(listOf(
            PlaylistEntryEntity(1L, "p1", 0, "a", "t", null, null, 100, null, null),
            PlaylistEntryEntity(1L, "p1", 1, "b", "t", null, null, 100, null, null),
            PlaylistEntryEntity(1L, "p1", 2, "a", "t", null, null, 100, null, null)))
        val dao = FakeDownloadDao()
        val repository = repository(browse, library, dao)
        assertNull(repository.removeEntry(1L, "p1", 2))
        assertEquals(listOf("a", "b"), browse.queueEntries(1L, "p1").map { it.songId })
    }

    @Test
    fun `offline edits to unknown songs fail without inventing metadata`() = runBlocking {
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao() // empty index
        browse.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null,
            0, 0, null, null, 0L))
        val dao = FakeDownloadDao()
        val repository = repository(browse, library, dao)
        assertNotNull(repository.addSongs(1L, "p1", listOf("ghost")))
        assertTrue(dao.opsOnce(1L).isEmpty())
        assertTrue(browse.queueEntries(1L, "p1").isEmpty())
    }
}
