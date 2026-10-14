package com.walktape.app

import android.content.Context
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.settings.SettingsRepository
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.core.net.Credentials
import com.walktape.app.core.profiles.CredentialVault
import com.walktape.app.core.profiles.ProfileRepository
import com.walktape.app.core.library.CoverArtStore
import com.walktape.app.core.library.LibrarySync
import com.walktape.app.core.library.PlaylistIndex
import com.walktape.app.playback.PlaybackConnection
import com.walktape.app.playback.QueueStore
import com.walktape.app.playback.AudioOutputMonitor
import com.walktape.app.playback.ContinuationSuggestion
import com.walktape.app.playback.SleepTimer
import java.io.File

@Suppress("StaticFieldLeak")
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
        database = WalkTapeDatabase.build(appContext)
        settingsRepository = SettingsRepository(appContext)
        profiles = ProfileRepository(database, CredentialVault(), appContext.filesDir)
        queues = QueueStore(database.queueDao())
    }

    fun app(): Context = appContext

    val playback: PlaybackConnection by lazy { PlaybackConnection(appContext) }
    val audioOutput = AudioOutputMonitor()
    val playbackTimer = SleepTimer()
    val suggestion = ContinuationSuggestion()

    fun client(address: ServerAddress, credentials: Credentials) = OpenSubsonicClient(address, credentials)

    fun librarySync() = LibrarySync(database.libraryDao(), clientProvider = { id -> profiles.clientFor(id) },
        playlistIndex = PlaylistIndex(database.browseDao()))

    @Synchronized
    fun coverArtStore(profileId: Long): CoverArtStore = coverStores.getOrPut(profileId) {
        CoverArtStore(File(profiles.directory(profileId), "covers"), { profiles.clientFor(profileId) })
    }
}
