package com.walktape.app.core.updates

import com.walktape.app.core.updates.UpdateManager.CheckOutcome
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateLogicTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.close() }

    // --- version parsing and comparison ---

    @Test
    fun `parses tag and plain version strings`() {
        assertEquals(ParsedVersion(0, 2, 0), ParsedVersion.parse("v0.2.0"))
        assertEquals(ParsedVersion(0, 2, 0), ParsedVersion.parse("0.2.0"))
        assertEquals(ParsedVersion(1, 0, 0), ParsedVersion.parse("1.0"))
        assertNull(ParsedVersion.parse("release-2026"))
        assertNull(ParsedVersion.parse(""))
    }

    @Test
    fun `compares versions by major minor patch`() {
        assertTrue(ParsedVersion(0, 10, 0) > ParsedVersion(0, 9, 9))
        assertTrue(ParsedVersion(1, 0, 0) > ParsedVersion(0, 99, 99))
        assertEquals(0, ParsedVersion(0, 1, 0).compareTo(ParsedVersion(0, 1, 0)))
    }

    // --- pure update decision ---

    @Test
    fun `newer release is available`() {
        val release = ReleaseInfo("v0.2.0", "0.2.0", "notes", null, emptyList())
        assertTrue(UpdateManager.decide(release, "0.1.0") is CheckOutcome.Available)
    }

    @Test
    fun `same or older release is up to date`() {
        val same = ReleaseInfo("v0.1.0", null, null, null, emptyList())
        val older = ReleaseInfo("v0.0.9", null, null, null, emptyList())
        assertTrue(UpdateManager.decide(same, "0.1.0") is CheckOutcome.UpToDate)
        assertTrue(UpdateManager.decide(older, "0.1.0") is CheckOutcome.UpToDate)
    }

    @Test
    fun `unparsable tags stay unknown rather than guessed`() {
        val release = ReleaseInfo("nightly-42", null, null, null, emptyList())
        assertTrue(UpdateManager.decide(release, "0.1.0") is CheckOutcome.UnknownVersion)
    }

    // --- asset selection ---

    @Test
    fun `selects the single apk asset`() {
        val asset = ReleaseAsset("walktape.apk", "https://x/walktape.apk", 1L, "application/vnd.android.package-archive")
        assertEquals(asset, AssetSelector.selectApk(listOf(asset)))
    }

    @Test
    fun `prefers universal over abi specific apks`() {
        val universal = ReleaseAsset("walktape-universal.apk", "u", 1L, "")
        val arm64 = ReleaseAsset("walktape-arm64.apk", "a", 1L, "")
        assertEquals(universal, AssetSelector.selectApk(listOf(arm64, universal)))
    }

    @Test
    fun `refuses ambiguous apk candidates`() {
        val a = ReleaseAsset("one.apk", "a", 1L, "")
        val b = ReleaseAsset("two.apk", "b", 1L, "")
        assertNull(AssetSelector.selectApk(listOf(a, b)))
        assertNull(AssetSelector.selectApk(listOf(
            ReleaseAsset("walktape-arm64.apk", "a", 1L, ""),
            ReleaseAsset("walktape-x86.apk", "b", 1L, ""))))
    }

    @Test
    fun `no apk assets means no selection`() {
        val zip = ReleaseAsset("sources.zip", "z", 1L, "")
        assertNull(AssetSelector.selectApk(listOf(zip)))
    }

    // --- GitHub Releases client against a local server ---

    private fun releases(body: String): GitHubReleases =
        GitHubReleases(OkHttpClient(), server.url("/").toString().trimEnd('/'))

    @Test
    fun `parses the latest release payload`() = runBlocking {
        server.enqueue(MockResponse.Builder()
            .body("""
                {"tag_name":"v0.2.0","name":"0.2.0","body":"Fixed playback",
                 "published_at":"2026-10-16T00:00:00Z",
                 "assets":[{"name":"walktape.apk","browser_download_url":"${server.url("/dl/apk")}",
                            "size":123,"content_type":"application/vnd.android.package-archive"}]}
            """.trimIndent())
            .addHeader("Content-Type", "application/json")
            .build())
        val result = releases("").latest("rosariomoscato", "walktape")
        assertTrue(result is GitHubReleases.Result.Latest)
        val release = (result as GitHubReleases.Result.Latest).release
        assertEquals("v0.2.0", release.tagName)
        assertEquals(1, release.assets.size)
        assertEquals(123L, release.assets.first().sizeBytes)
    }

    @Test
    fun `maps 404 to honest no releases`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).body("{}").build())
        assertEquals(GitHubReleases.Result.NoReleases, releases("").latest("o", "r"))
    }

    @Test
    fun `maps exhausted rate limits`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(403)
            .addHeader("X-RateLimit-Remaining", "0")
            .addHeader("Retry-After", "1200")
            .build())
        val result = releases("").latest("o", "r")
        assertTrue(result is GitHubReleases.Result.RateLimited)
        assertEquals(1200L, (result as GitHubReleases.Result.RateLimited).retryAfterSec)
    }

    @Test
    fun `maps broken payloads to invalid`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("<<not json>>")
            .addHeader("Content-Type", "application/json").build())
        assertEquals(GitHubReleases.Result.Invalid, releases("").latest("o", "r"))
    }

    @Test
    fun `sends the documented github headers`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").build())
        releases("").latest("o", "r")
        val recorded = server.takeRequest()
        assertEquals("application/vnd.github+json", recorded.headers["Accept"])
        assertEquals(GitHubReleases.USER_AGENT, recorded.headers["User-Agent"])
        assertEquals("2022-11-28", recorded.headers["X-GitHub-Api-Version"])
        assertTrue(recorded.url.encodedPath.endsWith("/repos/o/r/releases/latest"))
    }
}
