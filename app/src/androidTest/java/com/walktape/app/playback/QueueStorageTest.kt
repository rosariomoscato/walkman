package com.walktape.app.playback

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.db.WalkTapeDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class QueueStorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun sample(id: Long, position: Long = 12_345) = SavedQueue(id, listOf(
        SavedQueueItem("queue/a", "same-song", "First copy", "Artist", "Album", 90_000, "Fixture playlist"),
        SavedQueueItem("queue/b", "same-song", "Second copy", "Artist", "Album", 90_000, "Fixture playlist")),
        listOf("queue/b", "queue/a"), "queue/b", position, 2, true, 100)

    private suspend fun profile(db: WalkTapeDatabase, name: String): Long = db.profileDao().insert(
        ProfileEntity(name = name, serverUrl = "https://example.invalid", username = "fixture", createdAt = 1))

    @Test fun migrateV5KeepsExistingQueueAndAddsNullableOrigin(): Unit = runBlocking {
        val name = "queue-origin-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open("com.walktape.app.core.db.WalkTapeDatabase/5.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        val helper = object : SQLiteOpenHelper(context, name, null, 5) {
            override fun onCreate(db: SQLiteDatabase) {
                schema.getAsJsonArray("entities").forEach { entry ->
                    val entity = entry.asJsonObject
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
            execSQL("INSERT INTO profile(id,name,serverUrl,username,isActive,createdAt,credentialId,encryptedPassword,allowLocalHttp) VALUES(1,'Preserved','https://example.invalid','fixture',1,1,'key','encrypted-fixture',0)")
            execSQL("INSERT INTO queue_state VALUES(1,'queue/a',12345,2,0,100)")
            execSQL("INSERT INTO queue_entry VALUES(1,0,0,'queue/a','song','Preserved title','Artist','Album',90000)")
        }
        helper.close()
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name)
            .addMigrations(*WalkTapeDatabase.MIGRATIONS).build()
        try {
            val queue = db.queueDao().load(1)!!
            assertEquals(12345L, queue.positionMs)
            assertEquals("Preserved title", queue.items.single().title)
            assertNull(queue.items.single().originName)
            db.queueDao().replace(sample(1))
            assertEquals(sample(1), db.queueDao().load(1))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migrateV4PreservesExistingProfileLibraryAndPlaylist(): Unit = runBlocking {
        val name = "queue-migration-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open("com.walktape.app.core.db.WalkTapeDatabase/4.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        val helper = object : SQLiteOpenHelper(context, name, null, 4) {
            override fun onCreate(db: SQLiteDatabase) {
                schema.getAsJsonArray("entities").forEach { entry ->
                    val entity = entry.asJsonObject
                    val table = entity.get("tableName").asString
                    db.execSQL(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                    entity.getAsJsonArray("indices")?.forEach { index -> db.execSQL(
                        index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table)) }
                }
                schema.getAsJsonArray("setupQueries").forEach { db.execSQL(it.asString) }
            }
            override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}
        }
        helper.writableDatabase.execSQL("INSERT INTO profile(id,name,serverUrl,username,isActive,createdAt,credentialId,encryptedPassword,allowLocalHttp) VALUES(1,'Preserved','https://example.invalid','fixture',1,1,'key','encrypted-fixture',0)")
        helper.writableDatabase.execSQL("INSERT INTO album(profileId,id,name,songCount,duration,isCompilation) VALUES(1,'album','Preserved album',2,90,0)")
        helper.writableDatabase.execSQL("INSERT INTO playlist(profileId,id,name,isPublic,songCount,duration,fetchedAt) VALUES(1,'playlist','Preserved playlist',0,0,0,1)")
        helper.close()
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name).addMigrations(*WalkTapeDatabase.MIGRATIONS).build()
        try {
            assertEquals("encrypted-fixture", db.profileDao().get(1)!!.encryptedPassword)
            assertTrue(db.profileDao().get(1)!!.isActive)
            assertEquals("Preserved album", db.libraryDao().album(1, "album")!!.name)
            assertEquals(1, db.browseDao().playlistCount(1))
            assertNull(db.queueDao().load(1))
            assertTrue(db.queueDao().replace(sample(1)))
            assertEquals(sample(1), db.queueDao().load(1))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun diskRoundTripIsolatedProfilesDuplicatesAndEmptyQueue(): Unit = runBlocking {
        val name = "queue-store-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name).addMigrations(*WalkTapeDatabase.MIGRATIONS).build()
        var db = open()
        try {
            val a = profile(db, "A"); val b = profile(db, "B")
            db.queueDao().replace(sample(a))
            db.queueDao().replace(sample(b, 44_444))
            db.close(); db = open()
            assertEquals(sample(a), db.queueDao().load(a))
            assertEquals(sample(b, 44_444), db.queueDao().load(b))
            val empty = SavedQueue(a, emptyList(), emptyList(), null, 0, 0, false, 200)
            db.queueDao().replace(empty)
            assertEquals(empty, db.queueDao().load(a))
            assertEquals(sample(b, 44_444), db.queueDao().load(b))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun writerOrderingDeletionCascadeAndLateWritesCannotResurrectProfile(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val store = QueueStore(db.queueDao())
        try {
            val a = profile(db, "A"); val b = profile(db, "B")
            repeat(25) { store.enqueue(sample(a, it * 1000L)) }
            store.enqueue(sample(b, 42_000))
            assertEquals(24_000L, store.load(a)!!.positionMs) // load is a barrier.
            assertEquals(42_000L, store.load(b)!!.positionMs)
            db.profileDao().delete(a)
            assertTrue(db.queueDao().entries(a).isEmpty())
            assertNull(db.queueDao().state(a))
            store.enqueue(sample(a, 88_000))
            store.flush()
            assertNull(store.load(a))
            assertEquals(42_000L, store.load(b)!!.positionMs)
        } finally { store.close(); db.close() }
    }

    @Test fun invalidSnapshotDoesNotOverwriteLastValidStateAndReportsFailure(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val store = QueueStore(db.queueDao())
        try {
            val a = profile(db, "A")
            store.enqueue(sample(a)); store.flush()
            store.enqueue(sample(a).copy(currentId = "queue/missing")); store.flush()
            assertTrue(a in store.failedProfiles.value)
            assertEquals(sample(a), db.queueDao().load(a))
            store.enqueue(sample(a, 55_000)); store.flush()
            assertFalse(a in store.failedProfiles.value)
            assertEquals(55_000L, store.load(a)!!.positionMs)
        } finally { store.close(); db.close() }
    }
}
