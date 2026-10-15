package com.walktape.app

import android.content.Context
import androidx.room.withTransaction
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.settings.AppSettings
import com.walktape.app.core.settings.ProfileNetworkGate
import com.walktape.app.core.settings.SettingsRepository
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.core.net.Credentials
import com.walktape.app.core.profiles.CredentialVault
import com.walktape.app.core.profiles.ProfileRepository
import com.walktape.app.core.library.CoverArtStore
import com.walktape.app.core.library.LibrarySync
import com.walktape.app.core.library.LyricsRepository
import com.walktape.app.core.library.PlaylistIndex
import com.walktape.app.core.library.PlaylistRepository
import com.walktape.app.core.library.StarRepository
import com.walktape.app.core.downloads.DownloadStore
import com.walktape.app.core.downloads.DownloadEngine
import com.walktape.app.playback.PlaybackConnection
import com.walktape.app.playback.QueueStore
import com.walktape.app.playback.AudioOutputMonitor
import com.walktape.app.playback.ContinuationSuggestion
import com.walktape.app.playback.ScrobbleSender
import com.walktape.app.playback.ListeningStore
import com.walktape.app.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

@Suppress("StaticFieldLeak")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object Graph {
    private val coverStores = mutableMapOf<Long, CoverArtStore>()
    private lateinit var appContext: Context
    lateinit var database: WalkTapeDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var profiles: ProfileRepository
        private set
    lateinit var queues: QueueStore
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        com.walktape.app.core.net.NetworkTraffic.setOffline(true)
        database = WalkTapeDatabase.build(appContext)
        settingsRepository = SettingsRepository(appContext)
        profiles = ProfileRepository(database, CredentialVault(), appContext.filesDir)
        queues = QueueStore(database.queueDao())
        networkGate // eagerly load the fail-closed policy before any worker/client is used
        appScope.launch { settingsRepository.settings.collect { currentSettings = it } }
        appScope.launch {
            // Resume queues that lost their work (defensive; WorkManager persists across reboots)
            database.profileDao().all().forEach { profile ->
                if (database.downloadDao().activeCount(profile.id) > 0) {
                    downloadEngine.schedule(profile.id)
                }
                offlineOps.triggerSync(profile.id)
            }
        }
        appScope.launch {
            // Leaving offline-only mode drains the mutation queue as soon as allowed.
            database.downloadDao().observeAllSettings().collect { rows ->
                rows.filter { !it.offlineOnly }.forEach { offlineOps.triggerSync(it.profileId) }
            }
        }
    }

    fun app(): Context = appContext

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Latest device settings for non-suspend providers (cover/cache limits). */
    @Volatile
    var currentSettings: AppSettings = AppSettings()
        private set

    val networkGate: ProfileNetworkGate by lazy { ProfileNetworkGate(database.downloadDao(), appScope,
        database.profileDao().observeAll().map { rows -> rows.firstOrNull { it.isActive }?.id }) }

    val downloadStore: DownloadStore by lazy {
        DownloadStore(database.downloadDao(), database.libraryDao(), database.browseDao(),
            profileDirectory = { id -> profiles.directory(id) },
            clientProvider = { id -> profiles.clientFor(id) },
            commitLocal = { block -> database.withTransaction { block() } })
    }
    val downloadEngine: DownloadEngine by lazy { DownloadEngine(appContext) }

    val playback: PlaybackConnection by lazy { PlaybackConnection(appContext) }
    val audioOutput = AudioOutputMonitor()
    val playbackTimer = SleepTimer()
    val suggestion = ContinuationSuggestion()

    /** Per-profile temporary audio cache (Media3 SimpleCache, 1 GB default). */
    val audioCache: com.walktape.app.playback.AudioCacheManager by lazy {
        com.walktape.app.playback.AudioCacheManager(appContext) { id -> profiles.directory(id) }
    }

    /** Counter of unavailable songs skipped during offline playback; observed by the player UI. */
    val offlineSkips = MutableStateFlow(0)
    /** Title of the last skipped unavailable song, for the honest UI notice. */
    val lastSkippedTitle = MutableStateFlow<String?>(null)

    /** User label for the active profile's current mixed queue; persisted with the queue itself. */
    val queueNaming = MutableStateFlow<String?>(null)
    val scrobbleSender: ScrobbleSender by lazy {
        ScrobbleSender(database.scrobbleDao(), { id -> profiles.clientFor(id) },
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
            offlineOnly = { id -> networkGate.offlineOnly(id) })
    }
    val listeningStore: ListeningStore by lazy { ListeningStore(database.scrobbleDao()) { scrobbleSender.trigger() } }

    fun client(address: ServerAddress, credentials: Credentials) = OpenSubsonicClient(address, credentials)

    fun playlistIndex() = PlaylistIndex(database.browseDao()) { id ->
        database.downloadDao().opsOnce(id).filter { it.kind == com.walktape.app.sync.OfflineOps.KIND_DELETE }
            .map { offlineOps.conflictPlaylistId(it) }.toSet()
    }

    fun librarySync() = LibrarySync(database.libraryDao(), clientProvider = { id ->
        // Do not let an index refresh replace optimistic favorites with older server
        // values while an edit, rejection or conflict is still awaiting resolution.
        if (database.downloadDao().opsOnce(id).isEmpty()) profiles.clientFor(id) else null
    },
        playlistIndex = playlistIndex(),
        offlineOnly = { id -> networkGate.offlineOnly(id) },
        afterSync = { profileId ->
            database.downloadDao().reconcileServerPresence(profileId)
            // Downloaded playlists update automatically on unmetered networks only (SPEC).
            if (isUnmetered() && !networkGate.offlineOnly(profileId) &&
                database.downloadDao().downloadedPlaylistOrigins(profileId).isNotEmpty()) {
                downloadStore.reconcileDownloadedPlaylists(profileId)
                downloadEngine.schedule(profileId)
            }
        })

    private fun isUnmetered(): Boolean {
        val manager = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun playlistRepository() = playlists

    val offlineOps: com.walktape.app.sync.OfflineOps by lazy {
        com.walktape.app.sync.OfflineOps(
            database.downloadDao(), database.browseDao(), database.libraryDao(),
            clientProvider = { id -> profiles.clientFor(id) },
            usernameProvider = { id -> database.profileDao().get(id)?.username },
            offlineOnly = { id -> networkGate.offlineOnly(id) },
            scope = appScope,
            schedule = { id -> com.walktape.app.sync.OfflineSyncWorker.schedule(appContext, id) },
        )
    }

    private val favorites by lazy { StarRepository(database.libraryDao(),
        clientProvider = { id -> profiles.clientFor(id) },
        ops = offlineOps,
        commitLocal = { block -> offlineOps.serializeEdit { database.withTransaction { block() } } },
        offlineOnly = { id -> networkGate.offlineOnly(id) }) }
    fun starRepository() = favorites

    private val playlists by lazy { PlaylistRepository(database.browseDao(),
        clientProvider = { id -> profiles.clientFor(id) },
        ownerProvider = { id -> database.profileDao().get(id)?.username },
        libraryDao = database.libraryDao(),
        ops = offlineOps,
        commitLocal = { block -> offlineOps.serializeEdit { database.withTransaction { block() } } },
        offlineOnly = { id -> networkGate.offlineOnly(id) })
    }

    fun lyricsRepository() = LyricsRepository(database.lyricsDao(),
        clientProvider = { id -> profiles.clientFor(id) },
        offlineOnly = { id -> networkGate.offlineOnly(id) })

    @Synchronized
    fun coverArtStore(profileId: Long): CoverArtStore = coverStores.getOrPut(profileId) {
        CoverArtStore(
            File(profiles.directory(profileId), "covers"),
            { profiles.clientFor(profileId) },
            maxBytesProvider = { currentSettings.coverCacheLimitBytes },
            allowNetwork = { !networkGate.offlineOnly(profileId) },
        )
    }
}
