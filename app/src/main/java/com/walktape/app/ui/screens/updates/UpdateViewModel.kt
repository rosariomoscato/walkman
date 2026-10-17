package com.walktape.app.ui.screens.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.walktape.app.Graph
import com.walktape.app.core.settings.AppSettings
import com.walktape.app.core.updates.ReleaseInfo
import com.walktape.app.core.updates.UpdateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** W11-T04: manual update flow — check, notes, APK download, verification, install hand-off. */
class UpdateViewModel : ViewModel() {

    private val manager = Graph.updateManager

    val currentVersion: String = manager.currentVersionName

    val settings: StateFlow<AppSettings> = Graph.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    sealed interface CheckState {
        data object Idle : CheckState
        data object Busy : CheckState
        data class Done(val outcome: UpdateManager.CheckOutcome) : CheckState
    }

    private val mutableCheck = MutableStateFlow<CheckState>(CheckState.Idle)
    val check: StateFlow<CheckState> = mutableCheck.asStateFlow()

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Running(val bytes: Long, val total: Long) : DownloadState
        data class Verified(val file: File, val release: ReleaseInfo) : DownloadState
        /** Verification refusal; the argument selects the honest reason string. */
        data class Rejected(val reason: Rejection) : DownloadState
        data object Failed : DownloadState
        data object Cancelled : DownloadState
        enum class Rejection { NOT_WALKTAPE, NOT_NEWER, SIGNATURE, CORRUPT }
    }

    private val mutableDownload = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = mutableDownload.asStateFlow()

    /** True after the user was sent to the unknown-sources settings page. */
    private val mutableInstallPermission = MutableStateFlow(false)
    val installPermission = mutableInstallPermission.asStateFlow()

    private var downloadJob: Job? = null

    fun checkNow() {
        if (mutableCheck.value is CheckState.Busy) return
        viewModelScope.launch {
            mutableCheck.value = CheckState.Busy
            mutableCheck.value = CheckState.Done(manager.check())
        }
    }

    fun downloadApk(release: ReleaseInfo) {
        val asset = com.walktape.app.core.updates.AssetSelector.selectApk(release.assets)
        if (asset == null) {
            mutableDownload.value = DownloadState.Failed
            return
        }
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            mutableDownload.value = DownloadState.Running(0, asset.sizeBytes)
            when (val outcome = manager.download(asset, release.tagName) { bytes, total ->
                mutableDownload.value = DownloadState.Running(bytes, total)
            }) {
                is UpdateManager.DownloadOutcome.Done -> {
                    when (manager.verify(outcome.file)) {
                        com.walktape.app.core.updates.ApkVerifier.Outcome.Ok ->
                            mutableDownload.value = DownloadState.Verified(outcome.file, release)
                        com.walktape.app.core.updates.ApkVerifier.Outcome.NotWalkTape -> {
                            outcome.file.delete()
                            mutableDownload.value = DownloadState.Rejected(DownloadState.Rejection.NOT_WALKTAPE)
                        }
                        com.walktape.app.core.updates.ApkVerifier.Outcome.NotNewer -> {
                            outcome.file.delete()
                            mutableDownload.value = DownloadState.Rejected(DownloadState.Rejection.NOT_NEWER)
                        }
                        com.walktape.app.core.updates.ApkVerifier.Outcome.SignatureMismatch -> {
                            outcome.file.delete()
                            mutableDownload.value = DownloadState.Rejected(DownloadState.Rejection.SIGNATURE)
                        }
                        com.walktape.app.core.updates.ApkVerifier.Outcome.Corrupt -> {
                            outcome.file.delete()
                            mutableDownload.value = DownloadState.Rejected(DownloadState.Rejection.CORRUPT)
                        }
                    }
                }
                UpdateManager.DownloadOutcome.Cancelled -> mutableDownload.value = DownloadState.Cancelled
                UpdateManager.DownloadOutcome.Failed -> mutableDownload.value = DownloadState.Failed
                UpdateManager.DownloadOutcome.BlockedOffline -> mutableDownload.value = DownloadState.Failed
            }
        }
    }

    fun cancelDownload() { manager.cancelDownload() }

    /** Hands the verified APK to Android's installer (user-confirmed there). */
    fun install(file: File) {
        if (manager.canRequestInstalls()) {
            mutableInstallPermission.value = false
            Graph.app().startActivity(manager.installIntent(file))
        } else {
            mutableInstallPermission.value = true
            Graph.app().startActivity(manager.unknownSourcesIntent())
        }
    }

    fun dismissInstallPermission() { mutableInstallPermission.value = false }

    fun dismissDownload() { mutableDownload.value = DownloadState.Idle }

    fun setAutoCheck(enabled: Boolean) {
        viewModelScope.launch { Graph.settingsRepository.setUpdateCheckEnabled(enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { UpdateViewModel() }
        }
    }
}
