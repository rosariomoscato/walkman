package com.walktape.app.core.library

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.walktape.app.core.db.*
import com.walktape.app.core.net.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BrowseInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun migrationPreservesVersionThreeIndex(): Unit = runBlocking {
        val name = "browse-migration-test.db"
        context.deleteDatabase(name)
        val schema = instrumentation.context.assets.open("com.walktape.app.core.db.WalkTapeDatabase/3.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        val helper = object : SQLiteOpenHelper(context, name, null, 3) {
            override fun onCreate(db: SQLiteDatabase) {
                schema.getAsJsonArray("entities").forEach {
                    val entity = it.asJsonObject
                    val table = entity.get("tableName").asString
                    db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                    entity.getAsJsonArray("indices")?.forEach { index ->
                        db.execSQL(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                    }
                }
                schema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        helper.writableDatabase.execSQL("INSERT INTO profile(id,name,serverUrl,username,isActive,createdAt,credentialId,encryptedPassword,allowLocalHttp) VALUES(1,'Existing','https://example.org','fixture',1,123,'id','encrypted-fixture',0)")
        helper.writableDatabase.execSQL("INSERT INTO album(profileId,id,name,songCount,duration,isCompilation) VALUES(1,'existing','Preserved',2,200,0)")
        helper.close()
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name).addMigrations(*WalkTapeDatabase.MIGRATIONS).build()
        try {
            assertEquals("Preserved", db.libraryDao().album(1, "existing")!!.name)
            assertNull(db.libraryDao().album(1, "existing")!!.played)
            assertEquals("encrypted-fixture", db.profileDao().get(1)!!.encryptedPassword)
            assertEquals(0, db.browseDao().playlistCount(1))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun queriesArePagedFilteredBoundAndProfileIsolated(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "browse-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val songs = (0 until 10000).map { index -> fixtureSong("s$index", year = if (index % 2 == 0) 2000 else 2001,
                genre = if (index % 2 == 0) "Rock" else "Pop") }
            db.libraryDao().upsertSongs(songs)
            db.libraryDao().upsertSongs(listOf(fixtureSong("other").copy(profileId = 2)))
            db.libraryDao().upsertAlbums(listOf(AlbumEntity(1,"a","Fixture album","Album artist","ar",null,10000,1000000,2000,"Rock",null,false,null)))
            db.libraryDao().upsertArtists(listOf(ArtistEntity(1,"ar","Fixture artist",null,1)))
            db.libraryDao().upsertGenres(listOf(GenreEntity(1,"Rock","Rock",5000,1)))
            val dao = db.browseDao()
            val before = System.nanoTime()
            val page = page(dao.songs(BrowseQueries.list(BrowseKind.SONG,1,LibraryFilter(genre="Rock",year=2000),"title")))
            val millis = (System.nanoTime()-before)/1000000.0
            assertEquals(80, page.size)
            assertTrue(page.all { it.profileId==1L && it.genre=="Rock" && it.year==2000 })
            assertTrue(page(dao.songs(BrowseQueries.list(BrowseKind.SONG,1,LibraryFilter(query="' OR 1=1 --"),"title"))).isEmpty())
            db.libraryDao().upsertSongs(listOf(fixtureSong("literal").copy(title="100%_literal")))
            assertEquals(listOf("literal"), page(dao.songs(BrowseQueries.list(BrowseKind.SONG,1,LibraryFilter(query="%_"),"title"))).map { it.id })
            val catalog = LocalAudioCatalog(dao) { File(root,"profiles/$it") }
            val directory = File(root,"profiles/1").apply { mkdirs() }
            File(directory,"fixture.audio").writeBytes(byteArrayOf(1,2,3))
            assertFalse(catalog.register(1,"s0","../outside"))
            assertTrue(catalog.register(1,"s0","fixture.audio"))
            assertEquals(listOf("s0"), page(dao.songs(BrowseQueries.list(BrowseKind.SONG,1,LibraryFilter(offlineOnly=true),"title"))).map { it.id })
            assertTrue("An album with one local track is not fully offline",page(dao.albums(BrowseQueries.list(BrowseKind.ALBUM,1,LibraryFilter(offlineOnly=true),"name"))).isEmpty())
            val playlist = PlaylistEntity(1,"partial","Partial",null,false,null,null,2,200,null,null,1)
            dao.replacePlaylist(playlist,listOf(PlaylistEntryEntity(1,"partial",0,"s0","Zero",null,null,100,2000,"Rock"),
                PlaylistEntryEntity(1,"partial",1,"s1","One",null,null,100,2001,"Pop")))
            assertTrue(page(dao.playlists(BrowseQueries.list(BrowseKind.PLAYLIST,1,LibraryFilter(offlineOnly=true),"name"))).isEmpty())
            File(directory,"second.audio").writeBytes(byteArrayOf(3,2,1))
            assertTrue(catalog.register(1,"s1","second.audio"))
            assertEquals(listOf("partial"),page(dao.playlists(BrowseQueries.list(BrowseKind.PLAYLIST,1,LibraryFilter(offlineOnly=true),"name"))).map { it.id })
            db.libraryDao().upsertAlbums(listOf(AlbumEntity(1,"small","Small",null,null,null,2,200,2000,"Rock",null,false,null)))
            db.libraryDao().upsertSongs(listOf(fixtureSong("local1").copy(albumId="small"),fixtureSong("local2").copy(albumId="small")))
            assertTrue(catalog.register(1,"local1","fixture.audio"))
            assertTrue(page(dao.albums(BrowseQueries.list(BrowseKind.ALBUM,1,LibraryFilter(offlineOnly=true),"name"))).isEmpty())
            assertTrue(catalog.register(1,"local2","second.audio"))
            assertEquals(listOf("small"),page(dao.albums(BrowseQueries.list(BrowseKind.ALBUM,1,LibraryFilter(offlineOnly=true),"name"))).map { it.id })
            assertTrue(page(dao.songs(BrowseQueries.list(BrowseKind.SONG,2,LibraryFilter(offlineOnly=true),"title"))).isEmpty())
            val results = page(dao.search(BrowseQueries.search(1,LibraryFilter(query="Fixture"))))
            assertTrue(results.all { it.profileId==1L })
            assertTrue(results.any { it.kind=="ALBUM" } && results.any { it.kind=="ARTIST" })
            val warmed=(0 until 3).map {
                val started=System.nanoTime()
                assertEquals(80,page(dao.songs(BrowseQueries.list(BrowseKind.SONG,1,LibraryFilter(genre="Rock",year=2000),"title"))).size)
                (System.nanoTime()-started)/1000000.0
            }
            db.libraryDao().upsertAlbums((0 until 20).map {
                AlbumEntity(1,"home$it","Home $it",null,null,null,1,100,2000,"Rock",
                    "2026-01-${(it+1).toString().padStart(2,'0')}T00:00:00Z",false,
                    if(it==1) "2026-01-01T00:00:00Z" else null,
                    played = if(it==2) "2026-01-03T00:00:00Z" else null)
            })
            assertEquals(8,dao.recentAlbums(1).first().size)
            assertEquals(listOf("home2"),dao.playedAlbums(1).first().map { it.id })
            assertEquals(listOf("home1"),dao.favoriteAlbums(1).first().map { it.id })
            assertTrue(dao.recentAlbums(2).first().isEmpty())
            println("wave04-fixture-performance songs=10000 filteredPage=80 coldMillis=$millis warmAverageMillis=${warmed.average()}")
        } finally { db.close(); root.deleteRecursively() }
    }

    @Test fun realServerPlaylistsAreStoredForOfflineReading(): Unit = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        org.junit.Assume.assumeTrue("Opt-in server config required",env.exists())
        val config=env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val client=OpenSubsonicClient(ServerAddress.parse(config.getValue("ND_URL").replace("localhost","10.0.2.2"),true)!!,
            Credentials(config.getValue("ND_USER"),config.getValue("ND_PASS")))
        val db=Room.inMemoryDatabaseBuilder(context,WalkTapeDatabase::class.java).build()
        try {
            val result=client.playlists()
            assertTrue("Real playlist list must load (details suppressed)",result is ApiResult.Success)
            val list=(result as ApiResult.Success).value
            assertNull("Real read-only index must succeed",PlaylistIndex(db.browseDao()).refresh(31,client))
            assertEquals(list.size,db.browseDao().playlistCount(31))
            assertEquals(0,db.browseDao().playlistCount(32))
            for (p in list) {
                assertEquals(p.songCount,db.browseDao().entryCount(31,p.id))
            }
            println("wave04-real-playlists count=${list.size}")
        } finally { db.close() }
    }

    @Test fun playlistRefreshKeepsOrderDuplicatesAndDoesNotEraseCacheOnFailure(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        MockWebServer().apply { start() }.use { server ->
            try {
                val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(),true)!!,Credentials("fixture","fixture"))
                val dao = db.browseDao()
                val index = PlaylistIndex(dao)
                server.enqueue(response(""""playlists":{"playlist":[{"id":"p","name":"Mix","songCount":2}]}"""))
                server.enqueue(response(""""playlist":{"id":"p","name":"Mix","songCount":2,"entry":[{"id":"same","title":"First"},{"id":"same","title":"Repeat"}]}"""))
                assertNull(index.refresh(1,client))
                assertEquals(listOf("First","Repeat"),page(dao.entries(1,"p")).map { it.title })
                assertEquals(listOf(0,1),page(dao.entries(1,"p")).map { it.position })
                assertEquals(0,dao.playlistCount(2))
                server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40}}}""").build())
                assertNotNull(index.refresh(1,client))
                assertEquals(1,dao.playlistCount(1))
                server.enqueue(response(""""playlists":{}"""))
                assertNull(index.refresh(1,client))
                assertTrue(page(dao.entries(1,"p")).isEmpty())
            } finally { db.close() }
        }
    }

    private fun response(contents: String) = MockResponse.Builder()
        .body("""{"subsonic-response":{"status":"ok","version":"1.16.1",$contents}}""").build()

    private suspend fun <T:Any> page(source: PagingSource<Int,T>): List<T> {
        val result = source.load(PagingSource.LoadParams.Refresh(null,80,false))
        assertTrue("Paging query must return a page",result is PagingSource.LoadResult.Page)
        return (result as PagingSource.LoadResult.Page).data
    }

    private fun fixtureSong(id:String, year:Int=2000, genre:String="Rock") = SongEntity(
        profileId=1,id=id,parent=null,albumId="a",artistId="ar",title="Fixture $id",album="Fixture album",
        artist="Track artist",albumArtist="Album artist",displayArtist=null,track=1,discNumber=1,year=year,
        genre=genre,coverArt=null,duration=100,bitRate=320,suffix="mp3",contentType="audio/mpeg",size=1000,
        path=null,created=null,starred=null,type="music")
}
