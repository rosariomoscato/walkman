package com.walktape.app.playback

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.walktape.app.Graph
import com.walktape.app.core.db.*
import com.walktape.app.core.downloads.DownloadStore
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.playback.browse.AutoBrowseIds
import kotlinx.coroutines.*
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * W10: the real PlaybackService exposed as a MediaLibraryService for Android Auto. These
 * tests exercise the media-library contract (root/children/item/search/pagination), queue
 * identity of commands coming from browse ids, offline behaviour (zero HTTP, disk-served
 * covers) and the profile switch invalidation — through a MediaBrowser bound to the service,
 * which is a contract test, not an Android Auto projection (see docs/ANDROID_AUTO.md).
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Wave10AutoBrowseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val params = MediaLibraryService.LibraryParams.Builder().build()

    private suspend fun await(message: String, predicate: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (withContext(Dispatchers.Main) { predicate() }) return
            delay(100)
        }
        throw AssertionError(message)
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    /** Browser/controller calls happen on the app main thread; the future is awaited off it. */
    private suspend fun <T> call(block: () -> ListenableFuture<T>): T =
        onMain { block() }.get(30, TimeUnit.SECONDS)

    /**
     * Category-aware fixture server: any number of background requests (scrobble retries,
     * workers) gets a sane answer, so test sequencing never depends on process noise. The
     * per-category counters drive the zero-HTTP assertions.
     */
    private class FixtureDispatcher : Dispatcher() {
        val streams = AtomicInteger()
        val covers = AtomicInteger()
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.url?.encodedPath ?: ""
            return when {
                path.contains("getCoverArt") -> {
                    covers.incrementAndGet()
                    MockResponse.Builder().addHeader("Content-Type", "image/png")
                        .body(Buffer().write(pngBytes)).build()
                }
                path.contains("stream") || path.contains("download") -> {
                    streams.incrementAndGet()
                    MockResponse.Builder().addHeader("Content-Type", "audio/wav")
                        .body(Buffer().write(wavBytes)).build()
                }
                else -> MockResponse.Builder().body(
                    """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build()
            }
        }
        val wavBytes = ByteArray(44 + 44_100 * 2 * 6).also { data ->
            ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(data.size - 8); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(44_100); putInt(88_200)
                putShort(2); putShort(16); put("data".toByteArray()); putInt(data.size - 44)
            } }
        val pngBytes = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(0xFF3D6FB4.toInt()) }
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()
    }

    private fun song(profileId: Long, id: String, albumId: String, title: String, track: Int,
        coverArt: String? = null, artistId: String = "ar1") = SongEntity(profileId, id, albumId,
        albumId, artistId, title, if (albumId == "al1") "Album One" else if (albumId == "al2")
        "Album Two" else "Resume Album", "Fixture Artist", null, null, track, 1, 2001, null,
        coverArt, 6, 705, "wav", "audio/wav", 1_000, null, null, null, null)

    private suspend fun buildBrowser(): MediaBrowser {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        return onMain { MediaBrowser.Builder(context, token).buildAsync() }.get(20, TimeUnit.SECONDS)
    }

    private fun releaseBrowser(browser: MediaBrowser?) {
        browser?.let { b -> runBlocking { onMain { b.release() } } }
    }

    private suspend fun clearFixture(browser: MediaBrowser?) {
        onMain {
            browser?.pause()
            browser?.stop()
            browser?.clearMediaItems()
        }
    }

    @Test fun browseTreeSearchCommandsKeepQueueIdentityAndNeverAutoplay(): Unit = runBlocking {
        val previous = Graph.database.profileDao().active()?.id
        var profile: Long? = null
        var browser: MediaBrowser? = null
        var phone: MediaController? = null
        val fixture = FixtureDispatcher()
        MockWebServer().apply { dispatcher = fixture; start() }.use { server ->
            try {
                val id = (Graph.profiles.addVerified("W10 auto fixture ${System.nanoTime()}",
                    server.url("/").toString(), "fixture", "fixture-only", true) as ApiResult.Success).value
                profile = id
                Graph.database.libraryDao().upsertArtists(listOf(
                    ArtistEntity(id, "ar1", "Fixture Artist", null, 2)))
                Graph.database.libraryDao().upsertAlbums(listOf(
                    AlbumEntity(id, "al1", "Album One", "Fixture Artist", "ar1", "cov-al1", 2, 12, 2001, null, null, false, null),
                    AlbumEntity(id, "al2", "Album Two", "Fixture Artist", "ar1", "cov-al2", 1, 6, 2002, null, null, false, null)))
                Graph.database.libraryDao().upsertSongs(listOf(
                    song(id, "a1", "al1", "Alpha One", 1, "cov-al1"),
                    song(id, "a2", "al1", "Alpha Two", 2, "cov-al1"),
                    song(id, "b1", "al2", "Beta One", 1, "cov-al2")))
                Graph.database.browseDao().replacePlaylist(
                    PlaylistEntity(id, "pl1", "Fixture List", "fixture", false, null, null, 3, 18,
                        null, null, System.currentTimeMillis()),
                    listOf(PlaylistEntryEntity(id, "pl1", 0, "a1", "Alpha One", "Fixture Artist", "Album One", 6, 2001, null),
                        PlaylistEntryEntity(id, "pl1", 1, "b1", "Beta One", "Fixture Artist", "Album Two", 6, 2002, null),
                        PlaylistEntryEntity(id, "pl1", 2, "a1", "Alpha One", "Fixture Artist", "Album One", 6, 2001, null)))
                // One completed download (b1) so the downloads area is real, not demo data.
                Graph.downloadStore.enqueueSongs(id, listOf(song(id, "b1", "al2", "Beta One", 1)), "single")
                Graph.database.downloadDao().claimNext(id, 1)
                assertEquals(DownloadStore.Outcome.Completed, Graph.downloadStore.transfer(id, "b1",
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
                // A downloaded-playlist subscription snapshot keeps duplicates (W08-T05).
                Graph.downloadStore.enqueuePlaylist(id, "pl1")
                Graph.database.downloadDao().claimNext(id, 2)
                assertEquals(DownloadStore.Outcome.Completed, Graph.downloadStore.transfer(id, "a1",
                    OpenSubsonicClient.streamingHttpClient(), { false }) { _, _ -> })
                val completedRow = Graph.database.downloadDao().download(id, "b1")!!
                Graph.database.downloadDao().upsertDownload(completedRow.copy(songId = "stale", title = "Stale file",
                    relativePath = "music/not-present.wav"))
                Graph.database.downloadDao().upsertGroup(DownloadGroupEntity(id, "playlist:empty", "Empty snapshot",
                    "[]", System.currentTimeMillis(), false))
                Graph.database.downloadDao().upsertGroup(DownloadGroupEntity(id, "playlist:partial", "Partial snapshot",
                    "[\"a1\",\"stale\"]", System.currentTimeMillis(), true))
                Graph.profiles.activate(id)
                await("Profile connection must be active") {
                    Graph.database.profileDao().active()?.id == id
                }
                val baselineStreams = fixture.streams.get()
                val baselineCovers = fixture.covers.get()

                val b = buildBrowser(); browser = b

                // Root and categories
                val root = call { b.getLibraryRoot(params) }
                assertEquals(LibraryResult.RESULT_SUCCESS, root.resultCode)
                assertEquals(AutoBrowseIds.ROOT, root.value?.mediaId)
                val rootChildren = call { b.getChildren(AutoBrowseIds.ROOT, 0, 100, params) }
                assertEquals(LibraryResult.RESULT_SUCCESS, rootChildren.resultCode)
                assertEquals(3, rootChildren.value?.size)
                assertTrue(rootChildren.value!!.any { it.mediaId == AutoBrowseIds.library(id) })
                assertEquals(3, call { b.getChildren(AutoBrowseIds.library(id), 0, 100, params) }.value?.size)
                assertTrue(rootChildren.value!!.none { it.mediaMetadata.isPlayable == true })

                // Albums list and single album in disc/track order, with art and types
                val albums = call { b.getChildren(AutoBrowseIds.albums(id), 0, 100, params) }
                assertEquals(2, albums.value?.size)
                val albumOne = albums.value!!.first { it.mediaMetadata.title?.toString() == "Album One" }
                assertEquals(true, albumOne.mediaMetadata.isBrowsable)
                assertEquals(true, albumOne.mediaMetadata.isPlayable)
                assertEquals(MediaMetadata.MEDIA_TYPE_ALBUM, albumOne.mediaMetadata.mediaType)
                assertEquals(Uri.parse("content://com.walktape.app.art/$id/cov-al1/512"),
                    albumOne.mediaMetadata.artworkUri)
                val albumSongs = call { b.getChildren(albumOne.mediaId, 0, 100, params) }
                assertEquals(listOf("Alpha One", "Alpha Two"),
                    albumSongs.value!!.map { it.mediaMetadata.title?.toString() })
                assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC,
                    albumSongs.value!!.first().mediaMetadata.mediaType)

                // Artists
                val artists = call { b.getChildren(AutoBrowseIds.artists(id), 0, 100, params) }
                assertEquals(1, artists.value?.size)
                val artistAlbums = call { b.getChildren(artists.value!!.first().mediaId, 0, 100, params) }
                assertEquals(2, artistAlbums.value?.size)

                // Songs paginated
                val songsPage1 = call { b.getChildren(AutoBrowseIds.songs(id), 0, 2, params) }
                val songsPage2 = call { b.getChildren(AutoBrowseIds.songs(id), 1, 2, params) }
                assertEquals(2, songsPage1.value?.size)
                assertEquals(1, songsPage2.value?.size)

                // Invalid ids are honest errors, never fake content (page validation is
                // enforced by the MediaBrowser client itself, the window clamp is unit-tested).
                val badItem = call { b.getItem("wtb/${id + 999}/album/al1") }
                assertNotEquals(LibraryResult.RESULT_SUCCESS, badItem.resultCode)
                val single = call { b.getItem(AutoBrowseIds.song(id, "a1")) }
                assertEquals(LibraryResult.RESULT_SUCCESS, single.resultCode)
                assertEquals("Alpha One", single.value?.mediaMetadata?.title?.toString())

                // Playlist snapshot keeps duplicate occurrences in order
                val playlists = call { b.getChildren(AutoBrowseIds.playlists(id), 0, 100, params) }
                assertEquals(1, playlists.value?.size)
                val playlistChildren = call {
                    b.getChildren(playlists.value!!.first().mediaId, 0, 100, params) }
                assertEquals(listOf("Alpha One", "Beta One", "Alpha One"),
                    playlistChildren.value!!.map { it.mediaMetadata.title?.toString() })

                // Downloads area: the playlist group snapshot plus the single completed song
                val downloads = call { b.getChildren(AutoBrowseIds.downloads(id), 0, 100, params) }
                assertEquals(LibraryResult.RESULT_SUCCESS, downloads.resultCode)
                val groupPrefix = AutoBrowseIds.downloadGroup(id, "playlist:pl1")
                val group = downloads.value!!.first { it.mediaId == groupPrefix }
                assertEquals("Fixture List", group.mediaMetadata.title?.toString())
                val groupChildren = call { b.getChildren(group.mediaId, 0, 100, params) }
                assertEquals(listOf("Alpha One", "Beta One", "Alpha One"),
                    groupChildren.value!!.map { it.mediaMetadata.title?.toString() })
                val emptyGroup = downloads.value!!.first { it.mediaId == AutoBrowseIds.downloadGroup(id, "playlist:empty") }
                assertEquals(false, emptyGroup.mediaMetadata.isPlayable)
                assertEquals(0, call { b.getChildren(emptyGroup.mediaId, 0, 100, params) }.value!!.size)
                val partial = downloads.value!!.first { it.mediaId == AutoBrowseIds.downloadGroup(id, "playlist:partial") }
                assertEquals(false, partial.mediaMetadata.isPlayable)
                assertTrue(partial.mediaMetadata.subtitle.toString().startsWith("1/2"))
                assertEquals(listOf("Alpha One"), call { b.getChildren(partial.mediaId, 0, 100, params) }
                    .value!!.map { it.mediaMetadata.title.toString() })
                val downloadedSongs = call { b.getChildren(AutoBrowseIds.downloadedSongs(id), 0, 100, params) }
                assertEquals(listOf("Alpha One", "Beta One"),
                    downloadedSongs.value!!.map { it.mediaMetadata.title?.toString() })

                // Local search without touching the network
                val search = call { b.search("alpha", params) }
                assertEquals(LibraryResult.RESULT_SUCCESS, search.resultCode)
                val searchItems = call { b.getSearchResult("alpha", 0, 100, params) }
                assertTrue(searchItems.value!!.map { it.mediaMetadata.title?.toString() }
                    .containsAll(listOf("Alpha One", "Alpha Two")))
                val otherQuery = call { b.getSearchResult("beta", 0, 100, params) }
                assertEquals(listOf("Beta One"), otherQuery.value!!.map { it.mediaMetadata.title?.toString() })
                val blank = call { b.search("  ", params) }
                assertNotEquals(LibraryResult.RESULT_SUCCESS, blank.resultCode)

                // Browsing/searching never started playback, queued media or scrobbled
                assertEquals("Browsing must not issue HTTP requests", baselineStreams, fixture.streams.get())
                assertEquals("Browsing must not fetch covers", baselineCovers, fixture.covers.get())
                assertEquals(Player.STATE_IDLE, onMain { b.playbackState })
                assertEquals(0, onMain { b.mediaItemCount })
                assertTrue(Graph.database.scrobbleDao().pending(id, 10).isEmpty())

                // Explicit play from the car: album browse id expands into the real queue
                onMain {
                    b.setMediaItem(albumOne)
                    b.prepare()
                    b.play()
                }
                await("Album browse id must expand into the queue and play") {
                    b.isPlaying && b.currentPosition > 100
                }
                assertEquals(2, onMain { b.mediaItemCount })
                val playing = onMain { b.currentMediaItem }
                assertTrue(playing?.mediaId?.startsWith(QueueIdentity.PREFIX) == true)
                assertEquals("a1", playing?.mediaMetadata?.extras?.getString(QueueIdentity.SONG))
                assertEquals("Alpha One", playing?.mediaMetadata?.title?.toString())

                // The phone sees the exact same queue and occurrence (single session)
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                phone = onMain {
                    MediaController.Builder(context, token).buildAsync()
                }.get(20, TimeUnit.SECONDS)
                val phoneItem = onMain { phone!!.currentMediaItem }
                assertEquals(playing?.mediaId, phoneItem?.mediaId)
                assertEquals(playing?.mediaMetadata?.extras?.getString(QueueIdentity.SONG),
                    phoneItem?.mediaMetadata?.extras?.getString(QueueIdentity.SONG))
                onMain { b.pause() }
                await("Phone controller observes the pause") { phone!!.playWhenReady.not() }
                onMain { b.setMediaItem(playlists.value!!.first()); b.prepare(); b.play() }
                await("Playlist from Auto preserves three distinct occurrences") {
                    b.isPlaying && b.mediaItemCount == 3 &&
                        (0 until 3).all { b.getMediaItemAt(it).mediaId.startsWith(QueueIdentity.PREFIX) }
                }
                val queue = onMain { (0 until b.mediaItemCount).map { b.getMediaItemAt(it) } }
                assertEquals(listOf("a1", "b1", "a1"), queue.map { it.mediaMetadata.extras?.getString(QueueIdentity.SONG) })
                assertEquals(3, queue.map { it.mediaId }.distinct().size)
                assertTrue(queue.all { it.mediaMetadata.extras?.getString(QueueIdentity.ORIGIN) == "Fixture List" })
                onMain { b.shuffleModeEnabled = true; b.repeatMode = Player.REPEAT_MODE_ALL; b.pause() }
                await("Phone sees Auto shuffle and repeat") { phone!!.shuffleModeEnabled && phone!!.repeatMode == Player.REPEAT_MODE_ALL }
                // A legacy browser with an effectively unbounded page size can reach all
                // 10,003 indexed songs through bounded continuation folders.
                Graph.database.libraryDao().upsertSongs((0 until 10_000).map {
                    song(id, "large-$it", "large", "Large ${it.toString().padStart(5, '0')}", it + 1)
                })
                var parentId: String? = AutoBrowseIds.songs(id)
                val seen = mutableSetOf<String>()
                var pages = 0
                while (parentId != null) {
                    val currentParent = parentId
                    val page = call { b.getChildren(currentParent, 0, Int.MAX_VALUE, params) }.value!!
                    assertTrue(page.size <= AutoBrowseIds.MAX_PAGE_SIZE + 1)
                    page.filter { it.mediaMetadata.isPlayable == true }.forEach { assertTrue(seen.add(it.mediaId)) }
                    parentId = page.lastOrNull()?.takeIf { AutoBrowseIds.parse(it.mediaId) is AutoBrowseIds.Node.Page }?.mediaId
                    assertTrue(++pages <= 60)
                }
                assertEquals(10_003, seen.size)
            } finally {
                clearFixture(browser)
                releaseBrowser(browser)
                phone?.let { c -> runBlocking { onMain { c.release() } } }
                context.stopService(Intent(context, PlaybackService::class.java))
                if (previous != null) Graph.profiles.activate(previous)
                profile?.let { Graph.profiles.remove(it) }
            }
        }
    }

    @Test fun offlineBrowseSearchAndCoversStayOnDiskWithZeroHttpRequests(): Unit = runBlocking {
        val previous = Graph.database.profileDao().active()?.id
        var profile: Long? = null
        var browser: MediaBrowser? = null
        val fixture = FixtureDispatcher()
        MockWebServer().apply { dispatcher = fixture; start() }.use { server ->
            try {
                val id = (Graph.profiles.addVerified("W10 offline browse ${System.nanoTime()}",
                    server.url("/").toString(), "fixture", "fixture-only", true) as ApiResult.Success).value
                profile = id
                Graph.database.libraryDao().upsertAlbums(listOf(
                    AlbumEntity(id, "al1", "Offline Album", "Fixture Artist", "ar1", "cov-x", 1, 6, 2001, null, null, false, null)))
                Graph.database.libraryDao().upsertSongs(listOf(
                    song(id, "o1", "al1", "Offline Song", 1, "cov-x")))
                Graph.profiles.activate(id)
                await("Profile connection must be active") {
                    Graph.database.profileDao().active()?.id == id
                }
                // Cache one cover while still online; a second art id stays uncached.
                assertNotNull(Graph.coverArtStore(id).bitmap("cov-x", 512))

                Graph.database.downloadDao().upsertSettings(ProfileSettingsEntity(id, offlineOnly = true))
                Graph.profiles.activate(id)
                await("Offline policy must load") { Graph.networkGate.offlineOnly(id) }
                val baselineStreams = fixture.streams.get()
                val baselineCovers = fixture.covers.get()
                val baselineRequests = server.requestCount

                val b = buildBrowser(); browser = b
                val root = call { b.getLibraryRoot(params) }
                assertEquals(LibraryResult.RESULT_SUCCESS, root.resultCode)
                val albums = call { b.getChildren(AutoBrowseIds.albums(id), 0, 100, params) }
                assertEquals(listOf("Offline Album"), albums.value!!.map { it.mediaMetadata.title?.toString() })
                val songs = call { b.getChildren(albums.value!!.first().mediaId, 0, 100, params) }
                assertEquals(listOf("Offline Song"), songs.value!!.map { it.mediaMetadata.title?.toString() })
                val search = call { b.search("offline", params) }
                assertEquals(LibraryResult.RESULT_SUCCESS, search.resultCode)
                val results = call { b.getSearchResult("offline", 0, 100, params) }
                assertTrue(results.value!!.isNotEmpty())

                // Covers come from disk through the provider; an uncached id fails honestly.
                val cachedUri = albums.value!!.first().mediaMetadata.artworkUri
                val bytes = context.contentResolver.openInputStream(cachedUri!!)!!.use { it.readBytes() }
                assertTrue(bytes.size > 0)
                val uncached = Uri.parse("content://com.walktape.app.art/$id/never-cached/512")
                assertNull(runCatching {
                    context.contentResolver.openInputStream(uncached)!!.use { it.readBytes() }
                }.getOrNull())
                // Art of another profile is refused outright (profile isolation).
                val foreign = Uri.parse("content://com.walktape.app.art/${id + 999}/cov-x/512")
                assertNull(runCatching {
                    context.contentResolver.openInputStream(foreign)!!.use { it.readBytes() }
                }.getOrNull())

                assertEquals("Offline browse/covers must not issue HTTP requests",
                    baselineStreams, fixture.streams.get())
                assertEquals("Offline covers must stay on disk",
                    baselineCovers, fixture.covers.get())
                assertEquals("All app endpoints remain silent in offline-only", baselineRequests, server.requestCount)
                assertTrue(Graph.database.scrobbleDao().pending(id, 10).isEmpty())
            } finally {
                clearFixture(browser)
                releaseBrowser(browser)
                context.stopService(Intent(context, PlaybackService::class.java))
                if (previous != null) Graph.profiles.activate(previous)
                profile?.let { Graph.profiles.remove(it) }
            }
        }
    }

    @Test fun profileSwitchInvalidatesForeignBrowseIdsAndPlaybackRequests(): Unit = runBlocking {
        val previous = Graph.database.profileDao().active()?.id
        var first: Long? = null
        var second: Long? = null
        var browser: MediaBrowser? = null
        val fixture = FixtureDispatcher()
        MockWebServer().apply { dispatcher = fixture; start() }.use { server ->
            try {
                first = (Graph.profiles.addVerified("W10 profile A ${System.nanoTime()}",
                    server.url("/").toString(), "fixture", "fixture-only", true) as ApiResult.Success).value
                second = (Graph.profiles.addVerified("W10 profile B ${System.nanoTime()}",
                    server.url("/").toString(), "fixture2", "fixture-only", true) as ApiResult.Success).value
                Graph.database.libraryDao().upsertAlbums(listOf(
                    AlbumEntity(first!!, "al1", "Profile A Album", "A", "ar1", null, 1, 6, 2001, null, null, false, null)))
                Graph.database.libraryDao().upsertSongs(listOf(song(first!!, "a1", "al1", "Song A", 1)))
                Graph.database.libraryDao().upsertAlbums(listOf(
                    AlbumEntity(second!!, "al2", "Profile B Album", "B", "ar1", null, 1, 6, 2001, null, null, false, null)))
                Graph.database.libraryDao().upsertSongs(listOf(song(second!!, "b1", "al2", "Song B", 1)))

                Graph.profiles.activate(first!!)
                val b = buildBrowser(); browser = b
                assertEquals("Song A", call { b.getItem(AutoBrowseIds.song(first, "a1")) }.value?.mediaMetadata?.title?.toString())
                Graph.profiles.activate(second!!)
                await("Profile B active") { Graph.database.profileDao().active()?.id == second }
                withTimeout(15_000) {
                    while (call { b.getItem(AutoBrowseIds.song(second, "b1")) }.value == null) delay(100)
                }

                // Browsing profile A's ids while B is active yields honest empties/errors.
                val foreignChildren = call { b.getChildren(AutoBrowseIds.album(first!!, "al1"), 0, 100, params) }
                assertEquals(LibraryResult.RESULT_SUCCESS, foreignChildren.resultCode)
                assertEquals(0, foreignChildren.value?.size)
                val foreignItem = call { b.getItem(AutoBrowseIds.song(first!!, "a1")) }
                assertNotEquals(LibraryResult.RESULT_SUCCESS, foreignItem.resultCode)

                // A playback request for the foreign profile never reaches the queue.
                onMain {
                    b.setMediaItem(MediaItem.Builder()
                        .setMediaId(AutoBrowseIds.song(first!!, "a1")).build())
                    b.prepare()
                    b.play()
                }
                delay(2_000)
                assertEquals("Foreign-profile media must never enter the queue",
                    0, onMain { b.mediaItemCount })

                // The active profile keeps working: B's own album resolves.
                val ownChildren = call { b.getChildren(AutoBrowseIds.album(second!!, "al2"), 0, 100, params) }
                assertEquals(listOf("Song B"), ownChildren.value!!.map { it.mediaMetadata.title?.toString() })
                onMain { b.pause() }
            } finally {
                clearFixture(browser)
                releaseBrowser(browser)
                context.stopService(Intent(context, PlaybackService::class.java))
                if (previous != null) Graph.profiles.activate(previous)
                first?.let { Graph.profiles.remove(it) }
                second?.let { Graph.profiles.remove(it) }
            }
        }
    }

    @Test fun restoredQueueSurvivesRebindAndExplicitPlayFromTheCar(): Unit = runBlocking {
        val previous = Graph.database.profileDao().active()?.id
        var profile: Long? = null
        var parkingProfile: Long? = null
        var browser: MediaBrowser? = null
        val fixture = FixtureDispatcher()
        MockWebServer().apply { dispatcher = fixture; start() }.use { server ->
            try {
                // Seed an INACTIVE profile. The first profile auto-activates, so writing a
                // saved queue to it while an existing service is alive races its empty save.
                parkingProfile = (Graph.profiles.addVerified("W10 parking ${System.nanoTime()}",
                    server.url("/").toString(), "parking", "fixture-only", true) as ApiResult.Success).value
                Graph.profiles.activate(parkingProfile)
                val id = (Graph.profiles.addVerified("W10 resume fixture ${System.nanoTime()}",
                    server.url("/").toString(), "fixture", "fixture-only", true) as ApiResult.Success).value
                profile = id
                Graph.database.libraryDao().upsertAlbums(listOf(
                    AlbumEntity(id, "al1", "Resume Album", "Fixture Artist", "ar1", null, 2, 12, 2001, null, null, false, null)))
                Graph.database.libraryDao().upsertSongs(listOf(
                    song(id, "r1", "al1", "Resume One", 1), song(id, "r2", "al1", "Resume Two", 2)))
                // A saved queue paused mid-song on the second occurrence, with shuffle on.
                val queue = SavedQueue(id,
                    listOf(SavedQueueItem(QueueIdentity.PREFIX + "uuid-r1", "r1", "Resume One", "Fixture Artist", "Resume Album", 6_000),
                        SavedQueueItem(QueueIdentity.PREFIX + "uuid-r2", "r2", "Resume Two", "Fixture Artist", "Resume Album", 6_000)),
                    listOf(QueueIdentity.PREFIX + "uuid-r2", QueueIdentity.PREFIX + "uuid-r1"),
                    QueueIdentity.PREFIX + "uuid-r2", 1_500, Player.REPEAT_MODE_OFF, true,
                    System.currentTimeMillis())
                assertTrue(queue.isValid())
                Graph.queues.enqueue(queue)
                Graph.queues.flush()
                Graph.profiles.activate(id)
                val baselineStreams = fixture.streams.get()
                val baselineCovers = fixture.covers.get()

                val b = buildBrowser(); browser = b
                await("Saved queue restored without autoplay") {
                    b.mediaItemCount == 2 && b.playbackState == Player.STATE_IDLE
                }
                assertEquals("Restoring the queue must stay offline", baselineStreams, fixture.streams.get())
                assertEquals("Restoring the queue must not fetch covers", baselineCovers, fixture.covers.get())
                assertEquals(QueueIdentity.PREFIX + "uuid-r2", onMain { b.currentMediaItem }?.mediaId)
                assertTrue(onMain { b.currentPosition } in 1_000..2_000)
                assertEquals(true, onMain { b.shuffleModeEnabled })
                assertFalse(onMain { b.playWhenReady })

                // Rebind (controller released and rebuilt): same occurrence, still no autoplay.
                releaseBrowser(b); browser = null
                val b2 = buildBrowser(); browser = b2
                await("Rebind shows the same queue") { b2.mediaItemCount == 2 }
                assertEquals(QueueIdentity.PREFIX + "uuid-r2",
                    onMain { b2.currentMediaItem }?.mediaId)
                assertFalse(onMain { b2.playWhenReady })
                assertEquals("Rebinding must stay offline", baselineStreams, fixture.streams.get())

                // Explicit play from the car resumes exactly where it was paused.
                onMain { b2.play() }
                await("Explicit play resumes the restored occurrence") {
                    b2.isPlaying && b2.currentPosition > 1_500
                }
                assertEquals(QueueIdentity.PREFIX + "uuid-r2",
                    onMain { b2.currentMediaItem }?.mediaId)
                onMain { b2.pause() }
            } finally {
                clearFixture(browser)
                releaseBrowser(browser)
                context.stopService(Intent(context, PlaybackService::class.java))
                if (previous != null) Graph.profiles.activate(previous)
                profile?.let { Graph.profiles.remove(it) }
                parkingProfile?.let { Graph.profiles.remove(it) }
            }
        }
    }
}
