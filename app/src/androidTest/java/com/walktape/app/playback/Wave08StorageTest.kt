package com.walktape.app.playback

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.walktape.app.core.db.*
import com.walktape.app.core.downloads.DownloadStore
import com.walktape.app.core.net.*
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Wave08StorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun migrateSevenPreservesLibraryQueueLyricsAndListeningEvents(): Unit = runBlocking {
        val name = "wave08-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open("com.walktape.app.core.db.WalkTapeDatabase/7.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        val helper = object : SQLiteOpenHelper(context, name, null, 7) {
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
            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
        }
        helper.writableDatabase.apply {
            execSQL("INSERT INTO profile(id,name,serverUrl,username,isActive,createdAt,credentialId,encryptedPassword,allowLocalHttp) VALUES(1,'Existing','https://fixture.invalid','fixture',1,1,'key','cipher',0)")
            execSQL("INSERT INTO song(profileId,id,title,track,discNumber,duration,bitRate,size) VALUES(1,'s','Track',1,1,100,128,1000)")
            execSQL("INSERT INTO queue_state VALUES(1,'queue/a',12345,2,0,100,'Keep mix')")
            execSQL("INSERT INTO queue_entry VALUES(1,0,0,'queue/a','s','Track','Artist','Album',100000,'Origin')")
            execSQL("INSERT INTO lyrics VALUES(1,'s',NULL,NULL,NULL,0,'[]',1)")
            execSQL("INSERT INTO scrobble_event VALUES(1,'event','s','Track',NULL,NULL,1,5000,2,NULL)")
            execSQL("INSERT INTO listening_state VALUES(1,'queue/a','event','s','Track',NULL,NULL,1,5000,1)")
        }
        helper.close()
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name)
            .addMigrations(*WalkTapeDatabase.MIGRATIONS).build()
        try {
            assertEquals("Keep mix", db.queueDao().load(1)!!.mixtapeName)
            assertEquals(12345L, db.queueDao().load(1)!!.positionMs)
            assertNotNull(db.lyricsDao().get(1, "s"))
            assertEquals(1, db.scrobbleDao().pending(1, 10).size)
            assertTrue(db.scrobbleDao().listeningState(1)!!.emitted)
            assertEquals("s", db.libraryDao().songsByIds(1, listOf("s")).single().id)
            db.downloadDao().upsertSettings(ProfileSettingsEntity(1, offlineOnly = true))
            assertTrue(db.downloadDao().settings(1)!!.offlineOnly)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun interruptedTransferResumesValidatedTailAndProgressUpdatesKeepOrigins(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "wave08-${UUID.randomUUID()}").apply { mkdirs() }
        MockWebServer().apply { start() }.use { server ->
            try {
                val profile = db.profileDao().insert(ProfileEntity(name = "Download fixture",
                    serverUrl = "https://fixture.invalid", username = "fixture", createdAt = 1))
                val song = song(profile)
                db.libraryDao().upsertSongs(listOf(song))
                val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!,
                    Credentials("fixture", "fixture"))
                val store = DownloadStore(db.downloadDao(), db.libraryDao(), db.browseDao(),
                    { File(root, "$it") }, { client })
                store.enqueueSongs(profile, listOf(song), "single")
                store.enqueueSongs(profile, listOf(song), "playlist:p")
                db.downloadDao().claimNext(profile, 1)
                // Real PCM WAV fixture (not a recording or server-audio claim).
                val bytes = ByteArray(512 * 1024)
                java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()); putInt(bytes.size - 8); put("WAVEfmt ".toByteArray())
                    putInt(16); putShort(1); putShort(1); putInt(44_100); putInt(88_200)
                    putShort(2); putShort(16); put("data".toByteArray()); putInt(bytes.size - 44)
                }
                server.enqueue(MockResponse.Builder().addHeader("ETag", "\"stable\"")
                    .body(okio.Buffer().write(bytes)).build())
                var paused = false
                val first = store.transfer(profile, song.id, OpenSubsonicClient.streamingHttpClient(),
                    { paused }) { _, _ -> paused = true }
                assertEquals(DownloadStore.Outcome.Cancelled, first)
                val partial = db.downloadDao().download(profile, song.id)!!
                assertTrue(partial.bytes in 1 until bytes.size.toLong())
                assertEquals(2, db.downloadDao().originCount(profile, song.id))
                val offset = partial.bytes.toInt()
                server.takeRequest()
                server.enqueue(MockResponse.Builder().code(206)
                    .addHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                    .addHeader("ETag", "\"stable\"")
                    .body(okio.Buffer().write(bytes, offset, bytes.size - offset)).build())
                assertEquals(DownloadStore.Outcome.Completed, store.transfer(profile, song.id,
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
                val request = server.takeRequest()
                assertEquals("bytes=$offset-", request.headers["Range"])
                assertEquals("\"stable\"", request.headers["If-Range"])
                assertArrayEquals(bytes, store.completedFile(profile, song.id)!!.readBytes())
                assertEquals(bytes.size.toLong(), db.downloadDao().download(profile, song.id)!!.bytes)
                assertEquals(2, db.downloadDao().originCount(profile, song.id))
                db.profileDao().delete(profile)
                assertNull(db.downloadDao().download(profile, song.id))
                assertTrue(db.downloadDao().origins(profile, song.id).isEmpty())
            } finally { db.close(); root.deleteRecursively() }
        }
    }

    private fun song(profile: Long) = SongEntity(profile, "s", null, "album", null, "Transfer fixture",
        "Album", "Artist", null, null, 1, 1, null, null, null, 100, 128, "wav", "audio/wav",
        512 * 1024L, null, null, null, null)

    @Test fun emptyDownloadedPlaylistSubscriptionSurvivesAndDuplicateSnapshotIsIndependent(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "w08-groups-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val profile = db.profileDao().insert(ProfileEntity(name = "Groups fixture",
                serverUrl = "https://fixture.invalid", username = "fixture", createdAt = 1))
            val store = DownloadStore(db.downloadDao(), db.libraryDao(), db.browseDao(), { root }, { null })
            val playlist = PlaylistEntity(profile, "p", "Empty", "fixture", false, null, null, 0, 0, null, null, 1)
            db.browseDao().insertPlaylist(playlist)
            assertEquals(0, store.enqueuePlaylist(profile, "p"))
            assertEquals(listOf("playlist:p"), db.downloadDao().downloadedPlaylistOrigins(profile))
            assertEquals("[]", db.downloadDao().group(profile, "playlist:p")!!.songIds)
            val s = song(profile)
            db.libraryDao().upsertSongs(listOf(s))
            db.browseDao().replacePlaylist(playlist.copy(songCount = 2), listOf(
                PlaylistEntryEntity(profile, "p", 0, s.id, s.title, null, null, 6, null, null),
                PlaylistEntryEntity(profile, "p", 1, s.id, s.title, null, null, 6, null, null)))
            store.reconcilePlaylistDownload(profile, "p")
            assertEquals("[\"s\",\"s\"]", db.downloadDao().group(profile, "playlist:p")!!.songIds)
            assertEquals(1, db.downloadDao().downloadsOnce(profile).size)
            db.browseDao().deletePlaylist(profile, "p")
            store.reconcilePlaylistDownload(profile, "p")
            assertTrue(db.downloadDao().group(profile, "playlist:p")!!.serverMissing)
            assertEquals("[\"s\",\"s\"]", db.downloadDao().group(profile, "playlist:p")!!.songIds)
            db.profileDao().delete(profile)
            assertNull(db.downloadDao().group(profile, "playlist:p"))
        } finally { db.close(); root.deleteRecursively() }
    }

    @Test fun impossibleSpaceBudgetIsReportedBeforeAnyHttpRequest(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "w08-space-${UUID.randomUUID()}").apply { mkdirs() }
        MockWebServer().apply { start() }.use { server ->
            try {
                val id = db.profileDao().insert(ProfileEntity(name = "Space fixture", serverUrl = "https://fixture.invalid",
                    username = "fixture", createdAt = 1))
                val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!,
                    Credentials("fixture", "fixture"))
                val store = DownloadStore(db.downloadDao(), db.libraryDao(), db.browseDao(), { root }, { client })
                store.enqueueSongs(id, listOf(song(id).copy(size = Long.MAX_VALUE / 2)), "single")
                db.downloadDao().claimNext(id, 1)
                assertEquals(DownloadStore.Outcome.Space, store.transfer(id, "s",
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
                assertEquals("SPACE", db.downloadDao().download(id, "s")!!.lastError)
                assertEquals(0, server.requestCount)
                assertNull(db.browseDao().localAudio(id, "s"))
            } finally { db.close(); root.deleteRecursively() }
        }
    }

    @Test fun jsonErrorsAndCorruptAudioAreNeverAdvertisedAsDownloaded(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "w08-corrupt-${UUID.randomUUID()}").apply { mkdirs() }
        MockWebServer().apply { start() }.use { server ->
            try {
                val id = db.profileDao().insert(ProfileEntity(name = "Corruption fixture", serverUrl = "https://fixture.invalid",
                    username = "fixture", createdAt = 1))
                val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!,
                    Credentials("fixture", "fixture"))
                val store = DownloadStore(db.downloadDao(), db.libraryDao(), db.browseDao(), { root }, { client })
                store.enqueueSongs(id, listOf(song(id)), "single")
                db.downloadDao().claimNext(id, 1)
                server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json")
                    .body("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":70}}}""").build())
                assertEquals(DownloadStore.Outcome.Permanent(DownloadError.INVALID), store.transfer(id, "s",
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
                assertNull(store.completedFile(id, "s"))
                store.resume(id, "s"); db.downloadDao().claimNext(id, 1)
                server.enqueue(MockResponse.Builder().addHeader("Content-Type", "audio/wav")
                    .body(okio.Buffer().write(ByteArray(512 * 1024) { 42 })).build())
                assertEquals(DownloadStore.Outcome.Permanent(DownloadError.INVALID), store.transfer(id, "s",
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
                assertEquals(DownloadStatus.FAILED.name, db.downloadDao().download(id, "s")!!.status)
                assertNull(store.completedFile(id, "s"))
                assertNull(db.browseDao().localAudio(id, "s"))
                assertEquals(0L, db.downloadDao().download(id, "s")!!.bytes)
            } finally { db.close(); root.deleteRecursively() }
        }
    }

    @Test fun profileDeletedDuringTransferCannotResurrectCatalogOrPartial(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "w08-remove-${UUID.randomUUID()}").apply { mkdirs() }
        MockWebServer().apply { start() }.use { server ->
            try {
                val id = db.profileDao().insert(ProfileEntity(name = "Late writer fixture", serverUrl = "https://fixture.invalid",
                    username = "fixture", createdAt = 1))
                val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!,
                    Credentials("fixture", "fixture"))
                val store = DownloadStore(db.downloadDao(), db.libraryDao(), db.browseDao(), { root }, { client })
                store.enqueueSongs(id, listOf(song(id)), "single")
                db.downloadDao().claimNext(id, 1)
                server.enqueue(MockResponse.Builder().body(okio.Buffer().write(ByteArray(512 * 1024))).build())
                var deleted = false
                assertEquals(DownloadStore.Outcome.Cancelled, store.transfer(id, "s",
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ ->
                    if (!deleted) { db.profileDao().delete(id); deleted = true }
                })
                assertNull(db.downloadDao().download(id, "s"))
                assertNull(db.browseDao().localAudio(id, "s"))
                assertEquals(0L, DownloadStore.musicBytes(root))
            } finally { db.close(); root.deleteRecursively() }
        }
    }
}
