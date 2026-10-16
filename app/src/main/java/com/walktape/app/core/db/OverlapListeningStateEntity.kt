package com.walktape.app.core.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/** Second audible pass during overlap. Never populated by silent preparation. */
@Entity(tableName = "overlap_listening_state", foreignKeys = [ForeignKey(entity = ProfileEntity::class,
    parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE)])
data class OverlapListeningStateEntity(
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
    /** Retained after promotion to cover a crash before the queue snapshot catches up. */
    val promoted: Boolean,
    /** Outgoing pass receipt if the process dies before QueueStore saves the new owner. */
    @ColumnInfo(defaultValue = "NULL") val outgoingSnapshot: String? = null,
) {
    fun asPrimary() = ListeningStateEntity(profileId, mediaId, eventId, songId, title,
        artist, album, playedAtMs, listenedMs, emitted)
}

fun ListeningStateEntity.asOverlap(promoted: Boolean = false, outgoingSnapshot: String? = null) = OverlapListeningStateEntity(
    profileId, mediaId, eventId, songId, title, artist, album, playedAtMs, listenedMs, emitted, promoted, outgoingSnapshot)
