package com.walktape.app.core.net

import com.google.gson.JsonObject

data class ArtistDto(val id: String, val name: String, val coverArt: String?, val albumCount: Int)
data class AlbumDto(
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
)
data class SongDto(
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
data class GenreDto(val name: String, val songCount: Int, val albumCount: Int)
data class AlbumWithSongsDto(val album: AlbumDto, val songs: List<SongDto>)

internal fun JsonObject?.optString(name: String): String? =
    this?.get(name)?.takeIf { it.isJsonPrimitive }?.asString

internal fun JsonObject?.optInt(name: String, default: Int = 0): Int =
    this?.get(name)?.takeIf { it.isJsonPrimitive }?.asInt ?: default

internal fun JsonObject?.optLong(name: String, default: Long = 0L): Long =
    this?.get(name)?.takeIf { it.isJsonPrimitive }?.asLong ?: default

internal fun JsonObject?.optBool(name: String): Boolean =
    this?.get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false

internal fun JsonObject.obj(name: String): JsonObject? = get(name)?.takeIf { it.isJsonObject }?.asJsonObject

internal fun parseArtist(json: JsonObject): ArtistDto = ArtistDto(
    id = json.optString("id") ?: throw IllegalArgumentException(),
    name = json.optString("name") ?: throw IllegalArgumentException(),
    coverArt = json.optString("coverArt"),
    albumCount = json.optInt("albumCount"),
)

internal fun parseAlbum(json: JsonObject): AlbumDto = AlbumDto(
    id = json.optString("id") ?: throw IllegalArgumentException(),
    name = json.optString("name") ?: throw IllegalArgumentException(),
    artist = json.optString("artist"),
    artistId = json.optString("artistId"),
    coverArt = json.optString("coverArt"),
    songCount = json.optInt("songCount"),
    duration = json.optInt("duration"),
    year = json.get("year")?.takeIf { it.isJsonPrimitive }?.asInt,
    genre = json.optString("genre"),
    created = json.optString("created"),
    isCompilation = json.optBool("isCompilation"),
    starred = json.optString("starred"),
)

internal fun parseSong(json: JsonObject): SongDto {
    val albumArtists = json.get("albumArtists")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
    return SongDto(
        id = json.optString("id") ?: throw IllegalArgumentException(),
        parent = json.optString("parent"),
        albumId = json.optString("albumId"),
        artistId = json.optString("artistId"),
        title = json.optString("title") ?: throw IllegalArgumentException(),
        album = json.optString("album"),
        artist = json.optString("artist"),
        albumArtist = json.optString("displayAlbumArtist")?.takeIf { it.isNotBlank() } ?: albumArtists?.optString("name"),
        displayArtist = json.optString("displayArtist"),
        track = json.optInt("track"),
        discNumber = json.optInt("discNumber", 1),
        year = json.get("year")?.takeIf { it.isJsonPrimitive }?.asInt,
        genre = json.optString("genre"),
        coverArt = json.optString("coverArt"),
        duration = json.optInt("duration"),
        bitRate = json.optInt("bitRate"),
        suffix = json.optString("suffix"),
        contentType = json.optString("contentType"),
        size = json.optLong("size"),
        path = json.optString("path"),
        created = json.optString("created"),
        starred = json.optString("starred"),
        type = json.optString("type"),
    )
}
