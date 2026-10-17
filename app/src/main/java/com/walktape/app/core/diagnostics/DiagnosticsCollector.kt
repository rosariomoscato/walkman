package com.walktape.app.core.diagnostics

import android.content.Context
import android.os.Build
import android.os.StatFs
import com.walktape.app.core.db.LibraryStateEntity
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.library.SyncProgress
import com.walktape.app.core.settings.AppSettings
import com.walktape.app.playback.PlaybackSnapshot
import com.walktape.app.playback.SleepTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * W11-T02: diagnostics report built strictly from an allowlist of fields, grouped into
 * sections the user can include or exclude before copying/exporting. No logcat, no raw
 * database dumps, no automatic collection or upload; every rendered line goes through
 * [Redactor] as defence in depth.
 */
class DiagnosticsCollector(
    private val context: Context,
    private val sources: Sources = Sources.fromGraph(),
) {

    enum class SectionId { APP, DEVICE, STORAGE, LIBRARY, DOWNLOADS, SYNC, PLAYER, CONNECTIONS, PREFERENCES }

    data class ReportLine(val key: String, val value: String)
    data class ReportSection(val id: SectionId, val lines: List<ReportLine>)

    /**
     * Data sources injected for tests; the default binds to the live Graph singletons.
     * Each source returns allowlisted values only: never credentials, tokens or payloads.
     */
    data class Sources(
        val profiles: suspend () -> List<ProfileEntity> = { emptyList() },
        val libraryState: suspend (Long) -> LibraryStateEntity? = { null },
        /** Download counts per status name (QUEUED…FAILED). */
        val downloadCounts: suspend (Long) -> Map<String, Int> = { emptyMap() },
        /** Error categories of non-completed downloads (enum names, not raw messages). */
        val downloadErrors: suspend (Long) -> Map<String, Int> = { emptyMap() },
        /** Pending-op counts per state name (PENDING/SYNCING/CONFLICT/FAILED). */
        val pendingOpCounts: suspend (Long) -> Map<String, Int> = { emptyMap() },
        val syncProgress: () -> SyncProgress? = { null },
        val playback: () -> PlaybackSnapshot? = { null },
        val timer: () -> SleepTimer.State? = { null },
        val settings: suspend () -> AppSettings? = { null },
        /** Whether the offline-only gate blocks a profile (fail-closed until loaded). */
        val offlineOnly: (Long) -> Boolean = { false },
        /** Per-profile storage in bytes under fixed keys: music/audioCache/covers. */
        val profileStorage: suspend (Long) -> Map<String, Long> = { emptyMap() },
    ) {
        companion object {
            /** Binds to the process singletons; safe only after Graph.init (Graph.ready()). */
            @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
            fun fromGraph(): Sources {
                val graph = com.walktape.app.Graph
                return Sources(
                    profiles = { graph.database.profileDao().all() },
                    libraryState = { graph.database.libraryDao().state(it) },
                    downloadCounts = { id ->
                        graph.database.downloadDao().downloadsOnce(id)
                            .groupingBy { it.status }.eachCount()
                    },
                    downloadErrors = { id ->
                        graph.database.downloadDao().downloadsOnce(id)
                            .filter { it.lastError != null }
                            .groupingBy { it.lastError ?: "UNKNOWN" }.eachCount()
                    },
                    pendingOpCounts = { id ->
                        graph.database.downloadDao().opsOnce(id)
                            .groupingBy { it.state }.eachCount()
                    },
                    syncProgress = { graph.librarySyncEngine.progress.value },
                    playback = { graph.playback.state.value },
                    timer = { graph.playbackTimer.state.value },
                    settings = { graph.currentSettings.takeIf { _ -> graph.ready() } },
                    offlineOnly = { id -> graph.networkGate.offlineOnly(id) },
                    profileStorage = { id ->
                        val dir = graph.profiles.directory(id)
                        mapOf(
                            "music" to com.walktape.app.core.downloads.DownloadStore.musicBytes(dir),
                            "audioCache" to graph.audioCache.bytesOnDisk(id),
                            "covers" to graph.coverArtStore(id).diskBytes(),
                        )
                    },
                )
            }
        }
    }

    /** The connections section carries profile URLs/usernames: opt-in, excluded by default. */
    val defaultIncluded: Set<SectionId> = SectionId.entries.toSet() - SectionId.CONNECTIONS

    suspend fun buildSections(): List<ReportSection> = withContext(Dispatchers.IO) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val profiles = sources.profiles()
        val settings = sources.settings()
        val activeProfile = profiles.firstOrNull { it.isActive }
        val activeId = activeProfile?.id
        val filesStat = StatFs(context.filesDir.absolutePath)
        val cacheBytes = dirSize(context.cacheDir)
        val metrics = context.resources.displayMetrics
        val locale = context.resources.configuration.locales[0]

        buildList {
            add(ReportSection(SectionId.APP, listOf(
                ReportLine("app.version", packageInfo.versionName ?: "unknown"),
                ReportLine("app.versionCode",
                    androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo).toString()),
                ReportLine("app.package", context.packageName),
                ReportLine("locale", locale.toLanguageTag()),
            )))
            add(ReportSection(SectionId.DEVICE, listOf(
                ReportLine("device.manufacturer", Build.MANUFACTURER),
                ReportLine("device.model", Build.MODEL),
                ReportLine("device.android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
                ReportLine("device.abis", Build.SUPPORTED_ABIS.joinToString(",")),
                ReportLine("display", "${metrics.widthPixels}x${metrics.heightPixels} @${metrics.density}x"),
            )))
            add(ReportSection(SectionId.STORAGE, listOf(
                ReportLine("storage.files.availableMb", (filesStat.availableBytes / (1024 * 1024)).toString()),
                ReportLine("storage.files.totalMb", (filesStat.totalBytes / (1024 * 1024)).toString()),
                ReportLine("storage.cacheDirMb", (cacheBytes / (1024 * 1024)).toString()),
                ReportLine("storage.profiles.configured", profiles.size.toString()),
            ) + profiles.flatMap { profile ->
                val usage = sources.profileStorage(profile.id)
                listOf(
                    ReportLine("storage.profile.${profile.id}.musicMb",
                        ((usage["music"] ?: -1L) / (1024 * 1024)).toString()),
                    ReportLine("storage.profile.${profile.id}.audioCacheMb",
                        ((usage["audioCache"] ?: -1L) / (1024 * 1024)).toString()),
                    ReportLine("storage.profile.${profile.id}.coversMb",
                        ((usage["covers"] ?: -1L) / (1024 * 1024)).toString()),
                )
            }))
            add(ReportSection(SectionId.LIBRARY,
                profiles.map { profile -> profile.id.toString() to sources.libraryState(profile.id) }
                    .flatMap { (id, state) ->
                        if (state == null) listOf(ReportLine("library.profile.$id", "never-synced"))
                        else listOf(
                            ReportLine("library.profile.$id.lastSyncAt", state.lastSyncAt.toString()),
                            ReportLine("library.profile.$id.lastSyncOk", state.lastSyncOk.toString()),
                            ReportLine("library.profile.$id.counts",
                                "${state.artistCount}a/${state.albumCount}al/${state.songCount}s"),
                        )
                    } + (sources.syncProgress()?.let { progress ->
                        listOf(
                            ReportLine("library.sync.running", progress.running.toString()),
                            ReportLine("library.sync.processed", "${progress.processed}/${progress.total}"),
                            ReportLine("library.sync.lastErrorCategory",
                                progress.error?.let { it::class.simpleName } ?: "none"),
                        )
                    } ?: emptyList())))
            add(ReportSection(SectionId.DOWNLOADS, profiles.flatMap { profile ->
                val counts = sources.downloadCounts(profile.id)
                val errors = sources.downloadErrors(profile.id)
                listOf(ReportLine("downloads.profile.${profile.id}",
                    counts.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
                        .ifEmpty { "none" })) +
                    (if (errors.isEmpty()) emptyList() else listOf(ReportLine(
                        "downloads.profile.${profile.id}.errorCategories",
                        errors.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" })))
            }))
            add(ReportSection(SectionId.SYNC, profiles.flatMap { profile ->
                val counts = sources.pendingOpCounts(profile.id)
                listOf(ReportLine("sync.profile.${profile.id}",
                    counts.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
                        .ifEmpty { "none" }))
            }))
            add(ReportSection(SectionId.PLAYER, run {
                val snapshot = sources.playback()
                val timer = sources.timer()
                listOf(
                    ReportLine("player.connected", snapshot?.connected?.toString() ?: "unknown"),
                    ReportLine("player.hasMedia", snapshot?.hasMedia?.toString() ?: "unknown"),
                    ReportLine("player.playWhenReady", snapshot?.playWhenReady?.toString() ?: "unknown"),
                    ReportLine("player.shuffleOn", snapshot?.shuffleOn?.toString() ?: "unknown"),
                    ReportLine("player.repeatMode", snapshot?.repeatMode?.toString() ?: "unknown"),
                    ReportLine("player.queueSize", snapshot?.queue?.size?.toString() ?: "unknown"),
                    ReportLine("player.timerArmed", timer?.armed?.toString() ?: "unknown"),
                    ReportLine("player.failureCategory", snapshot?.failure?.name ?: "none"),
                )
            }))
            // Opt-in: profile connection identities (URL + username), the only private fields.
            add(ReportSection(SectionId.CONNECTIONS, profiles.flatMap { profile ->
                listOf(
                    ReportLine("connections.profile.${profile.id}.name", profile.name),
                    ReportLine("connections.profile.${profile.id}.url", profile.serverUrl),
                    ReportLine("connections.profile.${profile.id}.username", profile.username),
                    ReportLine("connections.profile.${profile.id}.active", profile.isActive.toString()),
                    ReportLine("connections.profile.${profile.id}.credentialsStored",
                        profile.credentialId.isNotEmpty().toString()),
                )
            }))
            if (settings != null) {
                add(ReportSection(SectionId.PREFERENCES, listOf(
                    ReportLine("preferences.themeMode", settings.themeMode.name),
                    ReportLine("preferences.suggestions", settings.suggestionsEnabled.toString()),
                    ReportLine("preferences.haptics", settings.hapticsEnabled.toString()),
                    ReportLine("preferences.keyClicks", settings.keyClicksEnabled.toString()),
                    ReportLine("preferences.audioCacheLimitMb",
                        (settings.audioCacheLimitBytes / (1024 * 1024)).toString()),
                    ReportLine("preferences.coverCacheLimitMb",
                        (settings.coverCacheLimitBytes / (1024 * 1024)).toString()),
                    ReportLine("preferences.crossfade", "${settings.crossfadeEnabled}/${settings.crossfadeDurationMs}ms"),
                    ReportLine("preferences.replayGain", settings.replayGainMode.name),
                    ReportLine("preferences.equalizer", settings.equalizerEnabled.toString()),
                    ReportLine("preferences.bassBoost", settings.bassBoostEnabled.toString()),
                    ReportLine("preferences.tapeEffect", settings.tapeEffectEnabled.toString()),
                    ReportLine("preferences.updateCheck", settings.updateCheckEnabled.toString()),
                    ReportLine("preferences.offlineOnlyActiveProfile",
                        activeId?.let { sources.offlineOnly(it).toString() } ?: "unknown"),
                )))
            }
        }
    }

    /** Renders the selected sections and redacts the whole text as a final safety pass. */
    fun render(sections: List<ReportSection>, included: Set<SectionId>): String =
        sections.filter { it.id in included }
            .joinToString("\n\n") { section ->
                buildString {
                    append("== ")
                    append(sectionTitle(section.id))
                    append(" ==\n")
                    section.lines.forEach { append(it.key); append(": "); append(it.value); append('\n') }
                }
            }
            .let { Redactor.redact(it.trimEnd()) }

    private fun sectionTitle(id: SectionId): String = context.getString(
        when (id) {
            SectionId.APP -> com.walktape.app.R.string.diagnostics_section_app
            SectionId.DEVICE -> com.walktape.app.R.string.diagnostics_section_device
            SectionId.STORAGE -> com.walktape.app.R.string.diagnostics_section_storage
            SectionId.LIBRARY -> com.walktape.app.R.string.diagnostics_section_library
            SectionId.DOWNLOADS -> com.walktape.app.R.string.diagnostics_section_downloads
            SectionId.SYNC -> com.walktape.app.R.string.diagnostics_section_sync
            SectionId.PLAYER -> com.walktape.app.R.string.diagnostics_section_player
            SectionId.CONNECTIONS -> com.walktape.app.R.string.diagnostics_section_connections
            SectionId.PREFERENCES -> com.walktape.app.R.string.diagnostics_section_preferences
        })

    suspend fun buildReport(): String = render(buildSections(), defaultIncluded)

    private fun dirSize(dir: java.io.File): Long =
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    companion object {
        fun formatBytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
