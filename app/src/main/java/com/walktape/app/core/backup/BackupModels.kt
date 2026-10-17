package com.walktape.app.core.backup

/**
 * W11-T03: explicit, versioned backup DTOs. Never a Room dump: no credentials, no
 * credentialId/Keystore references, no audio files, no cache, no listening ledger,
 * no pending operations. Server-side identity (song/album/playlist ids) is kept as-is
 * so a new login on the same server reuses it.
 */
data class BackupFile(
    val format: String,
    val version: Int,
    val createdAt: Long,
    val appVersion: String,
    val preferences: BackupPreferences?,
    val profiles: List<BackupProfile>,
)

/** Device preferences only; the `loaded` flag and runtime state never travel. */
data class BackupPreferences(
    val themeMode: String,
    val suggestionsEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val keyClicksEnabled: Boolean,
    val audioCacheLimitBytes: Long,
    val coverCacheLimitBytes: Long,
    val crossfadeEnabled: Boolean,
    val crossfadeDurationMs: Int,
    val replayGainMode: String,
    val equalizerEnabled: Boolean,
    val equalizerBands: String,
    val bassBoostEnabled: Boolean,
    val bassBoostStrength: Int,
    val tapeEffectEnabled: Boolean,
    val appLockEnabled: Boolean,
)

/** Profile identity without secrets: the imported profile needs a fresh login. */
data class BackupProfile(
    val name: String,
    val serverUrl: String,
    val username: String,
    val allowLocalHttp: Boolean,
    val settings: BackupProfileSettings?,
    /** Completed downloads of the source device: the "list to re-download". */
    val downloads: List<BackupDownload>,
    /** Downloaded-playlist subscriptions with their ordered snapshots (duplicates allowed). */
    val groups: List<BackupDownloadGroup>,
    val queue: BackupQueue?,
)

data class BackupProfileSettings(
    val wifiOnly: Boolean,
    val offlineOnly: Boolean,
    val skipUnavailable: Boolean,
    val mobileReduced: Boolean,
    val reducedBitRate: Int,
    val downloadReduced: Boolean,
)

/** Metadata snapshot of one downloaded song, enough to show and re-request it. */
data class BackupDownload(
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val coverArt: String?,
    val durationSec: Int,
    val suffix: String?,
    val contentType: String?,
    val serverSize: Long,
    val representation: String,
    val qualityKbps: Int?,
    val origins: List<String>,
)

data class BackupDownloadGroup(
    val origin: String,
    val name: String,
    val songIds: List<String>,
    val serverMissing: Boolean,
)

/** Saved queue with occurrences, traversal, position and mixtape label (no URIs). */
data class BackupQueue(
    val items: List<BackupQueueItem>,
    val traversal: List<String>,
    val currentId: String?,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean,
    val mixtapeName: String?,
)

data class BackupQueueItem(
    val entryId: String,
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val originName: String?,
)
