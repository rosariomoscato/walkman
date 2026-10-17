package com.walktape.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walktape.app.Graph
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.db.ProfileSettingsEntity
import com.walktape.app.core.downloads.DownloadStore
import com.walktape.app.core.settings.AppSettings
import com.walktape.app.core.settings.SettingsRepository
import com.walktape.app.core.settings.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Shared with the library screen: one mutex, no parallel index refreshes (W11-T01). */
    private val syncEngine = Graph.librarySyncEngine
    val syncProgress = syncEngine.progress

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val activeProfile: StateFlow<ProfileEntity?> = Graph.profiles.profiles
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Defaults match the SPEC: Wi-Fi-only downloads, skip unavailable on, offline off. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val profileSettings: StateFlow<ProfileSettingsEntity> = activeProfile.flatMapLatest { profile ->
        if (profile == null) flowOf(ProfileSettingsEntity(profileId = 0))
        else Graph.database.downloadDao().observeSettings(profile.id)
            .map { it ?: ProfileSettingsEntity(profileId = profile.id) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileSettingsEntity(profileId = 0))

    /** Persisted index state of the active profile: last successful refresh and counts. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val libraryState: StateFlow<com.walktape.app.core.db.LibraryStateEntity?> =
        activeProfile.flatMapLatest { profile ->
            profile?.let { Graph.database.libraryDao().observeState(it.id) }
                ?: flowOf<com.walktape.app.core.db.LibraryStateEntity?>(null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Offline edits waiting for the network (pending + conflicts + rejected). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingEdits: StateFlow<Int> = activeProfile.flatMapLatest { profile ->
        profile?.let { Graph.database.downloadDao().observeUnsyncedCount(it.id) } ?: flowOf(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Conflicted or rejected operations that need an explicit resolution in the library. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val problemEdits: StateFlow<Int> = activeProfile.flatMapLatest { profile ->
        profile?.let { Graph.database.downloadDao().observeProblemCount(it.id) } ?: flowOf(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeDownloads: StateFlow<Int> = activeProfile.flatMapLatest { profile ->
        profile?.let { Graph.database.downloadDao().observeActiveCount(it.id) } ?: flowOf(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val failedDownloads: StateFlow<Int> = activeProfile.flatMapLatest { profile ->
        profile?.let { Graph.database.downloadDao().observeFailedCount(it.id) } ?: flowOf(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pausedDownloads: StateFlow<Int> = activeProfile.flatMapLatest { profile ->
        profile?.let { Graph.database.downloadDao().observePausedCount(it.id) } ?: flowOf(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** One connection verification at a time; result kept until dismissed or re-run. */
    sealed interface VerifyState {
        data object Idle : VerifyState
        data object Busy : VerifyState
        data object Ok : VerifyState
        data class Failed(val error: com.walktape.app.core.net.ApiError) : VerifyState
    }

    private val mutableVerify = MutableStateFlow<VerifyState>(VerifyState.Idle)
    val verifyState = mutableVerify.asStateFlow()

    fun verifyConnection() {
        val profileId = activeProfile.value?.id ?: return
        if (mutableVerify.value is VerifyState.Busy) return
        if (Graph.networkGate.offlineOnly(profileId)) {
            mutableVerify.value = VerifyState.Failed(com.walktape.app.core.net.ApiError.Unreachable)
            return
        }
        viewModelScope.launch {
            mutableVerify.value = VerifyState.Busy
            mutableVerify.value = when (val result = Graph.profiles.verify(profileId)) {
                is com.walktape.app.core.net.ApiResult.Failure -> VerifyState.Failed(result.error)
                is com.walktape.app.core.net.ApiResult.Success -> VerifyState.Ok
            }
        }
    }

    fun dismissVerify() { mutableVerify.value = VerifyState.Idle }

    /** Manual index refresh on the shared engine; no-op while another refresh runs. */
    fun syncIndex() {
        val profileId = activeProfile.value?.id ?: return
        if (syncProgress.value.running) return
        viewModelScope.launch { syncEngine.sync(profileId) }
    }

    fun cancelSync() { syncEngine.cancel() }

    /** Drains the offline mutation queue of the active profile (same serializer as the library). */
    fun sendPendingEdits() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch { Graph.offlineOps.drain(profileId) }
    }

    /** Pauses every download of the active profile: rows + persistent work cancellation. */
    fun pauseAllDownloads() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            Graph.downloadEngine.pauseWork(profileId)
            Graph.downloadStore.pauseAll(profileId)
        }
    }

    fun resumeAllDownloads() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            Graph.downloadStore.resumeAll(profileId)
            Graph.downloadEngine.schedule(profileId, replace = true)
        }
    }

    data class SpaceUsage(val musicBytes: Long = -1, val audioCacheBytes: Long = -1,
        val coversBytes: Long = -1, val totalBytes: Long = -1)

    private val mutableSpace = MutableStateFlow(SpaceUsage())
    val space = mutableSpace.asStateFlow()

    private val mutableCacheCleared = MutableStateFlow<Boolean?>(null)
    val cacheCleared = mutableCacheCleared.asStateFlow()

    init {
        viewModelScope.launch {
            activeProfile.collect { mutableSpace.value = SpaceUsage(); refreshSpace() }
        }
    }

    fun refreshSpace() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            val usage = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val root = Graph.profiles.directory(profileId)
                val music = DownloadStore.musicBytes(root)
                val audio = Graph.audioCache.bytesOnDisk(profileId)
                val covers = Graph.coverArtStore(profileId).diskBytes()
                SpaceUsage(music, audio, covers, music + audio + covers)
            }
            mutableSpace.value = usage
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            val dao = Graph.database.downloadDao()
            dao.upsertSettings((dao.settings(profileId)
                ?: ProfileSettingsEntity(profileId = profileId)).copy(wifiOnly = enabled))
            Graph.downloadEngine.schedule(profileId, replace = true)
        }
    }

    fun setOfflineOnly(enabled: Boolean) {
        val profileId = activeProfile.value?.id ?: return
        if (enabled) com.walktape.app.core.net.NetworkTraffic.setOffline(true)
        viewModelScope.launch {
            val dao = Graph.database.downloadDao()
            dao.upsertSettings((dao.settings(profileId)
                ?: ProfileSettingsEntity(profileId = profileId)).copy(offlineOnly = enabled))
            if (enabled) {
                androidx.work.WorkManager.getInstance(Graph.app())
                    .cancelAllWorkByTag(com.walktape.app.core.downloads.DownloadEngine.TAG)
                Graph.suggestion.reset()
            } else {
                Graph.database.profileDao().all().forEach { profile ->
                    if (Graph.database.downloadDao().activeCount(profile.id) > 0)
                        Graph.downloadEngine.schedule(profile.id, replace = true)
                    Graph.offlineOps.triggerSync(profile.id)
                }
                Graph.scrobbleSender.trigger()
            }
        }
    }

    fun setSkipUnavailable(enabled: Boolean) {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            val dao = Graph.database.downloadDao()
            dao.upsertSettings((dao.settings(profileId)
                ?: ProfileSettingsEntity(profileId = profileId)).copy(skipUnavailable = enabled))
        }
    }

    /** W09-T01: reduced streaming on metered networks; originals remain the default. */
    fun setMobileReduced(enabled: Boolean) {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            val dao = Graph.database.downloadDao()
            dao.upsertSettings((dao.settings(profileId)
                ?: ProfileSettingsEntity(profileId = profileId)).copy(mobileReduced = enabled))
        }
    }

    fun setReducedBitRate(kbps: Int) {
        val profileId = activeProfile.value?.id ?: return
        if (kbps !in com.walktape.app.playback.MIN_REDUCED_KBPS..com.walktape.app.playback.MAX_REDUCED_KBPS) return
        viewModelScope.launch {
            val dao = Graph.database.downloadDao()
            dao.upsertSettings((dao.settings(profileId)
                ?: ProfileSettingsEntity(profileId = profileId)).copy(reducedBitRate = kbps))
        }
    }

    /** W09-T01: reduced copies for new downloads; existing files keep their bytes. */
    fun setDownloadReduced(enabled: Boolean) {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            val dao = Graph.database.downloadDao()
            dao.upsertSettings((dao.settings(profileId)
                ?: ProfileSettingsEntity(profileId = profileId)).copy(downloadReduced = enabled))
        }
    }

    fun setAudioCacheLimit(bytes: Long) {
        viewModelScope.launch {
            settingsRepository.setAudioCacheLimitBytes(bytes)
            Graph.audioCache.updateLimit(bytes)
        }
    }

    fun setCoverCacheLimit(bytes: Long) {
        viewModelScope.launch {
            settingsRepository.setCoverCacheLimitBytes(bytes)
            Graph.database.profileDao().all().forEach { Graph.coverArtStore(it.id).trimTo(bytes) }
            refreshSpace()
        }
    }

    /** Clearing the audio cache is refused while any media is prepared in the session. */
    fun clearAudioCache() {
        val profileId = activeProfile.value?.id ?: return
        if (Graph.playback.state.value.hasMedia) {
            mutableCacheCleared.value = false
            return
        }
        viewModelScope.launch {
            val done = withContext(kotlinx.coroutines.Dispatchers.IO) { Graph.audioCache.clear(profileId) }
            mutableCacheCleared.value = done
            refreshSpace()
        }
    }

    fun clearCoversCache() {
        val profileId = activeProfile.value?.id ?: return
        viewModelScope.launch {
            val done = withContext(kotlinx.coroutines.Dispatchers.IO) {
                Graph.coverArtStore(profileId).clearDisk()
            }
            mutableCacheCleared.value = done
            refreshSpace()
        }
    }

    fun dismissCacheCleared() { mutableCacheCleared.value = null }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAppLockEnabled(enabled)
        }
    }

    fun setSuggestionsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSuggestionsEnabled(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHapticsEnabled(enabled)
        }
    }

    fun setKeyClicksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeyClicksEnabled(enabled)
        }
    }

    // --- W09 audio DSP ---

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCrossfadeEnabled(enabled) }
    }

    fun setCrossfadeDurationMs(ms: Int) {
        viewModelScope.launch { settingsRepository.setCrossfadeDurationMs(ms) }
    }

    fun setReplayGainMode(mode: com.walktape.app.core.settings.ReplayGainMode) {
        viewModelScope.launch { settingsRepository.setReplayGainMode(mode) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEqualizerEnabled(enabled)
            Graph.systemEffects.apply(enabled,
                Graph.systemEffects.sanitizeBandLevelsCsv(
                    settingsRepository.settings.first().equalizerBands),
                settingsRepository.settings.first().bassBoostEnabled,
                settingsRepository.settings.first().bassBoostStrength)
        }
    }

    /** Applies a band change live and persists the full set when the gesture ends. */
    fun setEqualizerBand(band: Int, levelMilliB: Int, finished: Boolean) {
        if (!finished) Graph.systemEffects.setBandLevel(band, levelMilliB)
        if (finished) viewModelScope.launch {
            settingsRepository.setEqualizerBands(Graph.systemEffects.currentBandLevelsCsv())
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBassBoostEnabled(enabled) }
    }

    fun setBassBoostStrength(strength: Int) {
        viewModelScope.launch { settingsRepository.setBassBoostStrength(strength) }
    }

    fun setTapeEffectEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTapeEffectEnabled(enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(Graph.settingsRepository)
            }
        }
    }
}
