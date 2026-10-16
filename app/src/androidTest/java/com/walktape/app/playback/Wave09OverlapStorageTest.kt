package com.walktape.app.playback

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.walktape.app.core.db.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Wave09OverlapStorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun migrateElevenKeepsQualityOriginsQueueAndAcknowledgedPasses(): Unit = runBlocking {
        val name = "overlap-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open("com.walktape.app.core.db.WalkTapeDatabase/11.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        val helper = object : SQLiteOpenHelper(context, name, null, 11) {
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
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        helper.writableDatabase.apply {
            execSQL("INSERT INTO profile(id,name,serverUrl,username,isActive,createdAt,credentialId,encryptedPassword,allowLocalHttp) VALUES(1,'fixture','https://fixture.invalid','fixture',1,1,'key','cipher',0)")
            execSQL("INSERT INTO profile_settings(profileId,mobileReduced,reducedBitRate,downloadReduced) VALUES(1,1,96,1)")
            execSQL("INSERT INTO queue_state VALUES(1,'queue/a',1234,2,0,1,'Mixtape')")
            execSQL("INSERT INTO queue_entry VALUES(1,0,0,'queue/a','s','Track',NULL,NULL,10000,'Origin')")
            execSQL("INSERT INTO listening_state VALUES(1,'queue/a','ack-pass','s','Track',NULL,NULL,1,5000,1)")
            execSQL("INSERT INTO scrobble_event VALUES(1,'ack-pass','s','Track',NULL,NULL,1,5000,1,2)")
            execSQL("INSERT INTO download(profileId,songId,status,relativePath,bytes,totalBytes,title,duration,serverSize,createdAt,updatedAt,attempts,representation,qualityKbps) VALUES(1,'s','COMPLETED','music/s.ogg',100,100,'Track',10,1000,1,1,0,'REDUCED',96)")
            execSQL("INSERT INTO download_origin VALUES(1,'s','single',1)")
        }
        helper.close()
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name)
            .addMigrations(*WalkTapeDatabase.MIGRATIONS).build()
        try {
            assertEquals(96, db.downloadDao().settings(1)!!.reducedBitRate)
            assertEquals("Mixtape", db.queueDao().load(1)!!.mixtapeName)
            assertEquals(1234L, db.queueDao().load(1)!!.positionMs)
            assertTrue(db.scrobbleDao().listeningState(1)!!.emitted)
            assertTrue(db.scrobbleDao().pending(1, 10).isEmpty())
            assertEquals(96, db.downloadDao().download(1, "s")!!.qualityKbps)
            assertEquals(1, db.downloadDao().origins(1, "s").size)
            assertNull(db.scrobbleDao().overlapState(1))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun promotionReceiptAndAckSurviveReopenAndDoNotDuplicate(): Unit = runBlocking {
        val name = "overlap-receipt-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name).build()
        var db = open()
        try {
            val id = db.profileDao().insert(ProfileEntity(name = "fixture", serverUrl = "https://fixture.invalid",
                username = "fixture", createdAt = 1))
            val outgoing = ListeningStateEntity(id, "queue/a", "out", "same", "A", null, null, 1, 7000, true)
            val incoming = ListeningStateEntity(id, "queue/b", "in", "same", "B", null, null, 2, 3000, true)
            val event = ScrobbleEventEntity(id, "in", "same", "B", null, null, 2, 3000, 3)
            db.scrobbleDao().record(outgoing, null)
            db.scrobbleDao().recordOverlap(incoming, event)
            db.scrobbleDao().markSent(id, listOf("in"), 4)
            db.scrobbleDao().promoteOverlap(incoming, Gson().toJson(outgoing))
            // Subsequent primary writes must preserve the receipt and its outgoing ACK.
            db.scrobbleDao().record(incoming.copy(listenedMs = 4000), null)
            db.close(); db = open()
            val receipt = db.scrobbleDao().overlapState(id)!!
            assertTrue(receipt.promoted)
            assertEquals("in", receipt.eventId)
            assertEquals(4000L, receipt.listenedMs)
            assertTrue(receipt.emitted)
            val old = Gson().fromJson(receipt.outgoingSnapshot, ListeningStateEntity::class.java)
            assertEquals("out", old.eventId)
            assertTrue(old.emitted)
            // Replay of a local confirmation cannot insert a second event or lose ACK.
            db.scrobbleDao().record(receipt.asPrimary(), event)
            assertEquals(1, db.scrobbleDao().count(id))
            assertTrue(db.scrobbleDao().pending(id, 10).isEmpty())
            db.profileDao().delete(id)
            assertNull(db.scrobbleDao().overlapState(id))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
