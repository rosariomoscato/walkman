package com.walktape.app.core.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.Graph
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.profiles.CredentialVault
import com.walktape.app.core.profiles.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Opt-in helpers for manual UI inspection with real data on the emulator.
 * Seed runs only when /data/local/tmp/wt-seed exists (pushed via adb);
 * Cleanup removes the seeded profile when /data/local/tmp/wt-seed-cleanup exists.
 * Credentials never appear in output.
 */
@RunWith(AndroidJUnit4::class)
class UiSeedTest {
    private val seedName = "UI check"

    private fun repository() = ProfileRepository(Graph.database, CredentialVault(), Graph.app().filesDir)

    @Test fun seedRealLibraryForManualInspection() = runBlocking {
        org.junit.Assume.assumeTrue("Seed marker required", File("/data/local/tmp/wt-seed").exists())
        org.junit.Assume.assumeTrue("Local server config required", File("/data/local/tmp/walktape-test-server.env").exists())
        val config = File("/data/local/tmp/walktape-test-server.env").readLines()
            .filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val url = config.getValue("ND_URL").replace("localhost", "10.0.2.2")
        val repository = repository()
        val existing = repository.profiles.first().firstOrNull { it.name == seedName }
        val id = existing?.id ?: (repository.addVerified(
            seedName, url, config.getValue("ND_USER"), config.getValue("ND_PASS"), true
        ) as ApiResult.Success).value
        assertTrue(repository.activate(id))
        val sync = LibrarySync(Graph.database.libraryDao(), playlistIndex = PlaylistIndex(Graph.database.browseDao())) { repository.clientFor(it) }
        assertTrue(sync.sync(id))
        println("wave04-seed ok profileId=$id")
    }

    @Test fun cleanupSeededProfile() = runBlocking {
        org.junit.Assume.assumeTrue("Cleanup marker required", File("/data/local/tmp/wt-seed-cleanup").exists())
        val repository = repository()
        repository.profiles.first().firstOrNull { it.name == seedName }?.let {
            assertTrue(repository.remove(it.id))
        }
        println("wave04-seed cleaned")
    }
}
