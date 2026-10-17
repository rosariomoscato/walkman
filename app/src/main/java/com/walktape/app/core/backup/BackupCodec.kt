package com.walktape.app.core.backup

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.core.settings.AppSettings
import com.walktape.app.core.settings.ReplayGainMode
import com.walktape.app.core.settings.SettingsRepository
import com.walktape.app.core.settings.ThemeMode
import com.walktape.app.playback.QueueIdentity

/**
 * W11-T03: JSON codec for the versioned backup. Parsing is total: a file that fails
 * any structural check is rejected whole, so a truncated/corrupt/future backup can
 * never leave partially-applied data behind.
 */
object BackupCodec {

    const val FORMAT = "walktape.backup"
    const val VERSION = 1

    const val MAX_PROFILES = 32
    const val MAX_QUEUE_ITEMS = 5_000
    const val MAX_DOWNLOAD_ITEMS = 20_000
    const val MAX_CHARS = 10 * 1024 * 1024
    const val MAX_NAME = 80

    private val gson = Gson()
    private val originPattern = Regex("""(single|album:[^\s]+|playlist:[^\s]+)""")
    private val groupPattern = Regex("""(album:[^\s]+|playlist:[^\s]+)""")

    enum class Reason {
        NOT_JSON, NOT_BACKUP, UNSUPPORTED_VERSION, TOO_LARGE,
        INVALID_PROFILE, INVALID_QUEUE, INVALID_DOWNLOAD, INVALID_PREFERENCES,
    }

    sealed interface Parsed {
        data class Valid(val backup: BackupFile) : Parsed
        data class Invalid(val reason: Reason) : Parsed
    }

    fun write(backup: BackupFile): String {
        require(backup.format == FORMAT && backup.version == VERSION)
        return gson.toJson(backup)
    }

    fun parse(text: String): Parsed {
        if (text.length > MAX_CHARS) return Parsed.Invalid(Reason.TOO_LARGE)
        val raw = try {
            gson.fromJson(text, BackupFile::class.java)
        } catch (_: JsonParseException) {
            return Parsed.Invalid(Reason.NOT_JSON)
        } catch (_: IllegalStateException) {
            return Parsed.Invalid(Reason.NOT_JSON)
        } ?: return Parsed.Invalid(Reason.NOT_JSON)
        return when (val reason = validate(raw)) {
            null -> Parsed.Valid(raw)
            else -> Parsed.Invalid(reason)
        }
    }

    /** Full validation of a parsed backup; null means applicable. */
    fun validate(backup: BackupFile?): Reason? {
        if (backup == null) return Reason.NOT_JSON
        if (backup.format != FORMAT) return Reason.NOT_BACKUP
        if (backup.version > VERSION) return Reason.UNSUPPORTED_VERSION
        if (backup.version < 1 || backup.createdAt < 0) return Reason.NOT_BACKUP
        if (backup.profiles.size > MAX_PROFILES) return Reason.TOO_LARGE
        if (backup.preferences != null && invalidPreferences(backup.preferences) != null) {
            return Reason.INVALID_PREFERENCES
        }
        val totalDownloads = backup.profiles.sumOf { it.downloads.size }
        if (totalDownloads > MAX_DOWNLOAD_ITEMS) return Reason.TOO_LARGE
        backup.profiles.forEach { profile ->
            if (profile.name.isBlank() || profile.name.length > MAX_NAME) return Reason.INVALID_PROFILE
            if (profile.username.isBlank() || profile.username.length > MAX_NAME) return Reason.INVALID_PROFILE
            if (ServerAddress.parse(profile.serverUrl, profile.allowLocalHttp) == null) {
                return Reason.INVALID_PROFILE
            }
            if (invalidProfileSettings(profile.settings) != null) return Reason.INVALID_PROFILE
            val seen = HashSet<String>()
            profile.downloads.forEach { download ->
                if (download.songId.isBlank() || download.title.isBlank()) return Reason.INVALID_DOWNLOAD
                if (!seen.add(download.songId)) return Reason.INVALID_DOWNLOAD
                if (download.durationSec < 0 || download.serverSize < 0) return Reason.INVALID_DOWNLOAD
                if (download.representation !in setOf("ORIGINAL", "REDUCED")) return Reason.INVALID_DOWNLOAD
                if (download.qualityKbps != null && download.qualityKbps <= 0) return Reason.INVALID_DOWNLOAD
                if (download.origins.any { !originPattern.matches(it) }) return Reason.INVALID_DOWNLOAD
            }
            profile.groups.forEach { group ->
                if (!groupPattern.matches(group.origin) || group.name.isBlank()) {
                    return Reason.INVALID_DOWNLOAD
                }
                if (group.songIds.any { it.isBlank() }) return Reason.INVALID_DOWNLOAD
            }
            invalidQueue(profile.queue)?.let { return it }
        }
        return null
    }

    /** Mirrors SavedQueue.isValid() on the DTO, including duplicate occurrences and traversal. */
    private fun invalidQueue(queue: BackupQueue?): Reason? {
        if (queue == null) return null
        if (queue.items.size > MAX_QUEUE_ITEMS) return Reason.TOO_LARGE
        if (queue.positionMs < 0 || queue.repeatMode !in 0..2) return Reason.INVALID_QUEUE
        if (queue.items.isEmpty()) {
            return if (queue.currentId == null && queue.positionMs == 0L && !queue.shuffle) null
            else Reason.INVALID_QUEUE
        }
        queue.items.forEach { item ->
            if (!item.entryId.startsWith(QueueIdentity.PREFIX) || item.songId.isBlank()) {
                return Reason.INVALID_QUEUE
            }
            if (item.durationMs != null && item.durationMs <= 0) return Reason.INVALID_QUEUE
            if (item.title.isBlank()) return Reason.INVALID_QUEUE
        }
        val ids = queue.items.map { it.entryId }
        if (ids.toSet().size != ids.size) return Reason.INVALID_QUEUE
        if (queue.traversal.size != ids.size || queue.traversal.toSet() != ids.toSet()) {
            return Reason.INVALID_QUEUE
        }
        if (queue.currentId !in ids) return Reason.INVALID_QUEUE
        if (queue.mixtapeName != null && queue.mixtapeName.length > MAX_NAME) return Reason.INVALID_QUEUE
        return null
    }

    private fun invalidProfileSettings(settings: BackupProfileSettings?): Reason? {
        if (settings == null) return null
        if (settings.reducedBitRate !in 1..100_000) return Reason.INVALID_PROFILE
        return null
    }

    private fun invalidPreferences(prefs: BackupPreferences): Reason? {
        if (prefs.themeMode !in ThemeMode.entries.map { it.name }) return Reason.INVALID_PREFERENCES
        if (prefs.replayGainMode !in ReplayGainMode.entries.map { it.name }) return Reason.INVALID_PREFERENCES
        if (prefs.audioCacheLimitBytes !in SettingsRepository.MIN_CACHE_LIMIT..SettingsRepository.MAX_CACHE_LIMIT) {
            return Reason.INVALID_PREFERENCES
        }
        if (prefs.coverCacheLimitBytes !in SettingsRepository.MIN_CACHE_LIMIT..SettingsRepository.MAX_CACHE_LIMIT) {
            return Reason.INVALID_PREFERENCES
        }
        if (prefs.crossfadeDurationMs !in AppSettings.MIN_CROSSFADE_MS..AppSettings.MAX_CROSSFADE_MS) {
            return Reason.INVALID_PREFERENCES
        }
        if (prefs.bassBoostStrength !in AppSettings.MIN_BASS_STRENGTH..AppSettings.MAX_BASS_STRENGTH) {
            return Reason.INVALID_PREFERENCES
        }
        if (prefs.equalizerBands.length > 512) return Reason.INVALID_PREFERENCES
        return null
    }
}
