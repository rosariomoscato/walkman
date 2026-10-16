package com.walktape.app.playback

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.walktape.app.core.db.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** W09-T01: v11 adds quality columns without touching any existing data. */
@RunWith(AndroidJUnit4::class)
class Wave09StorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun audioPreferencesSurviveRepositoryRecreation(): Unit = runBlocking {
        val repository = com.walktape.app.Graph.settingsRepository
        val old = repository.settings.first()
        try {
            repository.setCrossfadeEnabled(true)
            repository.setCrossfadeDurationMs(4500)
            repository.setReplayGainMode(com.walktape.app.core.settings.ReplayGainMode.ALBUM)
            repository.setEqualizerEnabled(true)
            repository.setEqualizerBands("0:300,1:-200")
            repository.setBassBoostEnabled(true)
            repository.setBassBoostStrength(750)
            repository.setTapeEffectEnabled(true)
            val restored = com.walktape.app.core.settings.SettingsRepository(context).settings.first()
            assertTrue(restored.crossfadeEnabled)
            assertEquals(4500, restored.crossfadeDurationMs)
            assertEquals(com.walktape.app.core.settings.ReplayGainMode.ALBUM, restored.replayGainMode)
            assertTrue(restored.equalizerEnabled)
            assertEquals("0:300,1:-200", restored.equalizerBands)
            assertTrue(restored.bassBoostEnabled)
            assertEquals(750, restored.bassBoostStrength)
            assertTrue(restored.tapeEffectEnabled)
        } finally {
            repository.setCrossfadeEnabled(old.crossfadeEnabled)
            repository.setCrossfadeDurationMs(old.crossfadeDurationMs)
            repository.setReplayGainMode(old.replayGainMode)
            repository.setEqualizerEnabled(old.equalizerEnabled)
            repository.setEqualizerBands(old.equalizerBands)
            repository.setBassBoostEnabled(old.bassBoostEnabled)
            repository.setBassBoostStrength(old.bassBoostStrength)
            repository.setTapeEffectEnabled(old.tapeEffectEnabled)
        }
    }

    @Test fun migrateTenPreservesDataAndAddsQualityColumns(): Unit = runBlocking {
        val name = "wave09-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open("com.walktape.app.core.db.WalkTapeDatabase/10.json")
            .bufferedReader().use { JsonParser.parseReader(it).asJsonObject.getAsJsonObject("database") }
        val helper = object : SQLiteOpenHelper(context, name, null, 10) {
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
            execSQL("INSERT INTO profile_settings(profileId,wifiOnly,offlineOnly,skipUnavailable) VALUES(1,1,0,1)")
            execSQL("INSERT INTO download(profileId,songId,status,relativePath,bytes,totalBytes,title,duration,serverSize,serverMissing,createdAt,updatedAt,attempts) VALUES(1,'s','COMPLETED','music/a.mp3',1000,1000,'Track',100,1000,0,1,1,0)")
            execSQL("INSERT INTO download_origin(profileId,songId,origin,createdAt) VALUES(1,'s','single',1)")
        }
        helper.close()
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name)
            .addMigrations(*WalkTapeDatabase.MIGRATIONS).build()
        try {
            // existing rows survive untouched
            assertEquals("Keep mix", db.queueDao().load(1)!!.mixtapeName)
            assertEquals(12345L, db.queueDao().load(1)!!.positionMs)
            assertEquals("s", db.libraryDao().songsByIds(1, listOf("s")).single().id)
            val download = db.downloadDao().download(1, "s")!!
            assertEquals(DownloadStatus.COMPLETED.name, download.status)
            assertEquals(1, db.downloadDao().origins(1, "s").size)
            assertEquals("music/a.mp3", download.relativePath)
            // new quality columns apply honest defaults
            assertEquals(DownloadRepresentation.ORIGINAL.name, download.representation)
            assertNull(download.qualityKbps)
            val settings = db.downloadDao().settings(1)!!
            assertFalse(settings.mobileReduced)
            assertEquals(192, settings.reducedBitRate)
            assertFalse(settings.downloadReduced)
            // and they round-trip
            db.downloadDao().upsertSettings(settings.copy(mobileReduced = true, reducedBitRate = 128,
                downloadReduced = true))
            val updated = db.downloadDao().settings(1)!!
            assertTrue(updated.mobileReduced)
            assertEquals(128, updated.reducedBitRate)
            assertTrue(updated.downloadReduced)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
