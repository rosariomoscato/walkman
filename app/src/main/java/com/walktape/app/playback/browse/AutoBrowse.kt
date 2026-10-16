package com.walktape.app.playback.browse

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Stable, profile-bound browse identifiers for Android Auto (W10-T01). These IDs address
 * library resources and are deliberately distinct from the per-occurrence queue UUIDs
 * ("queue/<uuid>") that [com.walktape.app.playback.QueueIdentity] assigns when media
 * actually enters the queue — one browsed song can become any number of queue occurrences.
 *
 * Format: `wtb/<profileId>/<category>[/<encodedResourceId>]`. Resource IDs are URL-encoded
 * because Navidrome IDs may contain arbitrary characters; parsing rejects anything that does
 * not decode cleanly or that belongs to another profile.
 */
object AutoBrowseIds {
    const val ROOT = "wtb-root"
    const val SCHEME = "wtb"

    const val KIND_ALBUMS = "albums"
    const val KIND_ARTISTS = "artists"
    const val KIND_SONGS = "songs"
    const val KIND_PLAYLISTS = "playlists"
    const val KIND_DOWNLOADS = "downloads"
    const val KIND_DOWNLOADED_SONGS = "dl-songs"
    const val KIND_ALBUM = "album"
    const val KIND_ARTIST = "artist"
    const val KIND_PLAYLIST = "playlist"
    const val KIND_DOWNLOAD_GROUP = "dl-group"
    const val KIND_SONG = "song"

    sealed interface Node {
        val profileId: Long

        /** Category lists and container nodes a browser can open. */
        sealed interface Container : Node

        data class Library(override val profileId: Long) : Container
        data class Page(override val profileId: Long, val parentId: String, val offset: Int) : Container

        data class Albums(override val profileId: Long) : Container
        data class Artists(override val profileId: Long) : Container
        data class Songs(override val profileId: Long) : Container
        data class Playlists(override val profileId: Long) : Container
        data class Downloads(override val profileId: Long) : Container
        data class DownloadedSongs(override val profileId: Long) : Container
        data class Album(override val profileId: Long, val albumId: String) : Container
        data class Artist(override val profileId: Long, val artistId: String) : Container
        data class Playlist(override val profileId: Long, val playlistId: String) : Container
        data class DownloadGroup(override val profileId: Long, val origin: String) : Container

        /** A single playable song leaf. */
        data class Song(override val profileId: Long, val songId: String) : Node
    }

    fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, "UTF-8").ifEmpty { null }
    } catch (_: IllegalArgumentException) {
        null
    }?.takeIf { id -> id.none { it.code < 0x20 } } // control chars never appear in real ids

    fun albums(profileId: Long) = "$SCHEME/$profileId/$KIND_ALBUMS"
    fun library(profileId: Long) = "$SCHEME/$profileId/library"
    fun page(profileId: Long, parentId: String, offset: Int) = "$SCHEME/$profileId/page/$offset/${encode(parentId)}"
    fun artists(profileId: Long) = "$SCHEME/$profileId/$KIND_ARTISTS"
    fun songs(profileId: Long) = "$SCHEME/$profileId/$KIND_SONGS"
    fun playlists(profileId: Long) = "$SCHEME/$profileId/$KIND_PLAYLISTS"
    fun downloads(profileId: Long) = "$SCHEME/$profileId/$KIND_DOWNLOADS"
    fun downloadedSongs(profileId: Long) = "$SCHEME/$profileId/$KIND_DOWNLOADED_SONGS"
    fun album(profileId: Long, albumId: String) = "$SCHEME/$profileId/$KIND_ALBUM/${encode(albumId)}"
    fun artist(profileId: Long, artistId: String) = "$SCHEME/$profileId/$KIND_ARTIST/${encode(artistId)}"
    fun playlist(profileId: Long, playlistId: String) = "$SCHEME/$profileId/$KIND_PLAYLIST/${encode(playlistId)}"
    fun downloadGroup(profileId: Long, origin: String) = "$SCHEME/$profileId/$KIND_DOWNLOAD_GROUP/${encode(origin)}"
    fun song(profileId: Long, songId: String) = "$SCHEME/$profileId/$KIND_SONG/${encode(songId)}"

    /** Parses a browse id; returns null for foreign or malformed identifiers. */
    fun parse(mediaId: String): Node? {
        if (mediaId == ROOT) return null // the root is served directly, never re-parsed as a child
        if (!mediaId.startsWith("$SCHEME/")) return null
        val parts = mediaId.split('/')
        if (parts.size < 3) return null
        val profileId = parts[1].toLongOrNull() ?: return null
        if (profileId <= 0) return null
        val resource = parts.drop(2)
        return when (resource.first()) {
            "page" -> {
                if (resource.size != 3) return null
                val offset = resource[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
                val parentId = decode(resource[2]) ?: return null
                // Do not recursively parse another continuation (untrusted browser input).
                if (parentId.split('/').getOrNull(2) == "page") return null
                val parent = parse(parentId) as? Node.Container ?: return null
                if (parent.profileId != profileId) return null
                Node.Page(profileId, parentId, offset)
            }
            "library" -> if (resource.size == 1) Node.Library(profileId) else null
            KIND_ALBUMS -> if (resource.size == 1) Node.Albums(profileId) else null
            KIND_ARTISTS -> if (resource.size == 1) Node.Artists(profileId) else null
            KIND_SONGS -> if (resource.size == 1) Node.Songs(profileId) else null
            KIND_PLAYLISTS -> if (resource.size == 1) Node.Playlists(profileId) else null
            KIND_DOWNLOADS -> if (resource.size == 1) Node.Downloads(profileId) else null
            KIND_DOWNLOADED_SONGS -> if (resource.size == 1) Node.DownloadedSongs(profileId) else null
            KIND_ALBUM -> container(profileId, resource, 1) { Node.Album(profileId, it) }
            KIND_ARTIST -> container(profileId, resource, 1) { Node.Artist(profileId, it) }
            KIND_PLAYLIST -> container(profileId, resource, 1) { Node.Playlist(profileId, it) }
            KIND_DOWNLOAD_GROUP -> container(profileId, resource, 1) { Node.DownloadGroup(profileId, it) }
            KIND_SONG -> container(profileId, resource, 1) { Node.Song(profileId, it) }
            else -> null
        }
    }

    /**
     * Extracts the single encoded resource id. An encoded slash (%2F) stays inside the
     * segment and is a legal id character; a literal slash would have split the segments
     * and produced a different part count, which the callers already reject.
     */
    private inline fun container(profileId: Long, resource: List<String>, index: Int,
        build: (String) -> Node): Node? {
        if (resource.size != index + 1) return null
        return build(decode(resource[index]) ?: return null)
    }

    /** Page/pageSize clamp shared by children and search results: null means "not a page". */
    fun window(page: Int, pageSize: Int): Pair<Int, Int>? {
        if (page < 0 || pageSize <= 0) return null
        val size = pageSize.coerceAtMost(MAX_PAGE_SIZE)
        val offset = page.toLong() * size
        if (offset > Int.MAX_VALUE) return null
        return Pair(size, offset.toInt())
    }

    const val MAX_PAGE_SIZE = 200
}

/**
 * Content URIs through which the Android Auto client reads cover art (W10-T01). The provider
 * only ever serves cached cover images of the *active* profile — never audio, database files
 * or authenticated Navidrome URLs — and enforces its own caller allowlist.
 */
object CoverArtUris {
    const val AUTHORITY = "com.walktape.app.art"
    const val DEFAULT_SIZE_PX = 512
    private const val MIN_SIZE_PX = 96
    private const val MAX_SIZE_PX = 1024

    fun uriString(profileId: Long, coverArtId: String, sizePx: Int = DEFAULT_SIZE_PX): String =
        "content://$AUTHORITY/$profileId/${AutoBrowseIds.encode(coverArtId)}/${sizePx.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)}"

    data class ArtRequest(val profileId: Long, val coverArtId: String, val sizePx: Int)

    /** Pure string parsing so URI shape stays unit-testable on the JVM. */
    fun parse(uri: String): ArtRequest? {
        if (!uri.startsWith("content://$AUTHORITY/")) return null
        val segments = uri.removePrefix("content://$AUTHORITY/").split('/')
        if (segments.size != 3) return null
        val profileId = segments[0].toLongOrNull() ?: return null
        if (profileId <= 0) return null
        val coverArtId = runCatching {
            URLDecoder.decode(segments[1], "UTF-8")
        }.getOrNull() ?: return null
        if (coverArtId.isEmpty() || coverArtId.any { it.code < 0x20 }) return null
        val size = segments[2].toIntOrNull() ?: return null
        return ArtRequest(profileId, coverArtId, size.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX))
    }
}
