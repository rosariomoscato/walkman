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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(Graph.settingsRepository)
            }
        }
    }
}
