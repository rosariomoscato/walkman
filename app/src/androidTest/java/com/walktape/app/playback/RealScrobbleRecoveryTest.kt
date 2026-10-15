package com.walktape.app.playback

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.*
import com.walktape.app.core.net.*
import kotlinx.coroutines.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Injected recovery event, REAL Navidrome ACK/playCount. Player provenance is tested separately. */
@RunWith(AndroidJUnit4::class)
class RealScrobbleRecoveryTest {
    @Test fun pendingFixtureRetriesAgainstRealServerThenAckPreventsResend(): Unit = runBlocking {
        val file = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue(file.exists())
        val config = file.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        val client = OpenSubsonicClient(ServerAddress.parse(config.getValue("ND_URL").replace("localhost","10.0.2.2"),true)!!,
            Credentials(config.getValue("ND_USER"),config.getValue("ND_PASS")))
        val album = (client.albumList("newest",1,0) as ApiResult.Success).value.first()
        val song = (client.album(album.id) as ApiResult.Success).value.songs.first()
        suspend fun playCount(): Int = (client.request("getSong",listOf("id" to song.id)) as ApiResult.Success).value.obj("song")!!.optInt("playCount")
        val before = playCount()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context,WalkTapeDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
        MockWebServer().apply { start() }.use { unavailable ->
            try {
                val profile = db.profileDao().insert(ProfileEntity(name="Recovery fixture",serverUrl="https://fixture.invalid",username="fixture",createdAt=1))
                val timestamp = System.currentTimeMillis()
                val event = ScrobbleEventEntity(profile,UUID.randomUUID().toString(),song.id,song.title,song.artist,song.album,
                    timestamp,240_000,timestamp)
                db.scrobbleDao().insert(event)
                unavailable.enqueue(MockResponse.Builder().code(503).build())
                val failedClient = OpenSubsonicClient(ServerAddress.parse(unavailable.url("/").toString(),true)!!,Credentials("fixture","fixture"))
                ScrobbleSender(db.scrobbleDao(),{ failedClient },scope).deliverPending()
                assertEquals(1,db.scrobbleDao().pending(profile,10).size)
                ScrobbleSender(db.scrobbleDao(),{ client },scope).deliverPending()
                assertTrue(db.scrobbleDao().pending(profile,10).isEmpty())
                assertEquals(before+1,playCount())
                ScrobbleSender(db.scrobbleDao(),{ client },scope).deliverPending()
                assertEquals("A recreated sender must not resend a server-acknowledged event",before+1,playCount())
            } finally { scope.cancel(); db.close() }
        }
    }
}
