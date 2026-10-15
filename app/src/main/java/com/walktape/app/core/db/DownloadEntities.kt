package com.walktape.app.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Download states persisted across process death and reboots. */
enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

/** Generic, non-sensitive error kinds; never raw server messages. */
enum class DownloadError { NETWORK, SPACE, NOT_FOUND, FORBIDDEN, CANCELLED, INVALID }

/**
 * One audio file per (profile, song): the same file can be referenced by any number of
 * origins (single, album, playlist) without extra copies. The metadata snapshot keeps a
 * single download playable when the song disappears from the server or from the index.
 */
@Entity(
    tableName = "download",
    primaryKeys = ["profileId", "songId"],
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"],
        childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId")]
)
data class DownloadEntity(
    val profileId: Long,
    val songId: String,
    val status: String,
    /** Path relative to the profile directory; null until a partial file exists. */
    val relativePath: String?,
    /** Bytes currently on disk (partial or complete); the UI must show file truth. */
    val bytes: Long,
    /** Expected total bytes; -1 when unknown. */
    val totalBytes: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val coverArt: String?,
    val duration: Int,
    val suffix: String?,
    val contentType: String?,
    val serverSize: Long,
    val createdAt: Long,
    val completedAt: Long?,
    val updatedAt: Long,
    val lastError: String?,
    val attempts: Int,
    @ColumnInfo(defaultValue = "0") val serverMissing: Boolean = false,
)

/** Membership of a download: "single", "album:<id>" or "playlist:<id>". */
@Entity(
    tableName = "download_origin",
    primaryKeys = ["profileId", "songId", "origin"],
    foreignKeys = [ForeignKey(entity = DownloadEntity::class,
        parentColumns = ["profileId", "songId"], childColumns = ["profileId", "songId"],
        onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId", "origin")]
)
data class DownloadOriginEntity(
    val profileId: Long,
    val songId: String,
    val origin: String,
    val createdAt: Long,
)

/** Subscription and ordered snapshot, including duplicate occurrences and empty playlists. */
@Entity(tableName = "download_group", primaryKeys = ["profileId", "origin"],
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"],
        childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)])
data class DownloadGroupEntity(val profileId: Long, val origin: String, val name: String,
    val songIds: String, val refreshedAt: Long, val serverMissing: Boolean)

/** Offline mutation queue states. */
enum class PendingOpState { PENDING, SYNCING, CONFLICT, FAILED }

/**
 * Durable per-profile mutation queue (W08-T06): playlist and favorite edits recorded
 * offline, replayed serially with baseline checks. Payloads contain identifiers and
 * metadata only, never credentials or authenticated URLs.
 */
@Entity(
    tableName = "pending_op",
    primaryKeys = ["profileId", "opId"],
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"],
        childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profileId", "state")]
)
data class PendingOpEntity(
    val profileId: Long,
    val opId: String,
    val kind: String,
    val payload: String,
    /** Server "changed" value captured when the local edit started; null for creates. */
    val baselineChanged: String?,
    /** Content signature at edit time to detect concurrent server changes. */
    val baselineSignature: String?,
    val createdAt: Long,
    val state: String,
    val attempts: Int,
    val lastError: String?,
    val updatedAt: Long,
)

/** Per-profile connection and offline behaviour (SPEC: network settings belong to the profile). */
@Entity(
    tableName = "profile_settings",
    primaryKeys = ["profileId"],
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"],
        childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)]
)
data class ProfileSettingsEntity(
    val profileId: Long,
    /** Downloads restricted to unmetered networks until the user opts in. */
    @ColumnInfo(defaultValue = "1") val wifiOnly: Boolean = true,
    /** Offline-only mode: no app network requests at all for this profile. */
    @ColumnInfo(defaultValue = "0") val offlineOnly: Boolean = false,
    /** Automatically skip unavailable songs during offline playback; signalled in the UI. */
    @ColumnInfo(defaultValue = "1") val skipUnavailable: Boolean = true,
)
