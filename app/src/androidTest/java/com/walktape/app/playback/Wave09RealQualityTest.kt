package com.walktape.app.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.*
import com.walktape.app.core.downloads.DownloadChoice
import com.walktape.app.core.downloads.DownloadStore
import com.walktape.app.core.net.*
import kotlinx.coroutines.*
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Read-only real Navidrome MP3/FLAC -> original and Opus streams/downloads. No scrobbles. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Wave09RealQualityTest {
    @Test fun realMp3FlacReducedDownloadsDecodeAndKeepOriginalOrigins(): Unit = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue(env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        val client = OpenSubsonicClient(ServerAddress.parse(
            config.getValue("ND_URL").replace("localhost", "10.0.2.2"), true)!!,
            Credentials(config.getValue("ND_USER"), config.getValue("ND_PASS")))
        val songs = playbackCandidates(client)
        assertEquals("real MP3 and FLAC candidates required", 2, songs.size)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "wave09-real-${System.nanoTime()}").apply { mkdirs() }
        val http = OpenSubsonicClient.streamingHttpClient()
        try {
            val profile = db.profileDao().insert(ProfileEntity(name = "W09 read-only probe",
                serverUrl = "https://fixture.invalid", username = "fixture", createdAt = 1))
            val store = DownloadStore(db.downloadDao(), db.libraryDao(), db.browseDao(),
                { File(root, "$it") }, { client },
                choiceFor = { DownloadChoice(DownloadRepresentation.REDUCED, 96) })
            for (dto in songs) {
                // Actual original MIME + actual transcoding and ignored Range, not URL assertions.
                http.newCall(Request.Builder().url(client.streamUrl(dto.id, randomSalt()))
                    .header("Range", "bytes=0-4095").build()).execute().use { response ->
                    assertTrue(response.isSuccessful)
                    assertTrue(response.header("Content-Type").orEmpty().startsWith("audio/"))
                }
                http.newCall(Request.Builder().url(client.streamUrl(dto.id, randomSalt(), "opus", 96))
                    .header("Range", "bytes=1024-").build()).execute().use { response ->
                    assertEquals(200, response.code)
                    assertTrue(response.header("Content-Type").orEmpty().contains("ogg"))
                    assertNull(response.header("Content-Range"))
                }
                val song = dto.toSongEntity(profile)
                store.enqueueSongs(profile, listOf(song), "single")
                db.downloadDao().claimNext(profile, 1)
                assertEquals(DownloadStore.Outcome.Completed, store.transfer(profile, song.id,
                    http, { false }) { _, _ -> })
                val audio = requireNotNull(store.completedFile(profile, song.id))
                assertTrue(audio.extension == "ogg")
                assertTrue(audio.length() > 1024)
                val reducedSize = audio.length()
                val capture = CapturingAudioOutput()
                val player = withContext(Dispatchers.Main) { TestAudioEngine(context, capture).newPlayer() }
                try {
                    withContext(Dispatchers.Main) {
                        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(audio)))
                        player.prepare(); player.play()
                    }
                    withTimeout(30_000) {
                        while (capture.totalFrames < 24_000) {
                            withContext(Dispatchers.Main) { assertNull(player.playerError) }
                            delay(100)
                        }
                    }
                    assertTrue(capture.pcm().any { it.toInt() != 0 })
                } finally { withContext(Dispatchers.Main) { player.release() } }
                // A later quality preference change cannot overwrite this shared valid copy.
                val originalStore = DownloadStore(db.downloadDao(), db.libraryDao(), db.browseDao(),
                    { File(root, "$it") }, { client })
                originalStore.enqueueSongs(profile, listOf(song), "album:test")
                assertEquals(2, db.downloadDao().origins(profile, song.id).size)
                assertEquals(reducedSize, requireNotNull(originalStore.completedFile(profile, song.id)).length())
            }
        } finally { db.close(); root.deleteRecursively() }
    }
}
