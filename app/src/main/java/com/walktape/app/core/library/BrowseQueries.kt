package com.walktape.app.core.library

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

data class LibraryFilter(val query: String = "", val genre: String? = null, val year: Int? = null,
                         val offlineOnly: Boolean = false)

enum class BrowseKind(val table: String, val title: String, val subtitle: String?) {
    ALBUM("album", "name", "artist"), ARTIST("artist", "name", null),
    SONG("song", "title", "artist"), GENRE("genre", "name", null),
    PLAYLIST("playlist", "name", "owner")
}

object BrowseQueries {
    fun list(kind: BrowseKind, profileId: Long, filter: LibraryFilter, sort: String): SupportSQLiteQuery {
        val (where, args) = conditions(kind, profileId, filter)
        val order = when (kind) {
            BrowseKind.ALBUM -> when (sort) {
                "artist" -> "artist COLLATE NOCASE, name COLLATE NOCASE"
                "year" -> "year IS NULL, year, name COLLATE NOCASE"
                "recent" -> "created DESC, name COLLATE NOCASE"
                else -> "name COLLATE NOCASE"
            }
            BrowseKind.SONG -> when (sort) {
                "artist" -> "artist COLLATE NOCASE, album COLLATE NOCASE, discNumber, track"
                "album" -> "album COLLATE NOCASE, discNumber, track"
                "duration" -> "duration, title COLLATE NOCASE"
                else -> "title COLLATE NOCASE"
            }
            BrowseKind.PLAYLIST -> if (sort == "changed") "changed IS NULL, changed DESC, name COLLATE NOCASE" else "name COLLATE NOCASE"
            else -> "name COLLATE NOCASE"
        }
        return SimpleSQLiteQuery("SELECT e.* FROM ${kind.table} e WHERE $where ORDER BY $order, id", args.toTypedArray())
    }

    fun search(profileId: Long, filter: LibraryFilter): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val branches = BrowseKind.entries.map { kind ->
            val (where, values) = conditions(kind, profileId, filter)
            args.addAll(values)
            "SELECT '${kind.name}' AS kind,e.profileId,e.id,e.${kind.title} AS title,${kind.subtitle?.let { "e.$it" } ?: "NULL"} AS subtitle FROM ${kind.table} e WHERE $where"
        }
        return SimpleSQLiteQuery(branches.joinToString(" UNION ALL ") + " ORDER BY title COLLATE NOCASE,kind,id", args.toTypedArray())
    }

    private fun conditions(kind: BrowseKind, profileId: Long, filter: LibraryFilter): Pair<String, List<Any>> {
        val args = mutableListOf<Any>(profileId)
        val clauses = mutableListOf("e.profileId=?")
        val pattern = "%${filter.query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        val fields = when (kind) {
            BrowseKind.SONG -> listOf("title", "artist", "album", "albumArtist", "displayArtist")
            BrowseKind.ALBUM -> listOf("name", "artist")
            BrowseKind.PLAYLIST -> listOf("name", "owner", "comment")
            else -> listOf("name")
        }
        clauses += fields.joinToString(" OR ", "(", ")") { args.add(pattern); "e.$it LIKE ? ESCAPE '\\'" }
        when (kind) {
            BrowseKind.SONG, BrowseKind.ALBUM -> {
                filter.genre?.let { clauses += "e.genre=?"; args.add(it) }
                filter.year?.let { clauses += "e.year=?"; args.add(it) }
                if (filter.offlineOnly) clauses += if (kind == BrowseKind.SONG) local("e.id", "e.profileId")
                    else "e.songCount>0 AND e.songCount=(SELECT COUNT(*) FROM song s WHERE s.profileId=e.profileId AND s.albumId=e.id) AND NOT EXISTS(SELECT 1 FROM song s WHERE s.profileId=e.profileId AND s.albumId=e.id AND NOT ${local("s.id", "s.profileId")})"
            }
            BrowseKind.ARTIST, BrowseKind.GENRE, BrowseKind.PLAYLIST -> {
                if (filter.genre != null || filter.year != null || filter.offlineOnly) {
                    val entry = kind == BrowseKind.PLAYLIST
                    val related = mutableListOf(if (entry) "s.playlistId=e.id" else if (kind == BrowseKind.GENRE) "s.genre=e.name" else "(s.artistId=e.id OR EXISTS(SELECT 1 FROM album a WHERE a.profileId=s.profileId AND a.id=s.albumId AND a.artistId=e.id))")
                    filter.genre?.let { related += "s.genre=?"; args.add(it) }
                    filter.year?.let { related += "s.year=?"; args.add(it) }
                    if (filter.offlineOnly) related += local(if (entry) "s.songId" else "s.id", "s.profileId")
                    clauses += "EXISTS(SELECT 1 FROM ${if (entry) "playlist_entry" else "song"} s WHERE s.profileId=e.profileId AND ${related.joinToString(" AND ")})"
                    if (entry && filter.offlineOnly) clauses += "e.songCount>0 AND e.songCount=(SELECT COUNT(*) FROM playlist_entry s WHERE s.profileId=e.profileId AND s.playlistId=e.id) AND NOT EXISTS(SELECT 1 FROM playlist_entry s WHERE s.profileId=e.profileId AND s.playlistId=e.id AND NOT ${local("s.songId", "s.profileId")})"
                }
            }
        }
        return clauses.joinToString(" AND ") to args
    }

    private fun local(id: String, profile: String) =
        "EXISTS(SELECT 1 FROM local_audio l WHERE l.profileId=$profile AND l.songId=$id AND l.bytes>0)"
}
