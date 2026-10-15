package com.walktape.app.core.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One listened occurrence of a song, recorded by the playback service itself.
 * `sentAt == null` means still to deliver; delivered rows are kept as the local
 * listening history and are never re-sent (the server does not deduplicate).
 */
@Entity(tableName = "scrobble_event",
    primaryKeys = ["profileId", "eventId"],
    indices = [Index("profileId", "sentAt")],
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"],
        childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)])
data class ScrobbleEventEntity(
    val profileId: Long,
    val eventId: String,
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    /** Wall clock (epoch ms) of the first played second of this occurrence. */
    val playedAtMs: Long,
    /** Net playback time actually heard (paused time excluded, seeks not counted). */
    val listenedMs: Long,
    val createdAt: Long,
    val sentAt: Long? = null,
)
