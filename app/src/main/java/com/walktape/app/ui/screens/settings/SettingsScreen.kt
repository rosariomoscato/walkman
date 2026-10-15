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
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walktape.app.R
import com.walktape.app.core.settings.ThemeMode
import com.walktape.app.ui.components.SectionHeader
import com.walktape.app.ui.nav.WalkTapeDestination

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenProfiles: () -> Unit,
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

        DownloadsStorageSection(viewModel)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDiagnostics)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_diagnostics),
                style = MaterialTheme.typography.titleMedium
            )
        }

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

@Composable
private fun CacheLimitChooser(label: String, options: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
