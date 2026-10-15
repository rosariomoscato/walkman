package com.walktape.app.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.walktape.app.Graph
import com.walktape.app.core.db.*
import com.walktape.app.core.downloads.DownloadEngine
import com.walktape.app.core.downloads.DownloadWorker
import com.walktape.app.core.net.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Explicit two-process protocol. Marker stores identifiers/size only, never credentials. */
@RunWith(AndroidJUnit4::class)
class Wave08RestartTest {
    private val marker get() = File(Graph.app().cacheDir, "w08-restart.ids")

    @Test fun seedPersistentRealDownload(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wave08Restart") == "seed")
        check(!marker.exists()) { "An existing restart probe must be verified/cleaned first" }
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue(env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        val url = config.getValue("ND_URL").replace("localhost", "10.0.2.2")
        val result = Graph.profiles.addVerified("W08 process restart probe ${System.nanoTime()}", url,
            config.getValue("ND_USER"), config.getValue("ND_PASS"), true) as ApiResult.Success
        val profile = result.value
        try {
            val client = Graph.profiles.clientFor(profile)!!
            val album = (client.albumList("newest", 1, 0) as ApiResult.Success).value.first()
            val s = (client.album(album.id) as ApiResult.Success).value.songs.first()
            val song = SongEntity(profile, s.id, null, s.albumId, s.artistId, s.title, s.album, s.artist,
                s.albumArtist, s.displayArtist, s.track, s.discNumber, s.year, s.genre, s.coverArt,
                s.duration, s.bitRate, s.suffix, s.contentType, s.size, null, s.created, s.starred, s.type)
            Graph.database.downloadDao().upsertSettings(ProfileSettingsEntity(profile, wifiOnly = false))
            Graph.downloadStore.enqueueSongs(profile, listOf(song), "single")
            marker.writeText("$profile\n${song.id}\n${s.size}\n")
            val work = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadEngine.KEY_PROFILE to profile))
                .setInitialDelay(30, TimeUnit.SECONDS)
                .addTag(DownloadEngine.profileTag(profile))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(Graph.app()).enqueueUniqueWork(DownloadEngine.uniqueName(profile),
                ExistingWorkPolicy.REPLACE, work).result.get(10, TimeUnit.SECONDS)
            assertEquals(DownloadStatus.QUEUED.name, Graph.database.downloadDao().download(profile, song.id)!!.status)
        } catch (error: Throwable) {
            Graph.downloadEngine.cancelProfile(profile); Graph.profiles.remove(profile); marker.delete()
            throw error
        }
    }

    @Test fun verifyNewProcessResumesRealDownloadAndCleanup(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wave08Restart") == "verify")
        check(marker.exists())
        val lines = marker.readLines()
        val profile = lines[0].toLong(); val songId = lines[1]; val bytes = lines[2].toLong()
        try {
            withTimeout(120_000) {
                while (Graph.database.downloadDao().download(profile, songId)?.status != DownloadStatus.COMPLETED.name)
                    delay(250)
            }
            val file = Graph.downloadStore.completedFile(profile, songId)!!
            assertTrue(file.length() > 0)
            if (bytes > 0) assertEquals(bytes, file.length())
            assertEquals(1, Graph.database.downloadDao().originCount(profile, songId))
        } finally {
            Graph.downloadEngine.cancelProfile(profile)
            Graph.profiles.remove(profile)
            marker.delete()
        }
    }
}
