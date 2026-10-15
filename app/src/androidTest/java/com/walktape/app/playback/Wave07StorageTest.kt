package com.walktape.app.playback

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.walktape.app.core.db.*
import com.walktape.app.core.library.*
import com.walktape.app.core.net.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Wave07StorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun open() = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
    private suspend fun profile(db: WalkTapeDatabase, name: String) = db.profileDao().insert(
        ProfileEntity(name = name, serverUrl = "http://fixture.invalid", username = "fixture", createdAt = 1))
    private fun seedSong(db: WalkTapeDatabase, id: Long) {
        db.openHelper.writableDatabase.execSQL("INSERT INTO song(profileId,id,title,track,discNumber,duration,bitRate,size) VALUES($id,'song','Fixture',1,1,10,128,100)")
    }
    private fun client(server: MockWebServer) = OpenSubsonicClient(
        ServerAddress.parse(server.url("/").toString(), true)!!, Credentials("fixture", "fixture"))
    private fun ok() = MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build()

    @Test fun sharedReadonlyAndChangedPlaylistAreNotMutated(): Unit = runBlocking {
        val db = open()
        MockWebServer().apply { start() }.use { server ->
            try {
                val profile = profile(db,"Permissions")
                val repository = PlaylistRepository(db.browseDao(), { client(server) }, { "fixture" })
                fun playlist(owner: String, readonly: Boolean, changed: String = "new") = MockResponse.Builder().body(
                    """{"subsonic-response":{"status":"ok","version":"1.16.1","playlist":{"id":"p","name":"Fixture","owner":"$owner","readonly":$readonly,"changed":"$changed","songCount":0}}}""").build()
                server.enqueue(playlist("other",false))
                assertEquals(ApiError.Protocol(50,ProtocolErrorKind.FORBIDDEN),repository.rename(profile,"p","Changed"))
                assertEquals(1,server.requestCount)
                server.enqueue(playlist("fixture",true))
                assertEquals(ApiError.Protocol(50,ProtocolErrorKind.FORBIDDEN),repository.delete(profile,"p"))
                assertEquals(2,server.requestCount)
                db.browseDao().insertPlaylist(PlaylistEntity(profile,"p","Fixture","fixture",false,null,null,0,0,null,"old",1))
                server.enqueue(playlist("fixture",false))
                assertEquals(ApiError.InvalidRequest,repository.reorder(profile,"p",emptyList()))
                assertEquals("Stale positions must not trigger a write",3,server.requestCount)
            } finally { db.close() }
        }
    }

    @Test fun migrateSixPreservesQueueOriginCopiesAndAddsNewStores(): Unit = runBlocking {
        val name = "wave07-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open("com.walktape.app.core.db.WalkTapeDatabase/6.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        val helper = object : SQLiteOpenHelper(context, name, null, 6) {
            override fun onCreate(db: SQLiteDatabase) {
                schema.getAsJsonArray("entities").forEach { item ->
                    val entity = item.asJsonObject
                    val table = entity.get("tableName").asString
                    db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                    entity.getAsJsonArray("indices")?.forEach { index ->
                        db.execSQL(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table))
                    }
                }
                schema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            }
            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
        }
        helper.writableDatabase.apply {
            execSQL("INSERT INTO profile(id,name,serverUrl,username,isActive,createdAt,credentialId,encryptedPassword,allowLocalHttp) VALUES(1,'Existing','https://fixture.invalid','fixture',1,1,'key','cipher',0)")
            execSQL("INSERT INTO queue_state VALUES(1,'queue/a',12345,2,0,100)")
            execSQL("INSERT INTO queue_entry VALUES(1,0,0,'queue/a','song','First','Artist','Album',90000,'Origin')")
            execSQL("INSERT INTO queue_entry VALUES(1,1,1,'queue/b','song','Copy','Artist','Album',90000,'Origin')")
            execSQL("INSERT INTO playlist(profileId,id,name,isPublic,songCount,duration,fetchedAt) VALUES(1,'p','Existing',0,0,0,1)")
        }
        helper.close()
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name)
            .addMigrations(*WalkTapeDatabase.MIGRATIONS).build()
        try {
            val queue = db.queueDao().load(1)!!
            assertEquals(12345L, queue.positionMs)
            assertEquals(listOf("song", "song"), queue.items.map { it.songId })
            assertEquals(listOf("Origin", "Origin"), queue.items.map { it.originName })
            assertNull(queue.mixtapeName)
            assertFalse(db.browseDao().playlist(1, "p").first()!!.readOnly)
            db.queueDao().replace(queue.copy(mixtapeName = "My mix"))
            assertEquals("My mix", db.queueDao().load(1)!!.mixtapeName)
            seedSong(db, 1)
            db.lyricsDao().insert(LyricsEntity(1,"song",null,null,null,false,"[]",1))
            val state = ListeningStateEntity(1,"queue/a","event","song","Fixture",null,null,1,5_000,true)
            db.scrobbleDao().record(state, ScrobbleEventEntity(1,"event","song","Fixture",null,null,1,5_000,2))
            assertEquals(state, db.scrobbleDao().listeningState(1))
            assertEquals(1, db.scrobbleDao().pending(1, 10).size)
            db.profileDao().delete(1)
            assertNull(db.scrobbleDao().listeningState(1))
            assertTrue(db.scrobbleDao().pending(1,10).isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun favoritesAndLyricsCacheAreIsolatedAndErrorsDoNotInventSuccess(): Unit = runBlocking {
        val db = open()
        MockWebServer().apply { start() }.use { server ->
            try {
                val a = profile(db,"A"); val b = profile(db,"B")
                seedSong(db,a); seedSong(db,b)
                val favorites = StarRepository(db.libraryDao(), { client(server) })
                server.enqueue(ok())
                assertNull(favorites.setStarred(a, StarRepository.Kind.SONG,"song",true))
                assertEquals(1,db.browseDao().favoriteSongs(a).first().size)
                assertTrue(db.browseDao().favoriteSongs(b).first().isEmpty())
                server.enqueue(MockResponse.Builder().code(503).build())
                assertNotNull(favorites.setStarred(a, StarRepository.Kind.SONG,"song",false))
                assertEquals(1,db.browseDao().favoriteSongs(a).first().size)
                server.enqueue(ok())
                assertNull(favorites.setStarred(a, StarRepository.Kind.SONG,"song",false))
                assertTrue(db.browseDao().favoriteSongs(a).first().isEmpty())
                server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1","lyricsList":{"structuredLyrics":[{"synced":true,"offset":100,"line":[{"value":"Fixture line","start":1000}]}]}}}""").build())
                val lyrics = LyricsRepository(db.lyricsDao(), { client(server) })
                val first = lyrics.lyrics(a,"song") as LyricsRepository.Result.Available
                assertEquals(900L,first.lyrics.lines.single().startMs)
                assertFalse(first.fromCache)
                val count = server.requestCount
                assertTrue((lyrics.lyrics(a,"song") as LyricsRepository.Result.Available).fromCache)
                assertEquals(count,server.requestCount)
                assertNull(db.lyricsDao().get(b,"song"))
            } finally { db.close() }
        }
    }

    @Test fun offlineEventsSurviveReopenRetryAckAndDoNotResend(): Unit = runBlocking {
        val name = "scrobble-${UUID.randomUUID()}.db"
        fun openDisk() = Room.databaseBuilder(context,WalkTapeDatabase::class.java,name).build()
        var db = openDisk()
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        MockWebServer().apply { start() }.use { server ->
            try {
                val a = profile(db,"A"); val b = profile(db,"B")
                val state = ListeningStateEntity(a,"queue/a","event","song","Fixture",null,null,1234,5000,true)
                val event = ScrobbleEventEntity(a,"event","song","Fixture",null,null,1234,5000,1235)
                db.scrobbleDao().record(state,event)
                db.scrobbleDao().record(state,event) // same earned listen, e.g. after service recovery
                assertEquals(1,db.scrobbleDao().count(a))
                server.enqueue(MockResponse.Builder().code(503).build())
                ScrobbleSender(db.scrobbleDao(),{ client(server) },scope).deliverPending()
                assertEquals(1,db.scrobbleDao().pending(a,10).size)
                assertTrue(db.scrobbleDao().pending(b,10).isEmpty())
                db.close(); db = openDisk()
                assertEquals(state,db.scrobbleDao().listeningState(a))
                server.enqueue(ok())
                ScrobbleSender(db.scrobbleDao(),{ client(server) },scope).deliverPending()
                assertTrue(db.scrobbleDao().pending(a,10).isEmpty())
                assertNotNull(db.scrobbleDao().history(a).first().single().sentAt)
                val count = server.requestCount
                ScrobbleSender(db.scrobbleDao(),{ client(server) },scope).deliverPending()
                assertEquals(count,server.requestCount)
            } finally { scope.cancel(); db.close(); context.deleteDatabase(name) }
        }
    }
}
