package com.walktape.app.core.backup

import androidx.room.withTransaction
import com.google.gson.Gson
import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.db.DownloadGroupEntity
import com.walktape.app.core.db.DownloadOriginEntity
import com.walktape.app.core.db.DownloadStatus
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.db.ProfileSettingsEntity
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.settings.ReplayGainMode
import com.walktape.app.core.settings.SettingsRepository
import com.walktape.app.core.settings.ThemeMode
import com.walktape.app.playback.SavedQueue
import com.walktape.app.playback.SavedQueueItem
import kotlinx.coroutines.flow.first

/**
 * W11-T03: builds and applies the versioned backup. The export reads only approved
 * data (profiles without credentials, completed-download metadata, saved queues);
 * the import validates first, then applies each profile inside one transaction so a
 * failure can never leave partial data, never triggers downloads/playback and never
 * transfers listening history or pending operations.
 */
class BackupManager(
    private val database: WalkTapeDatabase,
    private val settingsRepository: SettingsRepository,
    /** Reads the saved queue through the FIFO writer barrier (consistency with pending saves). */
    private val queueLoader: suspend (Long) -> SavedQueue?,
    /** Queue replacement is refused for the active profile while media is loaded. */
    private val activeProfileHasMedia: () -> Boolean = { false },
    private val appVersion: String,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val gson = Gson()

    data class BuildPreview(val profiles: Int, val savedQueues: Int, val downloadItems: Int)

    suspend fun preview(): BuildPreview {
        val profileDao = database.profileDao()
        val downloadDao = database.downloadDao()
        var queues = 0
        var downloads = 0
        profileDao.all().forEach { profile ->
            if (queueLoader(profile.id) != null) queues++
            downloads += downloadDao.completedCount(profile.id)
        }
        return BuildPreview(profileDao.count(), queues, downloads)
    }

    suspend fun build(includePreferences: Boolean): BackupFile {
        val profileDao = database.profileDao()
        val downloadDao = database.downloadDao()
        val profiles = profileDao.all().map { profile ->
            val settings = downloadDao.settings(profile.id)
            val downloads = downloadDao.completedAll(profile.id)
            val origins = downloadDao.allOrigins(profile.id).groupBy { it.songId }
            val groups = downloadDao.groupsOnce(profile.id)
            val queue = queueLoader(profile.id)
            BackupProfile(
                name = profile.name,
                serverUrl = profile.serverUrl,
                username = profile.username,
                allowLocalHttp = profile.allowLocalHttp,
                settings = settings?.let {
                    BackupProfileSettings(it.wifiOnly, it.offlineOnly, it.skipUnavailable,
                        it.mobileReduced, it.reducedBitRate, it.downloadReduced)
                },
                downloads = downloads.map { row ->
                    BackupDownload(row.songId, row.title, row.artist, row.album, row.albumId,
                        row.coverArt, row.duration, row.suffix, row.contentType, row.serverSize,
                        row.representation, row.qualityKbps,
                        origins[row.songId].orEmpty().map { it.origin })
                },
                groups = groups.map { group ->
                    BackupDownloadGroup(group.origin, group.name, parseIds(group.songIds),
                        group.serverMissing)
                },
                queue = queue?.let { saved ->
                    BackupQueue(
                        items = saved.items.map {
                            BackupQueueItem(it.entryId, it.songId, it.title, it.artist,
                                it.album, it.durationMs, it.originName)
                        },
                        traversal = saved.traversal,
                        currentId = saved.currentId,
                        positionMs = saved.positionMs,
                        repeatMode = saved.repeatMode,
                        shuffle = saved.shuffle,
                        mixtapeName = saved.mixtapeName,
                    )
                },
            )
        }
        val preferences = if (includePreferences) {
            val s = settingsRepository.settings.first()
            BackupPreferences(s.themeMode.name, s.suggestionsEnabled, s.hapticsEnabled,
                s.keyClicksEnabled, s.audioCacheLimitBytes, s.coverCacheLimitBytes,
                s.crossfadeEnabled, s.crossfadeDurationMs, s.replayGainMode.name,
                s.equalizerEnabled, s.equalizerBands, s.bassBoostEnabled, s.bassBoostStrength,
                s.tapeEffectEnabled, s.appLockEnabled)
        } else null
        return BackupFile(BackupCodec.FORMAT, BackupCodec.VERSION, clock(), appVersion,
            preferences, profiles)
    }

    data class ProfileResult(
        val backupName: String,
        val displayName: String,
        val created: Boolean,
        val queueApplied: Boolean,
        /** Queue kept because the matching profile is active with media loaded. */
        val queueSkippedActive: Boolean,
        val downloadsAdded: Int,
        val downloadsKept: Int,
    )

    data class ImportSummary(
        val results: List<ProfileResult>,
        val preferencesApplied: Boolean,
        /** Profile names (as in the backup) that could not be applied at all. */
        val failed: List<String>,
    )

    suspend fun import(backup: BackupFile, applyPreferences: Boolean): ImportSummary {
        require(BackupCodec.validate(backup) == null) { "backup must be validated first" }
        val profileDao = database.profileDao()
        val downloadDao = database.downloadDao()
        val queueDao = database.queueDao()
        val activeProfile = profileDao.active()
        val activeHasMedia = activeProfileHasMedia()
        val results = mutableListOf<ProfileResult>()
        val failed = mutableListOf<String>()

        backup.profiles.forEach { source ->
            try {
                var outcome: ProfileResult? = null
                database.withTransaction {
                    // Same server + same user = the same account: merge, never duplicate it.
                    val existing = profileDao.all().firstOrNull {
                        it.serverUrl == source.serverUrl && it.username == source.username
                    }
                    val targetId: Long
                    val created: Boolean
                    var displayName = source.name
                    if (existing != null) {
                        targetId = existing.id
                        created = false
                        displayName = existing.name
                    } else {
                        val names = profileDao.all().map { it.name }.toHashSet()
                        var candidate = source.name.trim()
                        var suffix = 2
                        while (candidate in names) {
                            candidate = "${source.name.trim()} ($suffix)"
                            suffix++
                        }
                        displayName = candidate
                        targetId = profileDao.insert(ProfileEntity(
                            name = candidate,
                            serverUrl = source.serverUrl,
                            username = source.username,
                            isActive = false,
                            createdAt = clock(),
                            // No credentialId/encryptedPassword: a fresh login is required.
                            credentialId = "",
                            encryptedPassword = "",
                            allowLocalHttp = source.allowLocalHttp,
                        ))
                        created = true
                    }
                    if (created) {
                        source.settings?.let { s ->
                            downloadDao.upsertSettings(ProfileSettingsEntity(
                                profileId = targetId, wifiOnly = s.wifiOnly,
                                offlineOnly = s.offlineOnly, skipUnavailable = s.skipUnavailable,
                                mobileReduced = s.mobileReduced, reducedBitRate = s.reducedBitRate,
                                downloadReduced = s.downloadReduced))
                        }
                    }
                    var added = 0
                    var kept = 0
                    source.downloads.forEach { item ->
                        if (downloadDao.download(targetId, item.songId) != null) {
                            // Existing files (also completed ones) are never degraded.
                            kept++
                        } else {
                            downloadDao.upsertDownload(DownloadEntity(
                                profileId = targetId, songId = item.songId,
                                status = DownloadStatus.PAUSED.name,
                                relativePath = null, bytes = 0, totalBytes = -1,
                                title = item.title, artist = item.artist, album = item.album,
                                albumId = item.albumId, coverArt = item.coverArt,
                                duration = item.durationSec, suffix = item.suffix,
                                contentType = item.contentType, serverSize = item.serverSize,
                                createdAt = clock(), completedAt = null, updatedAt = clock(),
                                lastError = null, attempts = 0,
                                representation = item.representation,
                                qualityKbps = item.qualityKbps))
                            item.origins.forEach { origin ->
                                downloadDao.insertOrigin(
                                    DownloadOriginEntity(targetId, item.songId, origin, clock()))
                            }
                            added++
                        }
                    }
                    source.groups.forEach { group ->
                        downloadDao.upsertGroup(DownloadGroupEntity(targetId, group.origin,
                            group.name, gson.toJson(group.songIds), clock(), group.serverMissing))
                    }
                    var queueApplied = false
                    var queueSkippedActive = false
                    source.queue?.let { queue ->
                        if (existing != null && existing.id == activeProfile?.id && activeHasMedia) {
                            queueSkippedActive = true
                        } else {
                            val saved = SavedQueue(
                                profileId = targetId,
                                items = queue.items.map {
                                    SavedQueueItem(it.entryId, it.songId, it.title, it.artist,
                                        it.album, it.durationMs, it.originName)
                                },
                                traversal = queue.traversal,
                                currentId = queue.currentId,
                                positionMs = queue.positionMs,
                                repeatMode = queue.repeatMode,
                                shuffle = queue.shuffle,
                                updatedAt = clock(),
                                mixtapeName = queue.mixtapeName,
                            )
                            if (saved.isValid()) queueApplied = queueDao.replace(saved)
                        }
                    }
                    outcome = ProfileResult(source.name, displayName, created, queueApplied,
                        queueSkippedActive, added, kept)
                }
                outcome?.let { results.add(it) }
            } catch (_: Exception) {
                failed.add(source.name)
            }
        }

        var preferencesApplied = false
        if (applyPreferences && failed.isEmpty() && backup.preferences != null) {
            applyPreferences(backup.preferences)
            preferencesApplied = true
        }
        return ImportSummary(results, preferencesApplied, failed)
    }

    private suspend fun applyPreferences(p: BackupPreferences) {
        settingsRepository.apply {
            setThemeMode(ThemeMode.entries.first { it.name == p.themeMode })
            setSuggestionsEnabled(p.suggestionsEnabled)
            setHapticsEnabled(p.hapticsEnabled)
            setKeyClicksEnabled(p.keyClicksEnabled)
            setAudioCacheLimitBytes(p.audioCacheLimitBytes)
            setCoverCacheLimitBytes(p.coverCacheLimitBytes)
            setCrossfadeEnabled(p.crossfadeEnabled)
            setCrossfadeDurationMs(p.crossfadeDurationMs)
            setReplayGainMode(ReplayGainMode.entries.first { it.name == p.replayGainMode })
            setEqualizerEnabled(p.equalizerEnabled)
            setEqualizerBands(p.equalizerBands)
            setBassBoostEnabled(p.bassBoostEnabled)
            setBassBoostStrength(p.bassBoostStrength)
            setTapeEffectEnabled(p.tapeEffectEnabled)
            setAppLockEnabled(p.appLockEnabled)
        }
    }

    private fun parseIds(json: String): List<String> = try {
        gson.fromJson(json, Array<String>::class.java).toList()
    } catch (_: Exception) {
        emptyList()
    }
}
