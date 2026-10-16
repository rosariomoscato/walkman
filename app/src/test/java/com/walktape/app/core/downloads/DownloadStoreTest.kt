package com.walktape.app.core.downloads

import com.walktape.app.core.db.DownloadStatus
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.PlaylistEntryEntity
import com.walktape.app.core.db.SongEntity
import com.walktape.app.core.library.FakeLibraryDao
import com.walktape.app.sync.FakeBrowseDao
import com.walktape.app.sync.FakeDownloadDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM tests of download state and origin bookkeeping (W08-T01/T05): one audio file per
 * song shared across origins, honest removal rules and playlist reconciliation, all on
 * a real temporary directory so byte counts match files on disk.
 */
class DownloadStoreTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun song(id: String, size: Long = 1000) = SongEntity(
        profileId = 1L, id = id, parent = null, albumId = "al1", artistId = null,
        title = "t-$id", album = "Album", artist = "Artist", albumArtist = null,
        displayArtist = "Artist", track = 1, discNumber = 1, year = null, genre = null,
        coverArt = "cov", duration = 100, bitRate = 128, suffix = "mp3",
        contentType = "audio/mpeg", size = size, path = null, created = null,
        starred = null, type = null)

    private fun store(dao: FakeDownloadDao, browseDao: FakeBrowseDao, libraryDao: FakeLibraryDao,
                      root: File): DownloadStore {
        root.mkdirs()
        return DownloadStore(dao, libraryDao, browseDao,
            profileDirectory = { root },
            clientProvider = { throw IllegalStateException("no network in state tests") })
    }

    @Test
    fun `new downloads record the chosen representation and keep completed files on change`() = runBlocking {
        val dao = FakeDownloadDao()
        val library = FakeLibraryDao().apply { upsertSongs(listOf(song("a"), song("b"))) }
        val root = temporary.newFolder()
        val store = DownloadStore(dao, library, FakeBrowseDao(),
            profileDirectory = { root },
            clientProvider = { throw IllegalStateException("no network in state tests") },
            choiceFor = { DownloadChoice(com.walktape.app.core.db.DownloadRepresentation.REDUCED, 96) })
        store.enqueueSongs(1L, listOf(song("a")), DownloadStore.ORIGIN_SINGLE)
        val row = dao.download(1L, "a")!!
        assertEquals(com.walktape.app.core.db.DownloadRepresentation.REDUCED.name, row.representation)
        assertEquals(96, row.qualityKbps)
        // the original size does not predict a transcode transfer
        assertEquals(-1L, row.totalBytes)

        // a completed original keeps its bytes and origins when the choice changes
        val original = DownloadChoice(com.walktape.app.core.db.DownloadRepresentation.ORIGINAL, null)
        val completed = row.copy(status = DownloadStatus.COMPLETED.name,
            representation = com.walktape.app.core.db.DownloadRepresentation.ORIGINAL.name,
            qualityKbps = null, relativePath = "${DownloadStore.MUSIC_DIR}/a.mp3",
            bytes = 100, totalBytes = 100)
        dao.upsertDownload(completed)
        val second = DownloadStore(dao, library, FakeBrowseDao(),
            profileDirectory = { root },
            clientProvider = { throw IllegalStateException("no network in state tests") },
            choiceFor = { original })
        second.enqueueSongs(1L, listOf(song("a")), DownloadStore.ORIGIN_SINGLE)
        val after = dao.download(1L, "a")!!
        assertEquals(DownloadStatus.COMPLETED.name, after.status)
        assertEquals(com.walktape.app.core.db.DownloadRepresentation.ORIGINAL.name, after.representation)
        assertEquals(1, dao.origins(1L, "a").size)
    }

    @Test
    fun `enqueue creates one row per song and links origins`() = runBlocking {
        val dao = FakeDownloadDao()
        val store = store(dao, FakeBrowseDao(), FakeLibraryDao().apply {
            upsertSongs(listOf(song("a"), song("b")))
        }, temporary.newFolder())
        val queued = store.enqueueSongs(1L, listOf(song("a"), song("b")), DownloadStore.ORIGIN_SINGLE)
        assertEquals(2, queued)
        assertEquals(listOf("a", "b").sorted(), dao.downloadsOnce(1L).map { it.songId }.sorted())
        assertEquals(2, dao.originsFor(1L, DownloadStore.ORIGIN_SINGLE).size)
        // second enqueue of the same songs does not duplicate rows or queue work
        store.enqueueSongs(1L, listOf(song("a"), song("b")), DownloadStore.albumOrigin("al1"))
        assertEquals(2, dao.downloadsOnce(1L).size)
        assertEquals(2, dao.originsFor(1L, DownloadStore.albumOrigin("al1")).size)
        assertEquals(2, dao.origins(1L, "a").size) // single + album share one audio file
    }

    @Test
    fun `paused and failed downloads are requeued by a new enqueue`() = runBlocking {
        val dao = FakeDownloadDao()
        val library = FakeLibraryDao().apply { upsertSongs(listOf(song("a"))) }
        val store = store(dao, FakeBrowseDao(), library, temporary.newFolder())
        store.enqueueSongs(1L, listOf(song("a")), DownloadStore.ORIGIN_SINGLE)
        store.pause(1L, "a")
        assertEquals(DownloadStatus.PAUSED.name, dao.download(1L, "a")?.status)
        val queued = store.enqueueSongs(1L, listOf(song("a")), DownloadStore.albumOrigin("al1"))
        assertEquals(1, queued)
        assertEquals(DownloadStatus.QUEUED.name, dao.download(1L, "a")?.status)
    }

    @Test
    fun `remove deletes the file only when it is not kept by other origins`() = runBlocking {
        val dao = FakeDownloadDao()
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao().apply { upsertSongs(listOf(song("a"), song("b"))) }
        val root = temporary.newFolder()
        val store = store(dao, browse, library, root)
        store.enqueueSongs(1L, listOf(song("a")), DownloadStore.ORIGIN_SINGLE)
        store.enqueueSongs(1L, listOf(song("a"), song("b")), DownloadStore.playlistOrigin("p1"))
        // both files exist: simulate completed downloads
        val music = File(root, DownloadStore.MUSIC_DIR).apply { mkdirs() }
        val fileA = File(music, "a.mp3").apply { writeBytes(ByteArray(120)) }
        val fileB = File(music, "b.mp3").apply { writeBytes(ByteArray(140)) }
        dao.upsertDownload(dao.download(1L, "a")!!.copy(status = DownloadStatus.COMPLETED.name,
            relativePath = "${DownloadStore.MUSIC_DIR}/a.mp3", bytes = 120, totalBytes = 120))
        dao.upsertDownload(dao.download(1L, "b")!!.copy(status = DownloadStatus.COMPLETED.name,
            relativePath = "${DownloadStore.MUSIC_DIR}/b.mp3", bytes = 140, totalBytes = 140))
        browse.insertLocalAudio(com.walktape.app.core.db.LocalAudioEntity(1L, "a", "${DownloadStore.MUSIC_DIR}/a.mp3", 120))
        browse.insertLocalAudio(com.walktape.app.core.db.LocalAudioEntity(1L, "b", "${DownloadStore.MUSIC_DIR}/b.mp3", 140))

        // removing the user download of "a" keeps the file: the playlist still needs it
        // (modelled here by removing the single origin through reconcile, see below)
        assertTrue(store.completedFile(1L, "a") == fileA || store.completedFile(1L, "a")?.name == "a.mp3")
        // full remove (user action on the download list) drops row, origins, file and catalog
        store.remove(1L, "b")
        assertFalse(fileB.exists())
        assertNull(dao.download(1L, "b"))
        assertNull(browse.localAudio(1L, "b"))
        assertTrue(dao.origins(1L, "b").isEmpty())
        // "a" untouched: two origins still reference it
        assertTrue(fileA.exists())
        assertEquals(2, dao.origins(1L, "a").size)
    }

    @Test
    fun `playlist download dedupes songs while the cache keeps order and copies`() = runBlocking {
        val dao = FakeDownloadDao()
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao().apply { upsertSongs(listOf(song("a"), song("b"))) }
        browse.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null, 3, 300, null, null, 0L))
        browse.insertEntries(listOf(
            PlaylistEntryEntity(1L, "p1", 0, "a", "t", null, null, 100, null, null),
            PlaylistEntryEntity(1L, "p1", 1, "b", "t", null, null, 100, null, null),
            PlaylistEntryEntity(1L, "p1", 2, "a", "t", null, null, 100, null, null))) // duplicate kept
        val store = store(dao, browse, library, temporary.newFolder())
        val queued = store.enqueuePlaylist(1L, "p1")
        assertEquals(2, queued) // one audio file per distinct song
        assertEquals(3, browse.queueEntries(1L, "p1").size) // playlist copies untouched
        assertEquals(listOf("a", "b").sorted(), dao.downloadsOnce(1L).map { it.songId }.sorted())
    }

    @Test
    fun `reconcile mirrors the playlist and preserves files kept by other origins`() = runBlocking {
        val dao = FakeDownloadDao()
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao().apply {
            upsertSongs(listOf(song("a"), song("b"), song("c"), song("d")))
        }
        val root = temporary.newFolder()
        val store = store(dao, browse, library, root)
        val music = File(root, DownloadStore.MUSIC_DIR).apply { mkdirs() }
        val complete: suspend (String, Int) -> Unit = { id, bytes ->
            File(music, "$id.mp3").writeBytes(ByteArray(bytes))
            dao.upsertDownload(dao.download(1L, id)!!.copy(
                status = DownloadStatus.COMPLETED.name,
                relativePath = "${DownloadStore.MUSIC_DIR}/$id.mp3",
                bytes = bytes.toLong(), totalBytes = bytes.toLong(),
                serverSize = 1000))
            browse.insertLocalAudio(
                com.walktape.app.core.db.LocalAudioEntity(1L, id, "${DownloadStore.MUSIC_DIR}/$id.mp3", bytes.toLong()))
        }
        // p1 has a,b,c; b and c are ALSO single downloads; d joins in the new version, a and c leave
        browse.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null, 3, 300, null, null, 0L))
        browse.insertEntries(listOf(
            PlaylistEntryEntity(1L, "p1", 0, "a", "t", null, null, 100, null, null),
            PlaylistEntryEntity(1L, "p1", 1, "b", "t", null, null, 100, null, null),
            PlaylistEntryEntity(1L, "p1", 2, "c", "t", null, null, 100, null, null)))
        store.enqueueSongs(1L, listOf(song("b"), song("c")), DownloadStore.ORIGIN_SINGLE)
        store.enqueuePlaylist(1L, "p1")
        complete("a", 110); complete("b", 120); complete("c", 130)

        // new playlist content: b, b, d (duplicates preserved, a/c leave the playlist)
        browse.deleteEntries(1L, "p1")
        browse.insertEntries(listOf(
            PlaylistEntryEntity(1L, "p1", 0, "b", "t", null, null, 100, null, null),
            PlaylistEntryEntity(1L, "p1", 1, "b", "t", null, null, 100, null, null),
            PlaylistEntryEntity(1L, "p1", 2, "d", "t", null, null, 100, null, null)))

        val result = store.reconcilePlaylistDownload(1L, "p1")

        // d enqueued (not previously present)
        assertEquals(DownloadStatus.QUEUED.name, dao.download(1L, "d")?.status)
        // a left the playlist with no other origin: file and row removed
        assertNull(dao.download(1L, "a"))
        assertFalse(File(music, "a.mp3").exists())
        // c left the playlist but is kept as a single download
        assertTrue(result.keptForOtherOrigins.contains("c"))
        assertTrue(File(music, "c.mp3").exists())
        assertEquals(DownloadStatus.COMPLETED.name, dao.download(1L, "c")?.status)
        // b stayed in the playlist: file kept, single origin still attached
        assertTrue(File(music, "b.mp3").exists())
        assertEquals(DownloadStatus.COMPLETED.name, dao.download(1L, "b")?.status)
        assertEquals(2, dao.origins(1L, "b").size)
        assertEquals(listOf("a"), result.removed)
    }

    @Test
    fun `reconcile requeues completed files whose server size changed`() = runBlocking {
        val dao = FakeDownloadDao()
        val browse = FakeBrowseDao()
        val library = FakeLibraryDao()
        val root = temporary.newFolder()
        val store = store(dao, browse, library, root)
        browse.insertPlaylist(PlaylistEntity(1L, "p1", "Mix", "user", false, null, null, 1, 100, null, null, 0L))
        browse.insertEntries(listOf(PlaylistEntryEntity(1L, "p1", 0, "a", "t", null, null, 100, null, null)))
        // first version of the song
        library.upsertSongs(listOf(song("a", size = 1000)))
        store.enqueuePlaylist(1L, "p1")
        val music = File(root, DownloadStore.MUSIC_DIR).apply { mkdirs() }
        File(music, "a.mp3").writeBytes(ByteArray(100))
        dao.upsertDownload(dao.download(1L, "a")!!.copy(status = DownloadStatus.COMPLETED.name,
            relativePath = "${DownloadStore.MUSIC_DIR}/a.mp3", bytes = 100, totalBytes = 100, serverSize = 1000))
        // the server now has a different file for the same id
        library.songs[1L to "a"] = song("a", size = 2000)

        val result = store.reconcilePlaylistDownload(1L, "p1")
        assertEquals(1, result.requeued)
        assertEquals(DownloadStatus.QUEUED.name, dao.download(1L, "a")?.status)
        assertFalse(File(music, "a.mp3").exists()) // stale file dropped before the re-download
        assertEquals(2000L, dao.download(1L, "a")?.serverSize)
    }

    @Test
    fun `musicBytes counts real files on disk`() = runBlocking {
        val root = temporary.newFolder()
        val music = File(root, DownloadStore.MUSIC_DIR).apply { mkdirs() }
        File(music, "a.mp3").writeBytes(ByteArray(150))
        File(music, "b.part").writeBytes(ByteArray(50))
        assertEquals(200L, DownloadStore.musicBytes(root))
    }
}
