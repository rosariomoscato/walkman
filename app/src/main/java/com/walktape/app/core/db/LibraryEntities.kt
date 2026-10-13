package com.walktape.app.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(tableName = "artist", primaryKeys = ["profileId", "id"])
data class ArtistEntity(
    val profileId: Long,
    val id: String,
    val name: String,
    val coverArt: String?,
    val albumCount: Int,
    @ColumnInfo(defaultValue = "NULL") val starred: String? = null,
)

@Entity(
    tableName = "album",
    primaryKeys = ["profileId", "id"],
    indices = [
        Index("profileId", "artistId"),
        Index("profileId", "created"),
    ]
)
data class AlbumEntity(
    val profileId: Long,
    val id: String,
    val name: String,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int,
    val duration: Int,
    val year: Int?,
    val genre: String?,
    val created: String?,
    val isCompilation: Boolean,
    val starred: String?,
    @ColumnInfo(defaultValue = "NULL") val played: String? = null,
)

@Entity(
    tableName = "song",
    primaryKeys = ["profileId", "id"],
    indices = [
        Index("profileId", "albumId"),
        Index("profileId", "artistId"),
        Index("profileId", "title"),
    ]
)
data class SongEntity(
    val profileId: Long,
    val id: String,
    val parent: String?,
    val albumId: String?,
    val artistId: String?,
    val title: String,
    val album: String?,
    val artist: String?,
    val albumArtist: String?,
    val displayArtist: String?,
    val track: Int,
    val discNumber: Int,
    val year: Int?,
    val genre: String?,
    val coverArt: String?,
    val duration: Int,
    val bitRate: Int,
    val suffix: String?,
    val contentType: String?,
    val size: Long,
    val path: String?,
    val created: String?,
    val starred: String?,
    val type: String?,
)

@Entity(tableName = "genre", primaryKeys = ["profileId", "id"])
data class GenreEntity(
    val profileId: Long,
    val id: String,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
)

@Entity(tableName = "library_state", primaryKeys = ["profileId"])
data class LibraryStateEntity(
    val profileId: Long,
    val lastSyncAt: Long,
    val lastSyncOk: Boolean,
    val artistCount: Int,
    val albumCount: Int,
    val songCount: Int,
)
