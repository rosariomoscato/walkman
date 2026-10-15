package com.walktape.app.core.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Durable identity of the current listening pass, independent of controller connections. */
@Entity(tableName = "listening_state", foreignKeys = [ForeignKey(entity = ProfileEntity::class,
    parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)])
data class ListeningStateEntity(
    @PrimaryKey val profileId: Long,
    val mediaId: String,
    val eventId: String,
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val playedAtMs: Long,
    val listenedMs: Long,
    val emitted: Boolean,
)
