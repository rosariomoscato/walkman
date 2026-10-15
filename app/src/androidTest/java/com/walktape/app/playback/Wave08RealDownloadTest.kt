package com.walktape.app.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.*
import com.walktape.app.core.downloads.DownloadStore
import com.walktape.app.core.net.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Real original Navidrome audio; local-only DataSource read, not a hardware playback claim. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Wave08RealDownloadTest {
    @Test fun downloadsOriginalAndLocalSourceWorksWithNetworkUpstreamBlocked(): Unit = runBlocking {
        val env = File("/data/local/tmp/walktape-test-server.env")
        assumeTrue(env.exists())
        val config = env.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        val client = OpenSubsonicClient(ServerAddress.parse(
            config.getValue("ND_URL").replace("localhost", "10.0.2.2"), true)!!,
            Credentials(config.getValue("ND_USER"), config.getValue("ND_PASS")))
        val album = (client.albumList("newest", 1, 0) as ApiResult.Success).value.first()
        val original = (client.album(album.id) as ApiResult.Success).value.songs.first()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "wave08-real-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val profile = db.profileDao().insert(ProfileEntity(name = "Read-only real download probe",
                serverUrl = "https://fixture.invalid", username = "fixture", createdAt = 1))
            val song = SongEntity(profile, original.id, null, original.albumId, original.artistId,
                original.title, original.album, original.artist, original.albumArtist, original.displayArtist,
                original.track, original.discNumber, original.year, original.genre, original.coverArt,
                original.duration, original.bitRate, original.suffix, original.contentType, original.size,
                null, original.created, original.starred, original.type)
            val store = DownloadStore(db.downloadDao(), db.libraryDao(), db.browseDao(),
                { File(root, "$it") }, { client })
            store.enqueueSongs(profile, listOf(song), "single")
            db.downloadDao().claimNext(profile, 1)
            assertEquals(DownloadStore.Outcome.Completed, store.transfer(profile, song.id,
                OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
            val audio = store.completedFile(profile, song.id)!!
            assertTrue(audio.length() > 0)
            if (original.size > 0) assertEquals(original.size, audio.length())
            val upstream = SubsonicStreamDataSourceFactory(OpenSubsonicClient.streamingHttpClient(),
                "WalkTape-test", offlineOnly = { true }) { StreamProfile(profile, client) }.createDataSource()
            val source = LocalFirstDataSource({ profile }, { p, id ->
                audio.takeIf { p == profile && id == song.id }
            }, upstream)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                source.open(DataSpec(Uri.parse(SubsonicStream.uri(profile, song.id))))
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            } finally { source.close() }
            val expected = MessageDigest.getInstance("SHA-256")
            audio.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    expected.update(buffer, 0, count)
                }
            }
            assertArrayEquals(expected.digest(), digest.digest())
            // The metadata/download is independent from a refreshed/removed library row.
            db.libraryDao().deleteSongsFor(profile)
            assertNotNull(store.completedFile(profile, song.id))
        } finally { db.close(); root.deleteRecursively() }
    }
}
