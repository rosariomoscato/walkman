package com.walktape.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.walktape.app.Graph
import com.walktape.app.MainActivity
import com.walktape.app.R
import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.library.BrowseQueries
import com.walktape.app.core.library.LibraryFilter
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.playback.browse.AutoBrowseIds
import com.walktape.app.playback.browse.AutoBrowseItems
import com.walktape.app.playback.browse.CoverArtProvider
import java.util.UUID
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

/**
 * Media3 library session service: owns the single logical ExoPlayer, feeds it through the
 * local-first data source (completed downloads, then temporary cache, then authenticated
 * network) and follows the active profile — switching profile stops playback before
 * restoring the profile's saved queue without preparing or starting audio.
 *
 * W10: the same session is also browsable by Android Auto. The browse tree (library,
 * playlists, downloads, search) is served entirely from the stored Room index — browsing
 * never starts audio, never scrobbles and never issues a network request — and playback
 * requests coming from browse ids are resolved against the active profile before they can
 * reach the queue, keeping queue UUIDs, origins, order and duplicates exactly as the phone
 * UI produces them.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private val connection = MutableStateFlow<StreamProfile?>(null)
    private lateinit var mediaSession: MediaLibraryService.MediaLibrarySession
    private lateinit var persistence: PlaybackPersistence
    private lateinit var tracker: ScrobbleTracker
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Library futures are completed off the application thread: a legacy browser blocks the
     * main thread while waiting for onGetLibraryRoot, so completing on (or posting to) the
     * application thread would deadlock the service.
     */
    private val browseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val observedParents = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Completed downloads of the active profile: songId -> audio file + representation. */
    @Volatile private var completedDownloads: Map<String, Pair<File, Boolean>> = emptyMap()
    private var skipStreak = 0

    private data class DeckAudio(
        val gain: com.walktape.app.playback.dsp.ReplayGainProcessor,
        val tape: com.walktape.app.playback.dsp.TapeProcessor,
        var tags: com.walktape.app.playback.dsp.ReplayGainParser.Tags = com.walktape.app.playback.dsp.ReplayGainParser.Tags(),
    )
    private val deckAudio = java.util.IdentityHashMap<ExoPlayer, DeckAudio>()
    private lateinit var crossfade: CrossfadeCoordinator

    /** A song occurrence resolved from the stored index, or from download metadata alone. */
    private data class SongRef(val song: com.walktape.app.core.db.SongEntity?, val download: DownloadEntity?) {
        val songId: String get() = song?.id ?: download!!.songId
    }

    private suspend fun activeConnection(): StreamProfile? {
        val activeId = Graph.database.profileDao().active()?.id ?: return null
        return withTimeoutOrNull(5_000) {
            connection.filterNotNull().first { it.id == activeId }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val selectionFor: (Long) -> StreamSelection = { profileId ->
            // Pure local decision (persisted settings + connectivity state): no request,
            // no capability probe — offline-only never leaks through this path.
            streamSelection(
                Graph.networkGate.mobileReduced(profileId),
                Graph.networkGate.reducedBitRate(profileId),
                connectivity?.activeNetwork?.let { network ->
                    connectivity.getNetworkCapabilities(network)?.let { capabilities ->
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) &&
                            !capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    }
                } == true,
            )
        }
        val dataSourceFactory = LocalFirstDataSourceFactory(
            OpenSubsonicClient.streamingHttpClient(), "WalkTape",
            { connection.value },
            Graph.audioCache,
            localFile = { profileId, songId ->
                if (connection.value?.id == profileId) completedDownloads[songId]?.first else null
            },
            offlineOnly = { profileId -> Graph.networkGate.offlineOnly(profileId) },
            selectionFor = selectionFor,
            qualityMonitor = Graph.playbackQuality,
            localReducedLookup = { songId -> completedDownloads[songId]?.second == true },
        )
        Graph.audioOutput.reset()
        Graph.scrobbleSender.trigger() // deliver offline listen events as soon as possible
        // One shared audio session for both decks: the system EQ/bass chain (W09-T04)
        // processes whatever either deck plays, including during a crossfade.
        val audioSessionId = (getSystemService(AUDIO_SERVICE) as? android.media.AudioManager)
            ?.generateAudioSessionId() ?: android.media.audiofx.AudioEffect.ERROR_BAD_VALUE
        val musicAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        fun buildDeck(): ExoPlayer {
            val audio = DeckAudio(com.walktape.app.playback.dsp.ReplayGainProcessor(),
                com.walktape.app.playback.dsp.TapeProcessor())
            audio.gain.enabled = Graph.currentSettings.replayGainMode != com.walktape.app.core.settings.ReplayGainMode.OFF
            audio.tape.enabled = Graph.currentSettings.tapeEffectEnabled
            val deck = ExoPlayer.Builder(this,
                Graph.audioOutput.renderers(this, sinkHook = { builder ->
                    builder.setAudioProcessors(arrayOf(audio.gain, audio.tape))
                }, monitor = true))
                .setDeviceVolumeControlEnabled(true)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setAudioAttributes(musicAttributes, /* handleAudioFocus = */ false)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
            if (audioSessionId > 0) deck.setAudioSessionId(audioSessionId)
            deckAudio[deck] = audio
            deck.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    audio.tags = com.walktape.app.playback.dsp.ReplayGainParser.parse(
                        tracks.groups.firstOrNull { it.getTrackFormat(0).sampleMimeType?.startsWith("audio/") == true }
                            ?.getTrackFormat(0)?.metadata)
                    applyReplayGainTags(audio.gain, audio.tags)
                    if (::crossfade.isInitialized && crossfade.activeDeck === deck) publishReplayGain(audio)
                }
            })
            return deck
        }
        crossfade = CrossfadeCoordinator(scope, buildDeck(), ::buildDeck,
            enabled = { Graph.currentSettings.crossfadeEnabled },
            durationMs = { Graph.currentSettings.crossfadeDurationMs },
            transitionAllowed = { index -> Graph.playbackTimer.allowsOverlap(index) },
            report = { Graph.crossfadeStatus.value = it },
            deckReleased = { deckAudio.remove(it) })
        val player = crossfade.player
        crossfade.attachFocus(LogicalAudioFocus(this, musicAttributes,
            crossfade::onFocusCommand, crossfade::onDucking))
        val shuffle = ServiceShuffle(player)
        player.addListener(shuffle)
        Graph.playbackTimer.attach(player)
        player.addListener(automationListener)
        player.addListener(unavailableListener)
        player.addListener(qualityListener)
        tracker = ScrobbleTracker(player, Graph.listeningStore, scope,
            mainAudible = { player.isPlaying && crossfade.activeDeck.volume > 0f })
        crossfade.overlap = { deck, audible -> tracker.overlap(deck, audible) }
        crossfade.promoting = tracker::promoteOverlap
        crossfade.cancelOverlap = tracker::cancelOverlap
        crossfade.start()
        Graph.systemEffects.attach(audioSessionId)
        scope.launch {
            Graph.settingsRepository.settings.collect { settings ->
                deckAudio.values.toList().forEach { audio ->
                    audio.tape.enabled = settings.tapeEffectEnabled
                    audio.gain.enabled = settings.replayGainMode != com.walktape.app.core.settings.ReplayGainMode.OFF
                    applyReplayGainTags(audio.gain, audio.tags, settings.replayGainMode)
                }
                deckAudio[crossfade.activeDeck]?.let { publishReplayGain(it, settings.replayGainMode) }
                Graph.systemEffects.apply(settings.equalizerEnabled,
                    Graph.systemEffects.sanitizeBandLevelsCsv(settings.equalizerBands),
                    settings.bassBoostEnabled, settings.bassBoostStrength)
            }
        }
        scope.launch {
            while (true) {
                delay(60_000)
                Graph.scrobbleSender.trigger()
            }
        }
        // Keep the local-file lookup in sync with the download table of the active profile.
        scope.launch {
            connection.collectLatest { profile ->
                completedDownloads = emptyMap()
                if (profile != null) {
                    Graph.downloadStore.downloads(profile.id).collect { rows ->
                        val available = mutableMapOf<String, Pair<File, Boolean>>()
                        rows.filter { it.status == com.walktape.app.core.db.DownloadStatus.COMPLETED.name }
                            .forEach { row ->
                                val path = row.relativePath ?: return@forEach
                                val root = Graph.profiles.directory(profile.id).canonicalFile
                                runCatching {
                                    val file = File(root, path).canonicalFile
                                    if (file.path.startsWith(root.path + File.separator) && file.isFile && row.bytes > 0 && file.length() == row.bytes) {
                                        available[row.songId] = Pair(file,
                                            row.representation == com.walktape.app.core.db.DownloadRepresentation.REDUCED.name)
                                    }
                                }
                            }
                        completedDownloads = available.toMap()
                    }
                }
            }
        }
        // Apply audio cache limit changes as soon as the player is idle and empty.
        scope.launch {
            Graph.settingsRepository.settings.map { it.audioCacheLimitBytes }
                .distinctUntilChanged().collectLatest { limit ->
                    Graph.audioCache.updateLimit(limit)
                    val active = connection.value?.id
                    if (active != null && Graph.audioCache.limitPending()) {
                        awaitIdleAndRecreate(active)
                    }
                }
        }
        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()
        persistence = PlaybackPersistence(player, shuffle, Graph.queues, scope) {
            if (it == null) {
                Graph.playbackTimer.cancel(); Graph.suggestion.reset(); Graph.playbackQuality.reset()
                crossfade.resetForProfile()
                deckAudio[crossfade.activeDeck]?.let {
                    it.tags = com.walktape.app.playback.dsp.ReplayGainParser.Tags()
                    it.gain.targetGainDb = 0f
                    it.gain.tagPeak = null
                }
            }
            connection.value = it
        }
        persistence.start()
        scope.launch { connection.collectLatest { tracker.profile(it?.id) } }
        scope.launch { connection.collectLatest { notifyLibraryChanges(it) } }
        scope.launch {
            Graph.settingsRepository.settings.map { it.suggestionsEnabled }
                .distinctUntilChanged().collect {
                    if (!it) Graph.suggestion.reset()
                }
        }
    }

    // ------------------------------------------------------------------ library callbacks

    /**
     * One callback for browsing and playback. Library futures run on [browseScope] (see the
     * legacy-browser deadlock note); every path resolves ids against the active profile and
     * reads only the stored index — no implicit server request, no playback, no scrobble.
     */
    private inner class LibraryCallback : MediaLibraryService.MediaLibrarySession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            return if (controllerAllowed(controller)) super.onConnect(session, controller)
            else MediaSession.ConnectionResult.reject()
        }

        override fun onAddMediaItems(session: MediaSession,
            controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val result = SettableFuture.create<MutableList<MediaItem>>()
            val job = browseScope.launch {
                try {
                Graph.networkGate.awaitReady()
                // Binding may complete before Room/Keystore has loaded the active profile.
                val profile = activeConnection()
                if (profile == null) {
                    result.setException(IOException("Playback profile unavailable"))
                    return@launch
                }
                val resolved = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    // Media3's legacy playFromSearch/prepareFromSearch bridge carries
                    // voice queries here, rather than as ACTION_MEDIA_BUTTON intents.
                    val query = item.requestMetadata.searchQuery
                    if (query != null) {
                        val matches = searchItems(profile.id, query)
                        val target = matches.firstOrNull { AutoBrowseIds.parse(it.mediaId) is AutoBrowseIds.Node.Song }
                            ?: matches.firstOrNull { it.mediaMetadata.isPlayable == true }
                        val expanded = target?.let { expandBrowseItem(it.mediaId, profile.id) }
                        if (expanded.isNullOrEmpty()) {
                            result.setException(IOException("Search selection unavailable"))
                            return@launch
                        }
                        resolved.addAll(expanded)
                        continue
                    }
                    val uri = item.localConfiguration?.uri.toString()
                    if (uri.isNotEmpty() && SubsonicStream.songId(uri, profile.id) != null) {
                        resolved.add(item) // phone UI path: logical stream uri
                        continue
                    }
                    val expanded = expandBrowseItem(item.mediaId, profile.id) ?: run {
                        result.setException(IOException("Playback profile unavailable"))
                        return@launch
                    }
                    resolved.addAll(expanded)
                }
                if (connection.value?.id != profile.id || Graph.database.profileDao().active()?.id != profile.id || resolved.isEmpty()) {
                    result.setException(IOException("Playback selection unavailable"))
                    return@launch
                }
                val queue = resolved.map { item ->
                    assignQueueId(item, profile.id)
                }.toMutableList()
                withContext(Dispatchers.Main) {
                    if (connection.value?.id != profile.id) result.setException(IOException("Playback profile changed"))
                    else result.set(queue)
                }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    result.cancel(false)
                } catch (_: Exception) {
                    result.setException(IOException("Playback selection unavailable"))
                }
            }
            job.invokeOnCompletion { if (!result.isDone) result.cancel(false) }
            return result
        }

        override fun onPlaybackResumption(session: MediaSession,
            controller: MediaSession.ControllerInfo, playWhenReady: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val job = browseScope.launch {
                try {
                Graph.networkGate.awaitReady()
                val profile = activeConnection()
                if (profile == null) {
                    result.setException(IOException("Playback profile unavailable"))
                    return@launch
                }
                // Player state belongs to the application thread; Room suspension is main-safe.
                val resumed = withContext(Dispatchers.Main) {
                    persistence.resumeForExplicitPlay(profile.id)
                }
                if (resumed == null) result.setException(IOException("No saved queue to resume"))
                else {
                    withContext(Dispatchers.Main) {
                        // The framework re-sets the canonical list right after this future
                        // completes; reinstall the exact persisted shuffle order then.
                        val listener = object : Player.Listener {
                            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                                    mediaSession.player.removeListener(this)
                                    persistence.reapplyResumptionShuffle()
                                }
                            }
                        }
                        mediaSession.player.addListener(listener)
                    }
                    result.set(resumed)
                }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    result.cancel(false)
                } catch (_: Exception) {
                    result.setException(IOException("Playback resumption unavailable"))
                }
            }
            job.invokeOnCompletion { if (!result.isDone) result.cancel(false) }
            return result
        }

        override fun onGetLibraryRoot(session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo, params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val result = SettableFuture.create<LibraryResult<MediaItem>>()
            browseScope.launch {
                Graph.networkGate.awaitReady() // cheap once loaded; never a network call
                result.set(LibraryResult.ofItem(
                    AutoBrowseItems.rootItem(getString(R.string.app_name)),
                    // This root exposes the complete stored index, not just offline/recent
                    // or suggested media. Downloads are an explicit subtree.
                    params?.let { MediaLibraryService.LibraryParams.Builder().build() }))
            }
            return result
        }

        override fun onGetChildren(session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
            val result = SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
            browseScope.launch {
                Graph.networkGate.awaitReady()
                val window = AutoBrowseIds.window(page, pageSize)
                if (window == null) {
                    result.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    return@launch
                }
                val (limit, offset) = window
                if (observedParents.size < 2048) observedParents.add(parentId)
                val profile = activeConnection()
                val continuation = AutoBrowseIds.parse(parentId) as? AutoBrowseIds.Node.Page
                val baseParent = continuation?.parentId ?: parentId
                val baseOffset = offset.toLong() + (continuation?.offset ?: 0)
                if (baseOffset > Int.MAX_VALUE) {
                    result.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)); return@launch
                }
                val items = runCatching {
                    childrenOf(baseParent, profile, limit + 1, baseOffset.toInt())
                }.getOrElse {
                    result.set(LibraryResult.ofError(SessionError.ERROR_IO))
                    return@launch
                }
                if (profile != null && Graph.database.profileDao().active()?.id != profile.id) {
                    result.set(LibraryResult.ofError(SessionError.ERROR_INVALID_STATE))
                } else if (items == null) {
                    result.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                } else {
                    val pageItems = items.take(limit).toMutableList()
                    // Legacy Auto browsers often request ALL children with no paging options.
                    // Keep Binder payloads bounded but make every further window reachable.
                    if (pageSize > AutoBrowseIds.MAX_PAGE_SIZE && items.size > limit && profile != null && baseOffset + limit <= Int.MAX_VALUE) {
                        pageItems += AutoBrowseItems.category(AutoBrowseIds.page(profile.id, baseParent, (baseOffset + limit).toInt()),
                            getString(R.string.auto_more), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    }
                    result.set(LibraryResult.ofItemList(pageItems, params))
                }
            }
            return result
        }

        override fun onGetItem(session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo, mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val result = SettableFuture.create<LibraryResult<MediaItem>>()
            browseScope.launch {
                Graph.networkGate.awaitReady()
                val profile = activeConnection()
                val item = runCatching { itemFor(mediaId, profile) }.getOrNull()
                if (item?.mediaMetadata?.isBrowsable == true && observedParents.size < 2048) observedParents.add(mediaId)
                result.set(if (item == null || (profile != null && Graph.database.profileDao().active()?.id != profile.id)) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    else LibraryResult.ofItem(item, null))
            }
            return result
        }

        override fun onSearch(session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo, query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val result = SettableFuture.create<LibraryResult<Void>>()
            browseScope.launch {
                Graph.networkGate.awaitReady()
                if (query.isBlank()) {
                    result.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    return@launch
                }
                val profile = activeConnection()
                val count = if (profile == null) 0 else {
                    val items = runCatching { searchItems(profile.id, query) }.getOrDefault(emptyList())
                    if (Graph.database.profileDao().active()?.id != profile.id) {
                        result.set(LibraryResult.ofError(SessionError.ERROR_INVALID_STATE))
                        return@launch
                    }
                    items.size
                }
                scope.launch { // notifications belong to the application thread
                    if (connection.value?.id == profile?.id) session.notifySearchResultChanged(browser, query, count, params)
                }
                result.set(LibraryResult.ofVoid(params))
            }
            return result
        }

        override fun onGetSearchResult(session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
            val result = SettableFuture.create<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>()
            browseScope.launch {
                Graph.networkGate.awaitReady()
                val window = AutoBrowseIds.window(page, pageSize)
                if (window == null) {
                    result.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    return@launch
                }
                val (limit, offset) = window
                // Resolve this exact query against the current profile, even when a legacy
                // browser omitted onSearch. Never reuse another query/profile's results.
                val profile = activeConnection()
                val cached = runCatching {
                    profile?.let { searchItems(it.id, query) } ?: emptyList()
                }.getOrDefault(emptyList())
                if (profile != null && Graph.database.profileDao().active()?.id != profile.id) {
                    result.set(LibraryResult.ofError(SessionError.ERROR_INVALID_STATE))
                    return@launch
                }
                result.set(LibraryResult.ofItemList(
                    cached.drop(offset).take(limit), params))
            }
            return result
        }
    }

    /** Children of a browse node; null means "unknown id" (error), a list may be empty. */
    private suspend fun childrenOf(parentId: String, profile: StreamProfile?,
        limit: Int, offset: Int): List<MediaItem>? {
        if (parentId == AutoBrowseIds.ROOT) {
            if (profile == null) return listOf(AutoBrowseItems.setupItem(SETUP_ID,
                getString(R.string.auto_setup_title), getString(R.string.auto_setup_body))).drop(offset).take(limit)
            return listOf(
                AutoBrowseItems.category(AutoBrowseIds.library(profile.id),
                    getString(R.string.nav_home), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
                AutoBrowseItems.category(AutoBrowseIds.playlists(profile.id),
                    getString(R.string.auto_category_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                AutoBrowseItems.category(AutoBrowseIds.downloads(profile.id),
                    getString(R.string.auto_category_downloads), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            ).drop(offset).take(limit)
        }
        val node = AutoBrowseIds.parse(parentId) ?: return null
        if (profile == null || node.profileId != profile.id) return emptyList()
        val library = Graph.database.libraryDao()
        val browse = Graph.database.browseDao()
        val downloads = Graph.database.downloadDao()
        return when (node) {
            is AutoBrowseIds.Node.Page -> childrenOf(node.parentId, profile, limit, offset + node.offset)
            is AutoBrowseIds.Node.Library -> listOf(
                AutoBrowseItems.category(AutoBrowseIds.albums(profile.id), getString(R.string.auto_category_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                AutoBrowseItems.category(AutoBrowseIds.artists(profile.id), getString(R.string.auto_category_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
                AutoBrowseItems.category(AutoBrowseIds.songs(profile.id), getString(R.string.auto_category_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            ).drop(offset).take(limit)
            is AutoBrowseIds.Node.Albums -> library.albumsWindow(profile.id, limit, offset)
                .map { AutoBrowseItems.album(profile.id, it) }
            is AutoBrowseIds.Node.Artists -> library.artistsWindow(profile.id, limit, offset)
                .map { AutoBrowseItems.artist(profile.id, it) }
            is AutoBrowseIds.Node.Songs -> library.songsWindow(profile.id, limit, offset)
                .map { AutoBrowseItems.song(profile.id, it) }
            is AutoBrowseIds.Node.Playlists -> browse.playlistsWindow(profile.id, limit, offset)
                .map { AutoBrowseItems.playlist(profile.id, it) }
            is AutoBrowseIds.Node.Downloads -> {
                val groups = downloads.groupsOnce(profile.id)
                val children = groups.map { downloadGroupItem(it) }
                val all = if (validDownloadsWindow(profile.id, 1, 0).isNotEmpty()) children +
                    AutoBrowseItems.category(AutoBrowseIds.downloadedSongs(profile.id),
                        getString(R.string.auto_category_downloaded_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                else children
                all.drop(offset).take(limit)
            }
            is AutoBrowseIds.Node.DownloadedSongs -> validDownloadsWindow(profile.id, limit, offset)
                .map { AutoBrowseItems.downloadedSong(profile.id, it) }
            is AutoBrowseIds.Node.Album -> {
                if (library.album(profile.id, node.albumId) == null) return emptyList()
                Graph.database.libraryDao().songsInAlbums(profile.id, listOf(node.albumId))
                    .drop(offset).take(limit).map { AutoBrowseItems.song(profile.id, it) }
            }
            is AutoBrowseIds.Node.Artist -> library.artistAlbumsWindow(profile.id, node.artistId, limit, offset)
                .map { AutoBrowseItems.album(profile.id, it) }
            is AutoBrowseIds.Node.Playlist -> {
                if (browse.playlistOnce(profile.id, node.playlistId) == null) return emptyList()
                resolveSongRefs(profile.id, browse.queueEntriesWindow(profile.id, node.playlistId, limit, offset).map { it.songId })
                    .map { browseLeaf(profile.id, it) }
            }
            is AutoBrowseIds.Node.DownloadGroup -> {
                val group = downloads.group(profile.id, node.origin) ?: return emptyList()
                val ids = runCatching {
                    JSONArray(group.songIds).let { array -> (0 until array.length()).map { array.getString(it) } }
                }.getOrDefault(emptyList())
                resolveDownloadedRefs(profile.id, ids).drop(offset).take(limit)
                    .map { browseLeaf(profile.id, it) }
            }
            is AutoBrowseIds.Node.Song -> emptyList() // leaves have no children
        }
    }

    /** Single item lookup for subscribe/getItem; null for foreign or unknown ids. */
    private suspend fun itemFor(mediaId: String, profile: StreamProfile?): MediaItem? {
        if (mediaId == AutoBrowseIds.ROOT) {
            return AutoBrowseItems.rootItem(getString(R.string.app_name))
        }
        if (mediaId == SETUP_ID && profile == null) {
            return AutoBrowseItems.setupItem(SETUP_ID,
                getString(R.string.auto_setup_title), getString(R.string.auto_setup_body))
        }
        val node = AutoBrowseIds.parse(mediaId) ?: return null
        if (node.profileId != profile?.id) return null
        return when (node) {
            is AutoBrowseIds.Node.Page -> AutoBrowseItems.category(mediaId, getString(R.string.auto_more), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            is AutoBrowseIds.Node.Library -> AutoBrowseItems.category(AutoBrowseIds.library(profile.id),
                getString(R.string.nav_home), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            is AutoBrowseIds.Node.Albums -> AutoBrowseItems.category(AutoBrowseIds.albums(profile.id),
                getString(R.string.auto_category_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
            is AutoBrowseIds.Node.Artists -> AutoBrowseItems.category(AutoBrowseIds.artists(profile.id),
                getString(R.string.auto_category_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
            is AutoBrowseIds.Node.Songs -> AutoBrowseItems.category(AutoBrowseIds.songs(profile.id),
                getString(R.string.auto_category_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            is AutoBrowseIds.Node.Playlists -> AutoBrowseItems.category(AutoBrowseIds.playlists(profile.id),
                getString(R.string.auto_category_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
            is AutoBrowseIds.Node.Downloads -> AutoBrowseItems.category(AutoBrowseIds.downloads(profile.id),
                getString(R.string.auto_category_downloads), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            is AutoBrowseIds.Node.DownloadedSongs -> AutoBrowseItems.category(AutoBrowseIds.downloadedSongs(profile.id),
                getString(R.string.auto_category_downloaded_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            is AutoBrowseIds.Node.Album -> Graph.database.libraryDao().album(profile.id, node.albumId)
                ?.let { AutoBrowseItems.album(profile.id, it) }
            is AutoBrowseIds.Node.Artist -> Graph.database.libraryDao().artist(profile.id, node.artistId)
                ?.let { AutoBrowseItems.artist(profile.id, it) }
            is AutoBrowseIds.Node.Playlist -> Graph.database.browseDao().playlistOnce(profile.id, node.playlistId)
                ?.let { AutoBrowseItems.playlist(profile.id, it) }
            is AutoBrowseIds.Node.DownloadGroup -> Graph.database.downloadDao().group(profile.id, node.origin)
                ?.let { downloadGroupItem(it) }
            is AutoBrowseIds.Node.Song -> Graph.database.libraryDao().song(profile.id, node.songId)
                ?.let { AutoBrowseItems.song(profile.id, it) }
                ?: Graph.database.downloadDao().download(profile.id, node.songId)
                    ?.takeIf { validDownload(it) }
                    ?.let { AutoBrowseItems.downloadedSong(profile.id, it) }
        }
    }

    /**
     * Resolves a browse id to queue-ready media with logical stream uris (W10-T02). Songs
     * resolve to one occurrence; albums/artists expand in their real listening order;
     * playlists and download groups expand their full snapshot, duplicates included, with
     * the playlist/group name as queue origin — exactly what the phone UI does.
     */
    private suspend fun expandBrowseItem(mediaId: String, profileId: Long): List<MediaItem>? {
        val node = AutoBrowseIds.parse(mediaId) ?: return null
        if (node.profileId != profileId) return null
        val library = Graph.database.libraryDao()
        val browse = Graph.database.browseDao()
        val downloads = Graph.database.downloadDao()
        return when (node) {
            is AutoBrowseIds.Node.Song ->
                resolveSongRefs(profileId, listOf(node.songId)).map { queueReady(profileId, it, null) }
            is AutoBrowseIds.Node.Album ->
                library.album(profileId, node.albumId)?.let { album ->
                    val songs = library.songsInAlbums(profileId, listOf(node.albumId))
                    if (songs.size != album.songCount) null else songs.map { songMediaItem(it) }
                }
            is AutoBrowseIds.Node.Artist ->
                library.artistSongs(profileId, node.artistId).map { songMediaItem(it) }
            is AutoBrowseIds.Node.Playlist -> {
                val playlist = browse.playlistOnce(profileId, node.playlistId) ?: return null
                val entries = browse.queueEntries(profileId, node.playlistId)
                val refs = resolveSongRefs(profileId, entries.map { it.songId })
                if (refs.size != playlist.songCount || entries.size != playlist.songCount) return null
                refs.map { queueReady(profileId, it, playlist.name) }
            }
            is AutoBrowseIds.Node.DownloadGroup -> {
                val group = downloads.group(profileId, node.origin) ?: return null
                val ids = runCatching {
                    JSONArray(group.songIds).let { array -> (0 until array.length()).map { array.getString(it) } }
                }.getOrDefault(emptyList())
                if (ids.isEmpty()) return emptyList()
                val refs = resolveDownloadedRefs(profileId, ids)
                if (refs.size != ids.size) return null
                refs.map { queueReady(profileId, it, group.name) }
            }
            is AutoBrowseIds.Node.DownloadedSongs ->
                downloads.completedAll(profileId).filter { validDownload(it) }.map { queueReady(profileId, SongRef(null, it), null) }
            // Categories and the downloads container are not playable leaves.
            is AutoBrowseIds.Node.Page, is AutoBrowseIds.Node.Library, is AutoBrowseIds.Node.Albums, is AutoBrowseIds.Node.Artists, is AutoBrowseIds.Node.Songs,
            is AutoBrowseIds.Node.Playlists, is AutoBrowseIds.Node.Downloads -> null
        }
    }

    /**
     * Occurrences in order, duplicates preserved: the stored song index wins; downloaded
     * metadata keeps playlist positions whose song already left the server; anything else
     * is dropped — no placeholder or made-up entry ever reaches the queue.
     */
    private suspend fun resolveSongRefs(profileId: Long, songIds: List<String>): List<SongRef> {
        val distinct = songIds.distinct()
        val songs = HashMap<String, com.walktape.app.core.db.SongEntity>()
        distinct.chunked(500).forEach { chunk ->
            Graph.database.libraryDao().songsByIds(profileId, chunk).forEach { songs[it.id] = it }
        }
        val downloads = HashMap<String, DownloadEntity>()
        distinct.filter { it !in songs }.chunked(500).forEach { chunk ->
            Graph.database.downloadDao().completedFor(profileId, chunk).filter { validDownload(it) }.forEach { downloads[it.songId] = it }
        }
        return songIds.mapNotNull { id ->
            songs[id]?.let { SongRef(it, null) } ?: downloads[id]?.let { SongRef(null, it) }
        }
    }

    private fun browseLeaf(profileId: Long, ref: SongRef): MediaItem = ref.song
        ?.let { AutoBrowseItems.song(profileId, it) }
        ?: AutoBrowseItems.downloadedSong(profileId, ref.download!!)

    private fun validDownload(row: DownloadEntity): Boolean = runCatching {
        if (row.status != com.walktape.app.core.db.DownloadStatus.COMPLETED.name || row.bytes <= 0) return false
        val root = Graph.profiles.directory(row.profileId).canonicalFile
        val file = File(root, row.relativePath ?: return false).canonicalFile
        file.path.startsWith(root.path + File.separator) && file.isFile && file.length() == row.bytes
    }.getOrDefault(false)

    /** File truth without materialising the entire download library for each browse page. */
    private suspend fun validDownloadsWindow(profileId: Long, limit: Int, offset: Int): List<DownloadEntity> {
        val result = ArrayList<DownloadEntity>()
        var rawOffset = 0
        var validSeen = 0
        while (result.size < limit) {
            val rows = Graph.database.downloadDao().completedWindow(profileId, 200, rawOffset)
            for (row in rows) {
                if (validDownload(row) && validSeen++ >= offset) result.add(row)
                if (result.size == limit) break
            }
            if (rows.size < 200) break
            rawOffset += rows.size
        }
        return result
    }

    private suspend fun resolveDownloadedRefs(profileId: Long, ids: List<String>): List<SongRef> {
        val rows = ids.distinct().chunked(500).flatMap {
            Graph.database.downloadDao().completedFor(profileId, it)
        }.filter { validDownload(it) }.associateBy { it.songId }
        return ids.mapNotNull { rows[it]?.let { row -> SongRef(null, row) } }
    }

    private suspend fun downloadGroupItem(group: com.walktape.app.core.db.DownloadGroupEntity): MediaItem {
        val ids = runCatching { JSONArray(group.songIds).let { array ->
            (0 until array.length()).map { array.getString(it) }
        } }.getOrDefault(emptyList())
        val available = resolveDownloadedRefs(group.profileId, ids)
        val status = getString(R.string.auto_download_status, available.size, ids.size) +
            if (group.serverMissing) " · " + getString(R.string.auto_snapshot_missing) else ""
        return AutoBrowseItems.downloadGroup(group.profileId, group.origin, group.name,
            available.firstOrNull()?.download?.coverArt, status,
            ids.isNotEmpty() && available.size == ids.size)
    }

    private fun queueReady(profileId: Long, ref: SongRef, origin: String?): MediaItem = ref.song
        ?.let { songMediaItem(it, origin) }
        ?: downloadMediaItem(profileId, ref.download!!, origin)

    /** Queue identity (W05-T03): fresh per-occurrence UUID plus the song id in the extras. */
    private fun assignQueueId(item: MediaItem, profileId: Long): MediaItem {
        val songId = SubsonicStream.songId(item.localConfiguration!!.uri.toString(), profileId)!!
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            putString(QueueIdentity.SONG, songId)
        }
        return item.buildUpon().setMediaId(QueueIdentity.PREFIX + UUID.randomUUID())
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    /** Queue item built from download metadata alone (song no longer in the index). */
    private fun downloadMediaItem(profileId: Long, row: DownloadEntity, origin: String?): MediaItem {
        val extras = Bundle().apply {
            putString(QueueIdentity.SONG, row.songId)
            row.albumId?.let { putString(SleepTimer.ALBUM_ID, it) }
            row.coverArt?.let { putString(QueueIdentity.COVER_ART, it) }
            origin?.let { putString(QueueIdentity.ORIGIN, it) }
        }
        return MediaItem.Builder().setMediaId(row.songId)
            .setUri(Uri.parse(SubsonicStream.uri(profileId, row.songId)))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(row.title)
                .setArtist(row.artist?.takeIf { it.isNotBlank() })
                .setAlbumTitle(row.album?.takeIf { it.isNotBlank() })
                .setDurationMs(row.duration * 1000L)
                .setExtras(extras).build())
            .build()
    }

    /** Unified local search over the stored index: artists, albums, playlists and songs. */
    private suspend fun searchItems(profileId: Long, query: String): List<MediaItem> {
        val rows = Graph.database.browseDao().searchWindow(
            BrowseQueries.searchWindow(profileId, LibraryFilter(query = query),
                SEARCH_MAX_RESULTS, 0))
        return rows.mapNotNull { row ->
            when (row.kind) {
                "ALBUM" -> Graph.database.libraryDao().album(profileId, row.id)
                    ?.let { AutoBrowseItems.album(profileId, it) }
                "ARTIST" -> Graph.database.libraryDao().artist(profileId, row.id)
                    ?.let { AutoBrowseItems.artist(profileId, it) }
                "PLAYLIST" -> Graph.database.browseDao().playlistOnce(profileId, row.id)
                    ?.let { AutoBrowseItems.playlist(profileId, it) }
                "SONG" -> Graph.database.libraryDao().song(profileId, row.id)
                    ?.let { AutoBrowseItems.song(profileId, it) }
                else -> null
            }
        }
    }

    /**
     * Pushes library updates to subscribed browsers (W10-T02): coarse signatures only, so
     * transfer progress checkpoints do not spam the car UI. Runs on the main scope.
     */
    private suspend fun notifyLibraryChanges(profile: StreamProfile?) = coroutineScope {
        if (profile == null) return@coroutineScope
        fun notify(parentId: String) {
            if (::mediaSession.isInitialized) {
                mediaSession.notifyChildrenChanged(parentId, Int.MAX_VALUE, null)
            }
        }
        notify(AutoBrowseIds.ROOT)
        launch {
            // Includes edits that keep row counts unchanged (rename/reorder, downloaded
            // snapshot refresh). Only Media3's actual subscribers receive notifications.
            Graph.database.invalidationTracker.createFlow("album", "artist", "song", "playlist",
                "playlist_entry", "download", "download_group").conflate().collect {
                observedParents.filter { AutoBrowseIds.parse(it)?.profileId == profile.id }
                    .forEach { notify(it) }
            }
        }
        launch {
            Graph.database.downloadDao().downloads(profile.id)
                .map { rows -> rows.size to rows.count { it.status == com.walktape.app.core.db.DownloadStatus.COMPLETED.name } }
                .distinctUntilChanged().collect {
                    notify(AutoBrowseIds.downloads(profile.id))
                    notify(AutoBrowseIds.downloadedSongs(profile.id))
                }
        }
        launch {
            Graph.database.downloadDao().groups(profile.id)
                .map { groups -> groups.map { it.origin to it.songIds } }
                .distinctUntilChanged().collect { notify(AutoBrowseIds.downloads(profile.id)) }
        }
        launch {
            Graph.database.libraryDao().observeState(profile.id)
                .map { listOf(it?.albumCount, it?.artistCount, it?.songCount) }
                .distinctUntilChanged().collect {
                    notify(AutoBrowseIds.albums(profile.id)); notify(AutoBrowseIds.artists(profile.id))
                    notify(AutoBrowseIds.songs(profile.id))
                }
        }
        launch {
            Graph.database.browseDao().playlistFingerprint(profile.id)
                .distinctUntilChanged().collect { notify(AutoBrowseIds.playlists(profile.id)) }
        }
    }

    /** Recreates the profile cache when the player holds no prepared media. */
    private suspend fun awaitIdleAndRecreate(profileId: Long) {
        if (!::mediaSession.isInitialized) return
        val player = mediaSession.player
        var waited = 0
        while (player.playbackState != Player.STATE_IDLE && waited < 30_000) {
            delay(1_000)
            waited += 1_000
        }
        if (player.playbackState == Player.STATE_IDLE) Graph.audioCache.recreate(profileId)
    }

    /**
     * Unavailable songs during offline listening (W08-T04): when the failing song has no
     * local copy and the error is network-side, skip to the next occurrence if the profile
     * allows it — at most one full round of the queue, then the error surfaces. The skip is
     * signalled through Graph.offlineSkips for the honest UI notice.
     */
    private val unavailableListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val player = mediaSession.player
            val profile = connection.value ?: return
            if (!Graph.networkGate.skipUnavailable(profile.id)) return
            val item = player.currentMediaItem ?: return
            val songId = item.mediaMetadata.extras?.getString(QueueIdentity.SONG) ?: return
            if (completedDownloads.containsKey(songId)) return // local copy failed: real error
            val networkCause = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                -> true
                else -> error.cause is IOException
            }
            if (!networkCause) return
            if (skipStreak >= player.mediaItemCount) return // a full loop: stop skipping
            skipStreak++
            if (player.hasNextMediaItem()) {
                Graph.lastSkippedTitle.value = item.mediaMetadata.title?.toString()
                Graph.offlineSkips.value = Graph.offlineSkips.value + 1
                player.seekToNextMediaItem()
                player.prepare()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) skipStreak = 0
        }
    }

    /** Queue-end automation: the suggestion is fetched only for the active profile's server. */
    private val automationListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) Graph.suggestion.reset()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = mediaSession.player
            if (playbackState == Player.STATE_ENDED && !Graph.networkGate.offlineOnly(connection.value?.id ?: -1)) {
                val item = player.currentMediaItem
                val profile = connection.value
                Graph.suggestion.evaluate(profile?.client, profile?.id ?: -1L,
                    item?.mediaMetadata?.extras?.getString(QueueIdentity.SONG),
                    item?.mediaMetadata?.title?.toString())
            } else {
                Graph.suggestion.reset()
            }
        }
    }

    /** Honest quality label (W09-T01): decoder-side mime/bitrate of the song actually playing. */
    private val qualityListener = object : Player.Listener {
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            val item = mediaSession.player.currentMediaItem ?: return
            val songId = item.mediaMetadata.extras?.getString(QueueIdentity.SONG) ?: return
            var mime: String? = null
            var bitrate: Int? = null
            for (group in tracks.groups) {
                val format = group.getTrackFormat(0)
                if (format.sampleMimeType?.startsWith("audio/") == true) {
                    mime = format.sampleMimeType
                    bitrate = format.bitrate.takeIf { it > 0 }
                    break
                }
            }
            Graph.playbackQuality.observed(songId, mime, bitrate)
            deckAudio[crossfade.activeDeck]?.let { publishReplayGain(it) }
        }
    }

    private fun applyReplayGainTags(processor: com.walktape.app.playback.dsp.ReplayGainProcessor,
        tags: com.walktape.app.playback.dsp.ReplayGainParser.Tags,
        mode: com.walktape.app.core.settings.ReplayGainMode = Graph.currentSettings.replayGainMode) {
        processor.targetGainDb = com.walktape.app.playback.dsp.ReplayGainParser.selectGainDb(tags, mode)
        processor.tagPeak = com.walktape.app.playback.dsp.ReplayGainParser.selectPeak(tags, mode)
    }

    private fun publishReplayGain(audio: DeckAudio,
        mode: com.walktape.app.core.settings.ReplayGainMode = Graph.currentSettings.replayGainMode) {
            val tags = audio.tags
            val present = when (mode) {
                com.walktape.app.core.settings.ReplayGainMode.OFF -> false
                com.walktape.app.core.settings.ReplayGainMode.TRACK -> tags.trackGainDb != null
                com.walktape.app.core.settings.ReplayGainMode.ALBUM -> tags.albumGainDb != null || tags.trackGainDb != null
            }
            Graph.replayGainInfo.value = com.walktape.app.playback.dsp.ReplayGainParser.Info(
                mode, present, audio.gain.targetGainDb)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? {
        // Same UID, framework-trusted controllers (notification/media button) and the Android
        // Auto projection app may use the session. Anything else — including unverified
        // third-party browsers — gets nothing.
        // MediaSessionService.onBind uses this documented placeholder before it knows the
        // legacy client's identity. Real callers are still checked in onConnect above.
        val legacyBinding = controllerInfo.uid < 0 &&
            controllerInfo.packageName == MediaSession.ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME
        return if (legacyBinding || controllerAllowed(controllerInfo)) mediaSession else null
    }

    private fun controllerAllowed(controller: MediaSession.ControllerInfo): Boolean {
        val verifiedAuto = controller.packageName in CoverArtProvider.TRUSTED_BROWSER_PACKAGES &&
            packageManager.getPackagesForUid(controller.uid)?.contains(controller.packageName) == true
        return controller.uid == applicationInfo.uid || controller.isTrusted || verifiedAuto
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession.player
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        tracker.close()
        persistence.close()
        scope.cancel()
        browseScope.cancel()
        crossfade.release()
        Graph.systemEffects.release()
        mediaSession.run {
            player.removeListener(automationListener)
            player.removeListener(unavailableListener)
            player.removeListener(qualityListener)
            Graph.playbackTimer.detach()
            Graph.suggestion.reset()
            player.release()
            release()
        }
        Graph.audioOutput.reset()
        Graph.scrobbleSender.trigger() // last chance to flush pending events while alive
        super.onDestroy()
    }

    companion object {
        private const val SETUP_ID = "wtb-setup"
        private const val SEARCH_MAX_RESULTS = 500
    }
}
