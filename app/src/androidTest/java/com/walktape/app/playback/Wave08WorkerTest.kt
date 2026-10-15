package com.walktape.app.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.walktape.app.Graph
import com.walktape.app.core.db.*
import com.walktape.app.core.downloads.DownloadEngine
import com.walktape.app.core.net.ApiResult
import kotlinx.coroutines.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Real WorkManager/JobScheduler and protected profile credentials; HTTP/audio are PCM fixture. */
@RunWith(AndroidJUnit4::class)
class Wave08WorkerTest {
    @Test fun pausedQueueResumesThroughPersistentWorkerWithCredentialsReadFromVault(): Unit = runBlocking {
        var profile: Long? = null
        MockWebServer().apply { start() }.use { server ->
            try {
                server.enqueue(MockResponse.Builder().body(
                    """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build())
                val added = Graph.profiles.addVerified("W08 worker fixture ${System.nanoTime()}",
                    server.url("/").toString(), "fixture", "fixture-only", true) as ApiResult.Success
                val id = added.value; profile = id
                val bytes = ByteArray(44 + 88_200)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray()); putInt(bytes.size - 8); put("WAVEfmt ".toByteArray())
                    putInt(16); putShort(1); putShort(1); putInt(44_100); putInt(88_200)
                    putShort(2); putShort(16); put("data".toByteArray()); putInt(bytes.size - 44)
                }
                val song = SongEntity(id, "worker-a", null, null, null, "Worker PCM fixture", null,
                    null, null, null, 1, 1, null, null, null, 1, 705, "wav", "audio/wav",
                    bytes.size.toLong(), null, null, null, null)
                Graph.downloadStore.enqueueSongs(id, listOf(song), "single")
                Graph.downloadStore.enqueueSongs(id, listOf(song), "album:fixture")
                Graph.downloadEngine.pauseWork(id)
                assertEquals(DownloadStatus.PAUSED.name, Graph.database.downloadDao().download(id, song.id)!!.status)
                assertNull(Graph.downloadStore.completedFile(id, song.id))
                Graph.database.downloadDao().upsertSettings(ProfileSettingsEntity(id, wifiOnly = false))
                server.enqueue(MockResponse.Builder().addHeader("Content-Type", "audio/wav")
                    .body(okio.Buffer().write(bytes)).build())
                Graph.downloadStore.resume(id, song.id)
                Graph.downloadEngine.schedule(id, replace = true)
                withTimeout(90_000) {
                    while (Graph.database.downloadDao().download(id, song.id)?.status != DownloadStatus.COMPLETED.name)
                        delay(250)
                }
                assertArrayEquals(bytes, Graph.downloadStore.completedFile(id, song.id)!!.readBytes())
                assertEquals(2, Graph.database.downloadDao().originCount(id, song.id))
                val info = WorkManager.getInstance(Graph.app())
                    .getWorkInfosForUniqueWork(DownloadEngine.uniqueName(id)).get(10, TimeUnit.SECONDS)
                assertTrue(info.isNotEmpty())
                assertEquals(id, info.first().tags.firstOrNull { it == DownloadEngine.profileTag(id) }
                    ?.substringAfterLast('-')?.toLong())
            } finally {
                profile?.let {
                    Graph.downloadEngine.cancelProfile(it)
                    Graph.profiles.remove(it)
                }
            }
        }
    }
}
