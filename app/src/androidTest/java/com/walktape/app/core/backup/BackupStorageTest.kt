package com.walktape.app.core.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.db.DownloadGroupEntity
import com.walktape.app.core.db.DownloadOriginEntity
import com.walktape.app.core.db.DownloadStatus
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.db.ProfileSettingsEntity
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.settings.SettingsRepository
import com.walktape.app.playback.SavedQueue
import com.walktape.app.playback.SavedQueueItem
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * W11-T03/T05: backup round trip in isolated storage (fresh Room files under the app
 * sandbox, never the user's database), queue semantics (duplicates, traversal, mixtape),
 * merge with existing profiles, honest rejections of invalid files and zero network
 * traffic during import.
 */
@RunWith(AndroidJUnit4::class)
class BackupStorageTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val gson = Gson()

    private fun newDb(): WalkTapeDatabase =
        Room.databaseBuilder(context, WalkTapeDatabase::class.java,
            "w11-backup-${UUID.randomUUID()}.db").build()

    private fun manager(db: WalkTapeDatabase, hasMedia: () -> Boolean = { false }) =
        BackupManager(db, SettingsRepository(context),
            queueLoader = { db.queueDao().load(it) },
            activeProfileHasMedia = hasMedia,
            appVersion = "test")

    private fun download(profileId: Long, songId: String, status: DownloadStatus) =
        DownloadEntity(profileId = profileId, songId = songId, status = status.name,
            relativePath = if (status == DownloadStatus.COMPLETED) "music/$songId" else null,
            bytes = if (status == DownloadStatus.COMPLETED) 100 else 0,
            totalBytes = if (status == DownloadStatus.COMPLETED) 100 else -1,
            title = "Track $songId", artist = "Artist", album = "Album", albumId = "al1",
            coverArt = "cov-$songId", duration = 100, suffix = "mp3",
            contentType = "audio/mpeg", serverSize = 100, createdAt = 1,
            completedAt = if (status == DownloadStatus.COMPLETED) 2 else null,
            updatedAt = 2, lastError = null, attempts = 0)

    private suspend fun queueFor(profileId: Long): SavedQueue {
        val items = listOf(
            SavedQueueItem("queue/a", "s1", "Track s1", "Artist", "Album", 100_000L, "Road"),
            SavedQueueItem("queue/b", "s2", "Track s2", "Artist", "Album", 110_000L, null),
            SavedQueueItem("queue/c", "s1", "Track s1", "Artist", "Album", 100_000L, null),
        )
        return SavedQueue(profileId, items,
            traversal = listOf("queue/c", "queue/a", "queue/b"),
            currentId = "queue/c", positionMs = 12_345L, repeatMode = 2, shuffle = true,
            updatedAt = 7, mixtapeName = "Mix sera")
    }

    private suspend fun seedProfile(db: WalkTapeDatabase, name: String, url: String,
                                    username: String, active: Boolean): Long {
        val id = db.profileDao().insert(ProfileEntity(name = name, serverUrl = url,
            username = username, isActive = active, createdAt = 1,
            credentialId = "key-$name", encryptedPassword = "ENCRYPTED-$name", allowLocalHttp = true))
        db.downloadDao().upsertSettings(ProfileSettingsEntity(profileId = id))
        db.downloadDao().upsertDownload(download(id, "s1", DownloadStatus.COMPLETED))
        db.downloadDao().upsertDownload(download(id, "s2", DownloadStatus.COMPLETED))
        db.downloadDao().upsertDownload(download(id, "s3", DownloadStatus.PAUSED))
        db.downloadDao().insertOrigins(listOf(
            DownloadOriginEntity(id, "s1", "single", 1),
            DownloadOriginEntity(id, "s2", "playlist:9", 1)))
        db.downloadDao().upsertGroup(DownloadGroupEntity(id, "playlist:9", "Road",
            gson.toJson(listOf("s2", "s1", "s2")), 5, false))
        db.queueDao().replace(queueFor(id))
        return id
    }

    @Test
    fun roundTripTransfersTwoProfilesWithoutCredentialsOrPausedRows(): Unit = runBlocking {
        val source = newDb()
        val target = newDb()
        try {
            seedProfile(source, "Home", "http://10.0.2.2:4533", "walktape-test", active = true)
            seedProfile(source, "Casa", "http://192.168.1.4:4533", "other", active = false)
            val json = BackupCodec.write(manager(source).build(includePreferences = false))

            // No credential material may ever travel with the backup.
            assertFalse(json.contains("ENCRYPTED-"))
            assertFalse(json.contains("key-Home"))
            assertFalse(json.contains("encryptedPassword"))
            assertFalse(json.contains("credentialId"))

            val parsed = BackupCodec.parse(json)
            assertTrue(parsed is BackupCodec.Parsed.Valid)
            val backup = (parsed as BackupCodec.Parsed.Valid).backup
            assertEquals(2, backup.profiles.size)
            assertEquals(4, backup.profiles.sumOf { it.downloads.size })

            val summary = manager(target).import(backup, applyPreferences = false)
            assertTrue(summary.failed.isEmpty())
            assertEquals(2, summary.results.size)
            assertTrue(summary.results.all { it.created && it.queueApplied })
            assertTrue(summary.results.all { it.downloadsAdded == 2 && it.downloadsKept == 0 })

            val restored = target.profileDao().all()
            assertEquals(2, restored.size)
            restored.forEach { profile ->
                assertEquals("", profile.credentialId)
                assertEquals("", profile.encryptedPassword)
                assertFalse(profile.isActive)
                val queue = target.queueDao().load(profile.id)!!
                assertEquals(3, queue.items.size)
                assertEquals(listOf("queue/c", "queue/a", "queue/b"), queue.traversal)
                assertEquals("queue/c", queue.currentId)
                assertEquals(12_345L, queue.positionMs)
                assertEquals(2, queue.repeatMode)
                assertTrue(queue.shuffle)
                assertEquals("Mix sera", queue.mixtapeName)
                assertEquals(2, queue.items.count { it.songId == "s1" }) // duplicate occurrences kept
                // Only completed downloads travel; imported rows wait as paused re-downloads.
                val rows = target.downloadDao().downloadsOnce(profile.id)
                assertEquals(2, rows.size)
                assertTrue(rows.all { it.status == DownloadStatus.PAUSED.name })
                assertEquals("single", target.downloadDao().allOrigins(profile.id)
                    .first { it.songId == "s1" }.origin)
                val group = target.downloadDao().group(profile.id, "playlist:9")!!
                assertEquals(listOf("s2", "s1", "s2"),
                    gson.fromJson(group.songIds, Array<String>::class.java).toList())
            }
            assertNull(target.profileDao().active())
        } finally {
            source.close(); target.close()
        }
    }

    @Test
    fun importMergesIntoMatchingProfileKeepsFilesAndRenamesCollisions(): Unit = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val source = newDb()
            val target = newDb()
            try {
                seedProfile(source, "Home", server.url("/").toString().trimEnd('/'),
                    "walktape-test", active = true)
                // A second backup profile with a different account: no url+username match.
                seedProfile(source, "Second", "http://10.0.0.9:4533", "altro", active = false)
                val backup = manager(source).build(includePreferences = false)

                // Existing device: same account (url+username) with its own credentials
                // and one completed file; plus a different profile already called "Second".
                val matchedId = target.profileDao().insert(ProfileEntity(name = "Existing",
                    serverUrl = server.url("/").toString().trimEnd('/'),
                    username = "walktape-test", isActive = true, createdAt = 1,
                    credentialId = "existing-key", encryptedPassword = "ENCRYPTED-existing"))
                target.downloadDao().upsertDownload(download(matchedId, "s1", DownloadStatus.COMPLETED))
                target.queueDao().replace(queueFor(matchedId).copy(mixtapeName = "Local queue"))
                target.profileDao().insert(ProfileEntity(name = "Second",
                    serverUrl = "http://elsewhere:4533", username = "someone",
                    isActive = false, createdAt = 1, credentialId = "k", encryptedPassword = "e"))
                val localQueueBefore = target.queueDao().load(matchedId)!!

                val summary = manager(target).import(backup, applyPreferences = false)
                assertTrue(summary.failed.isEmpty())
                assertEquals(2, summary.results.size)
                val merged = summary.results.first { !it.created }
                assertEquals("Existing", merged.displayName)
                assertEquals(1, merged.downloadsKept)
                assertEquals(1, merged.downloadsAdded)
                assertTrue(merged.queueApplied)

                val matched = target.profileDao().get(matchedId)!!
                assertEquals("existing-key", matched.credentialId)
                assertEquals("ENCRYPTED-existing", matched.encryptedPassword)
                // The existing completed file is never degraded to a pending re-download.
                assertEquals(DownloadStatus.COMPLETED.name,
                    target.downloadDao().download(matchedId, "s1")!!.status)
                assertEquals(DownloadStatus.PAUSED.name,
                    target.downloadDao().download(matchedId, "s2")!!.status)
                // The backup queue replaced the local one for the matching profile.
                val localQueueAfter = target.queueDao().load(matchedId)!!
                assertEquals("Mix sera", localQueueAfter.mixtapeName)
                assertEquals(localQueueBefore.items.map { it.songId },
                    localQueueAfter.items.map { it.songId })

                // Name collision with a different account gets an honest suffix.
                val renamed = summary.results.first { it.created }
                assertEquals("Second (2)", renamed.displayName)

                // Import never talks to the profile's server.
                assertEquals(0, server.requestCount)
            } finally {
                source.close(); target.close()
            }
        }
    }

    @Test
    fun activeProfileWithLoadedMediaKeepsItsQueue(): Unit = runBlocking {
        val source = newDb()
        val target = newDb()
        try {
            seedProfile(source, "Home", "http://10.0.2.2:4533", "walktape-test", active = true)
            val backup = manager(source).build(includePreferences = false)
            val id = target.profileDao().insert(ProfileEntity(name = "Home",
                serverUrl = "http://10.0.2.2:4533", username = "walktape-test",
                isActive = true, createdAt = 1, credentialId = "k", encryptedPassword = "e"))
            target.queueDao().replace(queueFor(id).copy(mixtapeName = "Playing now"))

            val summary = manager(target, hasMedia = { true }).import(backup, applyPreferences = false)
            val result = summary.results.single()
            assertFalse(result.queueApplied)
            assertTrue(result.queueSkippedActive)
            assertEquals("Playing now", target.queueDao().load(id)!!.mixtapeName)
        } finally {
            source.close(); target.close()
        }
    }

    @Test
    fun truncatedFutureAndInvalidFilesLeaveExistingDataIntact(): Unit = runBlocking {
        val source = newDb()
        try {
            seedProfile(source, "Home", "http://10.0.2.2:4533", "walktape-test", active = true)
            val json = BackupCodec.write(manager(source).build(includePreferences = false))

            val truncated = BackupCodec.parse(json.substring(0, json.length / 2))
            assertTrue(truncated is BackupCodec.Parsed.Invalid)

            val future = BackupCodec.parse(json.replaceFirst("\"version\":1", "\"version\":2"))
            assertTrue((future as BackupCodec.Parsed.Invalid).reason == BackupCodec.Reason.UNSUPPORTED_VERSION)

            val badQueue = BackupCodec.parse(json
                .replace("\"traversal\":[\"queue/c\",\"queue/a\",\"queue/b\"]",
                    "\"traversal\":[\"queue/c\",\"queue/a\",\"queue/zz\"]"))
            assertTrue((badQueue as BackupCodec.Parsed.Invalid).reason == BackupCodec.Reason.INVALID_QUEUE)

            // None of the rejected files may reach the importer: the existing queue stays.
            val before = source.queueDao().load(source.profileDao().active()!!.id)!!
            listOf(truncated, future, badQueue).forEach { parsed ->
                if (parsed is BackupCodec.Parsed.Valid) {
                    throw AssertionError("invalid file parsed as valid")
                }
            }
            val after = source.queueDao().load(source.profileDao().active()!!.id)!!
            assertEquals(before, after)
        } finally {
            source.close()
        }
    }
}
