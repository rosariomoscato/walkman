package com.walktape.app.core.library

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.profiles.CredentialVault
import com.walktape.app.core.profiles.ProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LibraryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migrateFromWave03SchemaCreatesLibraryTables() {
        val name = "wave04-migration-test.db"
        context.deleteDatabase(name)
        val helper = object : SQLiteOpenHelper(context, name, null, 2) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `serverUrl` TEXT NOT NULL, `username` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `credentialId` TEXT NOT NULL DEFAULT '', `encryptedPassword` TEXT NOT NULL DEFAULT '', `allowLocalHttp` INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profile_name` ON `profile` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '495c6c6f3a5f3fc95acee727a8225777')")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        helper.writableDatabase.execSQL("INSERT INTO profile (id,name,serverUrl,username,isActive,createdAt,credentialId,encryptedPassword,allowLocalHttp) VALUES (1,'P','https://example.org','user',1,123,'k','enc',0)")
        helper.close()
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name)
            .addMigrations(*WalkTapeDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM song").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
            }
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM album").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
            }
            db.openHelper.readableDatabase.query("SELECT name FROM profile").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("P", cursor.getString(0))
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun realServerIndexAndIsolationFromEmulator() = runBlocking {
        val envFile = File("/data/local/tmp/walktape-test-server.env")
        org.junit.Assume.assumeTrue("Opt-in local server config required", envFile.exists())
        val config = envFile.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val url = config.getValue("ND_URL").replace("localhost", "10.0.2.2")
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "library-real-${UUID.randomUUID()}").apply { mkdirs() }
        val repo = ProfileRepository(db, CredentialVault(), root)
        var id: Long? = null
        var otherProfile: Long? = null
        try {
            id = (repo.addVerified("Library real", url, config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
                as com.walktape.app.core.net.ApiResult.Success).value
            val sync = LibrarySync(db.libraryDao(), playlistIndex = PlaylistIndex(db.browseDao())) { repo.clientFor(id) }
            val started = System.currentTimeMillis()
            val ok = sync.sync(id)
            val elapsedSeconds = (System.currentTimeMillis() - started) / 1000.0
            assertTrue("Real indexing must succeed (details suppressed)", ok)
            val state = db.libraryDao().state(id)!!
            assertTrue("Expected the real library to have at least 100 albums, had ${state.albumCount}", state.albumCount >= 100)
            assertTrue("Expected the real library to have at least 1000 songs, had ${state.songCount}", state.songCount >= 1000)
            assertTrue("Artist count must match server magnitude, had ${state.artistCount}", state.artistCount >= 100)
            val firstAlbum = db.libraryDao().albums(id, "name", "%").let { source ->
                var page: androidx.paging.PagingSource.LoadResult.Page<Int, com.walktape.app.core.db.AlbumEntity>? = null
                page = source.load(
                    androidx.paging.PagingSource.LoadParams.Refresh(null, 1, false)
                ) as androidx.paging.PagingSource.LoadResult.Page
                page.data.first()
            }
            val albumSongs = db.libraryDao().songsInAlbums(id, listOf(firstAlbum.id))
            assertEquals(firstAlbum.songCount, albumSongs.size)
            otherProfile = (repo.addVerified("Isolation empty", url, config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
                as com.walktape.app.core.net.ApiResult.Success).value
            assertEquals(0, db.libraryDao().songCount(otherProfile))
            val cover = firstAlbum.coverArt
            if (cover != null) {
                val provider: suspend () -> com.walktape.app.core.net.OpenSubsonicClient? = { repo.clientFor(id) }
                val bitmap = CoverArtStore(File(root, "profiles/$id/covers"), provider).bitmap(cover, 300)
                assertNotNull("At least one real cover must decode", bitmap)
            }
            println("wave04-real-index ok seconds=$elapsedSeconds albums=${state.albumCount} songs=${state.songCount} artists=${state.artistCount}")
            repo.remove(otherProfile)
            otherProfile = null
        } finally {
            otherProfile?.let { repo.remove(it) }
            id?.let { repo.remove(it) }
            db.close()
            root.deleteRecursively()
        }
    }
}
