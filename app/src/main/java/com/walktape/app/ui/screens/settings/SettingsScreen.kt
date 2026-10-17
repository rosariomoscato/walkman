package com.walktape.app.ui.screens.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walktape.app.Graph
import com.walktape.app.R
import com.walktape.app.core.settings.ThemeMode
import com.walktape.app.ui.components.SectionHeader
import com.walktape.app.ui.nav.WalkTapeDestination

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenUpdates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        SectionHeader(text = stringResource(R.string.settings_appearance))

        SettingLabel(text = stringResource(R.string.settings_theme), paddingStart = 16)
        ThemeChooser(
            selected = settings.themeMode,
            onSelect = viewModel::setThemeMode
        )

        SettingLabel(text = stringResource(R.string.settings_language), paddingStart = 16)
        LanguageChooser(context = context)

        SectionHeader(text = stringResource(R.string.settings_system))

        TextButton(onClick = onOpenProfiles, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.profiles_title))
        }

        AppLockSetting(
            enabled = settings.appLockEnabled,
            onCheckedChange = viewModel::setAppLockEnabled
        )

        SuggestionSetting(
            enabled = settings.suggestionsEnabled,
            onCheckedChange = viewModel::setSuggestionsEnabled
        )

        FeedbackSetting(
            title = stringResource(R.string.settings_haptics),
            help = stringResource(R.string.settings_haptics_help),
            enabled = settings.hapticsEnabled,
            onCheckedChange = viewModel::setHapticsEnabled
        )

        FeedbackSetting(
            title = stringResource(R.string.settings_key_clicks),
            help = stringResource(R.string.settings_key_clicks_help),
            enabled = settings.keyClicksEnabled,
            onCheckedChange = viewModel::setKeyClicksEnabled
        )

        SystemStatusSection(viewModel)

        DownloadsStorageSection(viewModel)

        SoundSection(viewModel)

        EntryRow(
            label = stringResource(R.string.system_backup_entry),
            icon = Icons.Filled.Android,
            onClick = onOpenBackup
        )

        EntryRow(
            label = stringResource(R.string.system_updates_entry),
            icon = Icons.Filled.SystemUpdate,
            onClick = onOpenUpdates
        )

        EntryRow(
            label = stringResource(R.string.settings_diagnostics),
            icon = Icons.Filled.BugReport,
            onClick = onOpenDiagnostics
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        Text(
            text = stringResource(R.string.settings_version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun SettingLabel(text: String, paddingStart: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = paddingStart.dp)
    )
}

@Composable
private fun EntryRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** Honest per-profile status: connection, index, pending edits and downloads (W11-T01). */
@Composable
private fun SystemStatusSection(viewModel: SettingsViewModel) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val progress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val pending by viewModel.pendingEdits.collectAsStateWithLifecycle()
    val problems by viewModel.problemEdits.collectAsStateWithLifecycle()
    val active by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val failed by viewModel.failedDownloads.collectAsStateWithLifecycle()
    val paused by viewModel.pausedDownloads.collectAsStateWithLifecycle()
    val verify by viewModel.verifyState.collectAsStateWithLifecycle()
    val verifyState = verify
    val dateFormatter = remember {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
    }

    SectionHeader(text = stringResource(R.string.system_status_title))
    val currentProfile = profile ?: run {
        Text(stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp))
        return
    }

    Text(currentProfile.name,
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
    when (verifyState) {
        is SettingsViewModel.VerifyState.Busy ->
            Text(stringResource(R.string.system_connection_running),
                modifier = Modifier.padding(horizontal = 16.dp))
        is SettingsViewModel.VerifyState.Ok -> {
            Text(stringResource(R.string.system_connection_ok),
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = viewModel::dismissVerify, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.action_close))
            }
        }
        is SettingsViewModel.VerifyState.Failed -> {
            Text(stringResource(apiErrorLabel(verifyState.error)),
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = viewModel::dismissVerify, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.action_close))
            }
        }
        SettingsViewModel.VerifyState.Idle -> Unit
    }
    if (verifyState !is SettingsViewModel.VerifyState.Busy) {
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = viewModel::verifyConnection) {
                Text(stringResource(R.string.system_connection_verify))
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    Text(stringResource(R.string.system_index_title),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
    val state = libraryState
    when {
        state == null -> Text(stringResource(R.string.system_index_never),
            modifier = Modifier.padding(horizontal = 16.dp))
        state.lastSyncOk -> Text(stringResource(R.string.system_index_last_ok,
            dateFormatter.format(java.util.Date(state.lastSyncAt)), state.songCount),
            modifier = Modifier.padding(horizontal = 16.dp))
        else -> Text(stringResource(R.string.system_index_last_failed,
            dateFormatter.format(java.util.Date(state.lastSyncAt))),
            modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (progress.running) {
        Text(stringResource(R.string.system_index_running, progress.processed, progress.total),
            modifier = Modifier.padding(horizontal = 16.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { if (progress.total > 0) progress.processed.toFloat() / progress.total else 0f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    } else {
        progress.error?.let {
            Text(stringResource(apiErrorLabel(it)), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp)) {
        if (progress.running) {
            TextButton(onClick = viewModel::cancelSync) {
                Text(stringResource(R.string.system_index_cancel))
            }
        } else {
            TextButton(onClick = viewModel::syncIndex) {
                Text(stringResource(R.string.system_index_refresh))
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    Text(stringResource(R.string.system_sync_title),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
    if (problems > 0) {
        Text(stringResource(R.string.system_sync_problems, problems),
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (pending > 0) {
        Text(stringResource(R.string.system_sync_pending, pending),
            modifier = Modifier.padding(horizontal = 16.dp))
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp)) {
            TextButton(onClick = viewModel::sendPendingEdits) {
                Text(stringResource(R.string.system_sync_send))
            }
        }
    } else if (problems == 0) {
        Text(stringResource(R.string.system_sync_none), modifier = Modifier.padding(horizontal = 16.dp))
    }
    Text(stringResource(R.string.system_sync_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp))

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    Text(stringResource(R.string.system_downloads_title),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
    if (active == 0 && failed == 0 && paused == 0) {
        Text(stringResource(R.string.system_downloads_idle), modifier = Modifier.padding(horizontal = 16.dp))
    } else {
        val parts = buildList {
            if (active > 0) add(stringResource(R.string.system_downloads_active, active))
            if (paused > 0) add(stringResource(R.string.system_downloads_paused, paused))
            if (failed > 0) add(stringResource(R.string.system_downloads_failed, failed))
        }
        Text(parts.joinToString(" · "), modifier = Modifier.padding(horizontal = 16.dp))
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp)) {
            TextButton(enabled = active > 0, onClick = viewModel::pauseAllDownloads) {
                Text(stringResource(R.string.system_downloads_pause))
            }
            TextButton(enabled = paused > 0, onClick = viewModel::resumeAllDownloads) {
                Text(stringResource(R.string.system_downloads_resume))
            }
        }
    }
}

/** Same honest categories as the profiles screen; never raw server messages. */
private fun apiErrorLabel(error: com.walktape.app.core.net.ApiError): Int = when (error) {
    com.walktape.app.core.net.ApiError.InvalidRequest -> R.string.error_configuration
    com.walktape.app.core.net.ApiError.CredentialsUnavailable -> R.string.error_credentials_storage
    com.walktape.app.core.net.ApiError.LocalStorage -> R.string.error_local_storage
    com.walktape.app.core.net.ApiError.Unreachable, com.walktape.app.core.net.ApiError.Network -> R.string.error_network
    com.walktape.app.core.net.ApiError.Timeout -> R.string.error_timeout
    com.walktape.app.core.net.ApiError.Tls -> R.string.error_tls
    com.walktape.app.core.net.ApiError.InvalidResponse -> R.string.error_response
    is com.walktape.app.core.net.ApiError.Http -> R.string.error_http
    is com.walktape.app.core.net.ApiError.Protocol -> when (error.kind) {
        com.walktape.app.core.net.ProtocolErrorKind.AUTHENTICATION -> R.string.error_auth
        com.walktape.app.core.net.ProtocolErrorKind.FORBIDDEN -> R.string.error_permission
        com.walktape.app.core.net.ProtocolErrorKind.AUTH_UNSUPPORTED, com.walktape.app.core.net.ProtocolErrorKind.AUTH_CONFLICT -> R.string.error_auth_method
        com.walktape.app.core.net.ProtocolErrorKind.CLIENT_VERSION, com.walktape.app.core.net.ProtocolErrorKind.SERVER_VERSION -> R.string.error_version
        else -> R.string.error_protocol
    }
}

/** Per-profile download/offline controls plus honest on-disk space accounting. */
@Composable
private fun DownloadsStorageSection(viewModel: SettingsViewModel) {
    val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val profileSettings by viewModel.profileSettings.collectAsStateWithLifecycle()
    val space by viewModel.space.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val cacheCleared by viewModel.cacheCleared.collectAsStateWithLifecycle()

    SectionHeader(text = stringResource(R.string.download_settings))
    if (profile == null) {
        Text(stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp))
        return
    }
    FeedbackSetting(
        title = stringResource(R.string.download_wifi_only),
        help = stringResource(R.string.download_wifi_only_help),
        enabled = profileSettings.wifiOnly,
        onCheckedChange = viewModel::setWifiOnly
    )
    FeedbackSetting(
        title = stringResource(R.string.download_offline_only),
        help = stringResource(R.string.download_offline_only_help),
        enabled = profileSettings.offlineOnly,
        onCheckedChange = viewModel::setOfflineOnly
    )
    FeedbackSetting(
        title = stringResource(R.string.download_skip_unavailable),
        help = stringResource(R.string.download_skip_unavailable_help),
        enabled = profileSettings.skipUnavailable,
        onCheckedChange = viewModel::setSkipUnavailable
    )

    QualitySection(viewModel, profileSettings)

    Text(stringResource(R.string.download_space_title),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
    if (cacheCleared == false) {
        Text(stringResource(R.string.download_cache_clear_blocked),
            color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
    } else if (cacheCleared == true) {
        Text(stringResource(R.string.download_cache_cleared),
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (space.musicBytes >= 0) Text(stringResource(R.string.download_space_music,
            com.walktape.app.ui.components.formatDownloadBytes(space.musicBytes)))
        if (space.audioCacheBytes >= 0) Text(stringResource(R.string.download_space_audio_cache,
            com.walktape.app.ui.components.formatDownloadBytes(space.audioCacheBytes)))
        if (space.coversBytes >= 0) Text(stringResource(R.string.download_space_covers,
            com.walktape.app.ui.components.formatDownloadBytes(space.coversBytes)))
        if (space.totalBytes >= 0) Text(stringResource(R.string.download_space_total,
            com.walktape.app.ui.components.formatDownloadBytes(space.totalBytes)),
            style = MaterialTheme.typography.titleSmall)
    }
    androidx.compose.foundation.layout.FlowRow(Modifier.padding(horizontal = 16.dp)) {
        TextButton(onClick = viewModel::clearAudioCache) {
            Text(stringResource(R.string.download_clear_audio_cache))
        }
        TextButton(onClick = viewModel::clearCoversCache) {
            Text(stringResource(R.string.download_clear_covers_cache))
        }
        TextButton(onClick = viewModel::refreshSpace) { Text(stringResource(R.string.action_retry)) }
    }
    if (cacheCleared != null) {
        TextButton(onClick = viewModel::dismissCacheCleared, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }

    CacheLimitChooser(
        label = stringResource(R.string.download_cache_limit_audio),
        options = listOf(512L * 1024 * 1024, 1024L * 1024 * 1024, 2L * 1024 * 1024 * 1024,
            4L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024),
        selected = settings.audioCacheLimitBytes,
        onSelect = viewModel::setAudioCacheLimit
    )
    CacheLimitChooser(
        label = stringResource(R.string.download_cache_limit_covers),
        options = listOf(100L * 1024 * 1024, 250L * 1024 * 1024, 500L * 1024 * 1024, 1024L * 1024 * 1024),
        selected = settings.coverCacheLimitBytes,
        onSelect = viewModel::setCoverCacheLimit
    )
}

/** Per-profile streaming/download quality (W09-T01); originals are the honest default. */
@Composable
private fun QualitySection(
    viewModel: SettingsViewModel,
    profileSettings: com.walktape.app.core.db.ProfileSettingsEntity,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.quality_mobile_title),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Text(stringResource(R.string.quality_mobile_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            val options = listOf(
                false to R.string.quality_mobile_original,
                true to R.string.quality_mobile_reduced,
            )
            options.forEachIndexed { index, (reduced, label) ->
                SegmentedButton(
                    selected = profileSettings.mobileReduced == reduced,
                    onClick = { viewModel.setMobileReduced(reduced) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(stringResource(label))
                }
            }
        }
        if (profileSettings.mobileReduced || profileSettings.downloadReduced) {
            Text(stringResource(R.string.quality_bitrate_title),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                com.walktape.app.playback.REDUCED_KBPS_CHOICES.forEach { kbps ->
                    androidx.compose.material3.FilterChip(
                        selected = profileSettings.reducedBitRate == kbps,
                        onClick = { viewModel.setReducedBitRate(kbps) },
                        label = { Text(stringResource(R.string.quality_bitrate_kbps, kbps)) }
                    )
                }
            }
        }
        FeedbackSetting(
            title = stringResource(R.string.quality_download_reduced),
            help = stringResource(R.string.quality_download_reduced_help),
            enabled = profileSettings.downloadReduced,
            onCheckedChange = viewModel::setDownloadReduced
        )
    }
}

/** W09 audio DSP: crossfade, ReplayGain, equalizer, bass boost and tape effect. */
@Composable
private fun SoundSection(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val effects by Graph.systemEffects.state.collectAsStateWithLifecycle()

    SectionHeader(text = stringResource(R.string.audio_section_title))
    Text(stringResource(R.string.audio_defaults_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp))

    FeedbackSetting(
        title = stringResource(R.string.audio_crossfade),
        help = stringResource(R.string.audio_crossfade_help),
        enabled = settings.crossfadeEnabled,
        onCheckedChange = viewModel::setCrossfadeEnabled
    )
    if (settings.crossfadeEnabled) {
        val durationLabel = stringResource(R.string.audio_crossfade_duration, settings.crossfadeDurationMs / 1000f)
        Text(durationLabel,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
        androidx.compose.material3.Slider(
            value = settings.crossfadeDurationMs.toFloat(),
            onValueChange = { viewModel.setCrossfadeDurationMs((it.toInt() / 500 * 500)
                .coerceIn(500, 12000)) },
            valueRange = 500f..12000f,
            steps = 22,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = durationLabel },
        )
    }

    Text(stringResource(R.string.audio_replaygain),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
    Text(stringResource(R.string.audio_replaygain_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val modes = listOf(
            com.walktape.app.core.settings.ReplayGainMode.OFF to R.string.audio_replaygain_off,
            com.walktape.app.core.settings.ReplayGainMode.TRACK to R.string.audio_replaygain_track,
            com.walktape.app.core.settings.ReplayGainMode.ALBUM to R.string.audio_replaygain_album,
        )
        modes.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = settings.replayGainMode == mode,
                onClick = { viewModel.setReplayGainMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
            ) { Text(stringResource(label)) }
        }
    }

    when (val state = effects) {
        is com.walktape.app.playback.dsp.SystemEffectsController.State.Ready -> {
            FeedbackSetting(
                title = stringResource(R.string.audio_equalizer),
                help = stringResource(R.string.audio_equalizer_help),
                enabled = state.eqEnabled,
                onCheckedChange = viewModel::setEqualizerEnabled
            )
            if (state.eqEnabled) {
                state.centerHz.forEachIndexed { band, hz ->
                    val level = state.bandLevelsMilliB.getOrElse(band) { 0 }
                    val range = state.levelRangeMilliB
                    val bandLabel = stringResource(R.string.audio_equalizer_band, hz)
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(bandLabel,
                            style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.Slider(
                            value = level.toFloat(),
                            onValueChange = { viewModel.setEqualizerBand(band, it.toInt(), finished = false) },
                            onValueChangeFinished = {
                                viewModel.setEqualizerBand(band, level, finished = true)
                            },
                            valueRange = range.first.toFloat()..range.last.toFloat(),
                            modifier = Modifier.semantics { contentDescription = bandLabel },
                        )
                    }
                }
            }
            FeedbackSetting(
                title = stringResource(R.string.audio_bass_boost),
                help = stringResource(R.string.audio_bass_boost_help, state.bassStrength / 10),
                enabled = state.bassEnabled,
                onCheckedChange = viewModel::setBassBoostEnabled
            )
            if (state.bassEnabled) {
                val bassLabel = stringResource(R.string.audio_bass_boost)
                androidx.compose.material3.Slider(
                    value = state.bassStrength.toFloat(),
                    onValueChange = { viewModel.setBassBoostStrength((it.toInt() / 50 * 50)
                        .coerceIn(0, 1000)) },
                    valueRange = 0f..1000f,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = bassLabel },
                )
            }
        }
        is com.walktape.app.playback.dsp.SystemEffectsController.State.Unavailable ->
            Text(stringResource(R.string.audio_equalizer_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
        else -> Unit
    }

    FeedbackSetting(
        title = stringResource(R.string.audio_tape),
        help = stringResource(R.string.audio_tape_help),
        enabled = settings.tapeEffectEnabled,
        onCheckedChange = viewModel::setTapeEffectEnabled
    )
}

@Composable
private fun CacheLimitChooser(label: String, options: List<Long>, selected: Long, onSelect: (Long) -> Unit) {    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, bytes ->
                androidx.compose.material3.FilterChip(
                    selected = selected == bytes,
                    onClick = { onSelect(bytes) },
                    label = { Text(com.walktape.app.ui.components.formatDownloadBytes(bytes)) }
                )
            }
        }
    }
}

@Composable
private fun AppLockSetting(enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val capability = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_app_lock),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    if (capability == BiometricManager.BIOMETRIC_SUCCESS) R.string.settings_app_lock_help
                    else R.string.settings_app_lock_unavailable
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (capability == BiometricManager.BIOMETRIC_SUCCESS) {
            Switch(checked = enabled, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SuggestionSetting(enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_suggestions),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_suggestions_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun FeedbackSetting(
    title: String,
    help: String,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun ThemeChooser(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.settings_theme_system,
        ThemeMode.LIGHT to R.string.settings_theme_light,
        ThemeMode.DARK to R.string.settings_theme_dark
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(stringResource(label))
            }
        }
    }
}

@Composable
private fun LanguageChooser(context: Context) {
    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val options = listOf(
        "" to R.string.settings_language_system,
        "it" to R.string.settings_language_italian,
        "en" to R.string.settings_language_english
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        options.forEachIndexed { index, (tag, label) ->
            SegmentedButton(
                selected = current == tag,
                onClick = {
                    val locales = if (tag.isEmpty()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(tag)
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(stringResource(label))
            }
        }
    }
}
