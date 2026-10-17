package com.walktape.app.core.diagnostics

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.db.DownloadStatus
import com.walktape.app.core.db.PendingOpEntity
import com.walktape.app.core.db.PendingOpState
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** W11-T02: the report is allowlist-built and carries no credential material. */
@RunWith(AndroidJUnit4::class)
class DiagnosticsReportTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun reportUsesAllowlistedFieldsAndOptionalConnections(): Unit = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        try {
            val id = db.profileDao().insert(ProfileEntity(name = "Home",
                serverUrl = "http://10.0.2.2:4533", username = "walktape-test",
                isActive = true, createdAt = 1,
                credentialId = "cred-key-987", encryptedPassword = "ENCRYPTEDSECRETVALUE",))
            db.downloadDao().upsertDownload(DownloadEntity(profileId = id, songId = "s1",
                status = DownloadStatus.FAILED.name, relativePath = null, bytes = 0,
                totalBytes = -1, title = "Track", artist = null, album = null, albumId = null,
                coverArt = null, duration = 100, suffix = null, contentType = null,
                serverSize = 0, createdAt = 1, completedAt = null, updatedAt = 1,
                lastError = "NETWORK", attempts = 1))
            db.downloadDao().upsertOp(PendingOpEntity(profileId = id, opId = "op1",
                kind = "STAR", payload = "{}", baselineChanged = null, baselineSignature = null,
                createdAt = 1, state = PendingOpState.CONFLICT.name, attempts = 1,
                lastError = null, updatedAt = 1))

            val collector = DiagnosticsCollector(context, DiagnosticsCollector.Sources(
                profiles = { db.profileDao().all() },
                libraryState = { db.libraryDao().state(it) },
                downloadCounts = { profileId ->
                    db.downloadDao().downloadsOnce(profileId).groupingBy { it.status }.eachCount()
                },
                downloadErrors = { profileId ->
                    db.downloadDao().downloadsOnce(profileId)
                        .filter { it.lastError != null }
                        .groupingBy { it.lastError!! }.eachCount()
                },
                pendingOpCounts = { profileId ->
                    db.downloadDao().opsOnce(profileId).groupingBy { it.state }.eachCount()
                },
                syncProgress = { null },
                playback = { null },
                timer = { null },
                settings = { AppSettings() },
                offlineOnly = { false },
                profileStorage = { mapOf("music" to 10L, "audioCache" to 20L, "covers" to 30L) },
            ))

            val sections = collector.buildSections()
            // The connections section is the only opt-in private data surface.
            assertFalse(collector.defaultIncluded.contains(DiagnosticsCollector.SectionId.CONNECTIONS))

            val full = collector.render(sections,
                collector.defaultIncluded + DiagnosticsCollector.SectionId.CONNECTIONS)
            assertTrue(full.contains("app.version"))
            assertTrue(full.contains("walktape-test"))
            assertTrue(full.contains("NETWORK=1"))
            assertTrue(full.contains("CONFLICT=1"))
            assertFalse(full.contains("ENCRYPTEDSECRETVALUE"))
            assertFalse(full.contains("cred-key-987"))
            assertFalse(full.contains("encryptedPassword"))
            assertFalse(full.contains("credentialId"))

            val withoutConnections = collector.render(sections, collector.defaultIncluded)
            assertTrue(withoutConnections.contains("app.version"))
            assertFalse(withoutConnections.contains("10.0.2.2"))
            assertFalse(withoutConnections.contains("walktape-test"))
        } finally {
            db.close()
        }
    }
}
