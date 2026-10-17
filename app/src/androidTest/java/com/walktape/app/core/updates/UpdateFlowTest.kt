package com.walktape.app.core.updates

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.db.ProfileSettingsEntity
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.net.NetworkTraffic
import com.walktape.app.core.settings.ProfileNetworkGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * W11-T04/T05: update check parsing against a real HTTP server, APK download with
 * honest verification refusal, offline-only blocking without any request, and the
 * archive-info path over the actually installed package.
 */
@RunWith(AndroidJUnit4::class)
class UpdateFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun manager(server: MockWebServer,
                        gate: (suspend () -> Boolean)? = null): UpdateManager =
        UpdateManager(context, OkHttpClient(),
            apiBaseUrl = server.url("/").toString().trimEnd('/'),
            networkAllowed = gate ?: { true })

    private fun releaseBody(server: MockWebServer, tag: String): String = """
        {"tag_name":"$tag","name":"$tag","body":"Notes for $tag",
         "assets":[{"name":"walktape.apk","browser_download_url":"${server.url("/dl/walktape.apk")}",
                    "size":2048,"content_type":"application/vnd.android.package-archive"}]}
    """.trimIndent()

    @Test
    fun checkParsesRemoteReleaseAndOffersTheAsset(): Unit = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(MockResponse.Builder()
                .body(releaseBody(server, "v99.0.0"))
                .addHeader("Content-Type", "application/json")
                .build())
            val outcome = manager(server).check()
            assertTrue(outcome is UpdateManager.CheckOutcome.Available)
            val release = (outcome as UpdateManager.CheckOutcome.Available).release
            assertEquals("v99.0.0", release.tagName)
            assertEquals("walktape.apk", AssetSelector.selectApk(release.assets)!!.name)
        }
    }

    @Test
    fun noReleasesIsAnHonestOutcome(): Unit = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(MockResponse.Builder().code(404).body("{}").build())
            val outcome = manager(server).check()
            assertTrue(outcome is UpdateManager.CheckOutcome.NoReleases)
        }
    }

    @Test
    fun downloadedNonApkIsRejectedAsCorruptAndNeverInstalled(): Unit = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            server.enqueue(MockResponse.Builder()
                .body("x".repeat(2048))
                .addHeader("Content-Type", "application/vnd.android.package-archive")
                .build())
            val asset = ReleaseAsset("walktape.apk", server.url("/dl/walktape.apk").toString(),
                2048L, "application/vnd.android.package-archive")
            val updateManager = manager(server)
            var progressCalls = 0
            val outcome = updateManager.download(asset, "v99.0.0") { bytes, total ->
                progressCalls++
                assertEquals(2048L, total)
                assertTrue(bytes in 1..2048)
            }
            assertTrue(outcome is UpdateManager.DownloadOutcome.Done)
            val file = (outcome as UpdateManager.DownloadOutcome.Done).file
            assertEquals(2048L, file.length())
            assertTrue(progressCalls > 0)
            assertEquals(ApkVerifier.Outcome.Corrupt, updateManager.verify(file))
            file.delete()
        }
    }

    @Test
    fun offlineOnlyGateBlocksTheCheckBeforeAnyRequest(): Unit = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val id = db.profileDao().insert(ProfileEntity(name = "Offline",
                    serverUrl = server.url("/").toString().trimEnd('/'), username = "u",
                    isActive = true, createdAt = 1, credentialId = "k", encryptedPassword = "e"))
                db.downloadDao().upsertSettings(ProfileSettingsEntity(id, offlineOnly = true))
                val gate = ProfileNetworkGate(db.downloadDao(), scope,
                    db.profileDao().observeAll().map { rows -> rows.firstOrNull { it.isActive }?.id })
                gate.awaitReady()
                assertTrue(gate.offlineOnly(id))

                val outcome = manager(server) {
                    gate.awaitReady(); !gate.offlineOnly(-1)
                }.check()
                assertTrue(outcome is UpdateManager.CheckOutcome.BlockedOffline)
                assertEquals("Solo offline must produce no network traffic", 0, server.requestCount)
            } finally {
                NetworkTraffic.setOffline(false)
                scope.cancel()
                db.close()
            }
        }
    }

    @Test
    fun installedApkIsReadableSamePackageButNotNewer(): Unit = runBlocking {
        val verifier = ApkVerifier(context)
        // The app's own installed APK: real archive-info parsing, package matches,
        // versionCode equal → honest NotNewer (never offered as an update).
        val outcome = verifier.verify(File(context.applicationInfo.sourceDir))
        assertEquals(ApkVerifier.Outcome.NotNewer, outcome)
    }
}
