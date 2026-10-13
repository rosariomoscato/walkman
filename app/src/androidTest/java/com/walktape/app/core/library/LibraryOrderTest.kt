package com.walktape.app.core.library

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.AlbumEntity
import com.walktape.app.core.db.SongEntity
import com.walktape.app.core.db.WalkTapeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryOrderTest {
    @Test fun multidiscOrderAndAlbumReplacementPreserveOtherProfiles(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        try {
            val dao = db.libraryDao()
            val album = AlbumEntity(1, "a", "Compilation", "Album artist", null, null, 3,
                300, 2000, "Rock", null, true, null)
            val base = SongEntity(profileId = 1, id = "first", parent = null, albumId = "a",
                artistId = null, title = "First", album = "Compilation", artist = "Track artist",
                albumArtist = "Album artist", displayArtist = "Track artist", track = 1,
                discNumber = 1, year = 2000, genre = "Rock", coverArt = null, duration = 100,
                bitRate = 320, suffix = "mp3", contentType = "audio/mpeg", size = 1000,
                path = null, created = null, starred = null, type = "music")
            dao.replaceAlbum(1, album, listOf(base.copy(id = "third", discNumber = 2),
                base.copy(id = "second", track = 2), base))
            dao.replaceAlbum(2, album.copy(profileId = 2), listOf(base.copy(profileId = 2)))
            assertEquals(listOf("first", "second", "third"), dao.observeAlbumSongs(1, "a").first().map { it.id })
            assertTrue(dao.album(1, "a")!!.isCompilation)
            assertNotEquals(base.artist, base.albumArtist)
            dao.replaceAlbum(1, album.copy(songCount = 1), listOf(base))
            assertEquals(1, dao.songCount(1))
            assertEquals(1, dao.songCount(2))
        } finally { db.close() }
    }
}
