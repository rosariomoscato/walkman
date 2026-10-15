package com.walktape.app.playback

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.*
import com.walktape.app.core.library.PlaylistRepository
import com.walktape.app.core.net.*
import com.walktape.app.sync.OfflineOps
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Mutates only a uniquely named dedicated playlist on the real non-admin test account. */
@RunWith(AndroidJUnit4::class)
class Wave08RealSyncTest {
    @Test fun offlineCreateEditsAndConcurrentConflictArePreservedAndExplicitlyResolved(): Unit = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue(env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        val client = OpenSubsonicClient(ServerAddress.parse(
            config.getValue("ND_URL").replace("localhost", "10.0.2.2"), true)!!,
            Credentials(config.getValue("ND_USER"), config.getValue("ND_PASS")))
        val album = (client.albumList("newest", 1, 0) as ApiResult.Success).value.first()
        val songs = (client.album(album.id) as ApiResult.Success).value.songs.take(2)
        assumeTrue(songs.size == 2)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefix = "WalkTape W08 test ${UUID.randomUUID()}"
        var serverId: String? = null
        var blocked = true
        try {
            val profile = db.profileDao().insert(ProfileEntity(name = "Real sync probe",
                serverUrl = "https://fixture.invalid", username = "walktape-test", createdAt = 1))
            db.libraryDao().upsertSongs(songs.map { s -> SongEntity(profile, s.id, null, s.albumId, s.artistId,
                s.title, s.album, s.artist, s.albumArtist, s.displayArtist, s.track, s.discNumber, s.year,
                s.genre, s.coverArt, s.duration, s.bitRate, s.suffix, s.contentType, s.size,
                null, s.created, s.starred, s.type) })
            val ops = OfflineOps(db.downloadDao(), db.browseDao(), db.libraryDao(),
                { client }, { "walktape-test" }, { blocked }, scope, autoSync = false)
            val repository = PlaylistRepository(db.browseDao(), { client }, { "walktape-test" },
                db.libraryDao(), ops, { blocked }, { block -> db.withTransaction { block() } })
            val a = songs[0].id; val b = songs[1].id
            assertNull(repository.create(profile, prefix, listOf(a, b, a)))
            val local = db.browseDao().pendingPlaylistsOnce(profile).single()
            assertNull(repository.rename(profile, local.id, "$prefix renamed"))
            assertEquals(1, db.downloadDao().opsOnce(profile).size)
            blocked = false
            assertTrue(ops.drain(profile))
            val candidates = (client.playlists() as ApiResult.Success).value.filter { it.name == "$prefix renamed" }
            assertEquals(1, candidates.size)
            val id = candidates.single().id
            serverId = id
            assertEquals(listOf(a, b, a), (client.playlist(id) as ApiResult.Success).value.entries.map { it.id })

            blocked = true
            assertNull(repository.addSongs(profile, id, listOf(b)))
            assertNull(repository.removeEntry(profile, id, 1))
            assertNull(repository.reorder(profile, id, listOf(b, a, a)))
            val localOrder = db.browseDao().queueEntries(profile, id).map { it.songId }
            assertEquals(listOf(b, a, a), localOrder)
            // A concurrent edit is made explicitly to this dedicated test playlist only.
            assertTrue(client.updatePlaylist(id, songIdsToAdd = listOf(b)) is ApiResult.Success)
            blocked = false
            assertFalse(ops.drain(profile))
            val conflict = db.downloadDao().opsOnce(profile).single()
            assertEquals(PendingOpState.CONFLICT.name, conflict.state)
            assertEquals(localOrder, db.browseDao().queueEntries(profile, id).map { it.songId })
            assertEquals(listOf(a, b, a, b), (client.playlist(id) as ApiResult.Success).value.entries.map { it.id })
            assertTrue(ops.resolveConflictKeepLocal(profile, conflict.opId))
            assertEquals(localOrder, (client.playlist(id) as ApiResult.Success).value.entries.map { it.id })
            assertTrue(db.downloadDao().opsOnce(profile).isEmpty())
        } finally {
            serverId?.let { client.deletePlaylist(it) }
            // A create may have succeeded before a lost response: remove only our exact
            // unique names, never any user's playlist with a generic prefix.
            val list = client.playlists()
            if (list is ApiResult.Success) list.value.filter {
                it.name == prefix || it.name == "$prefix renamed"
            }.forEach { client.deletePlaylist(it.id) }
            scope.cancel(); db.close()
        }
    }
}
