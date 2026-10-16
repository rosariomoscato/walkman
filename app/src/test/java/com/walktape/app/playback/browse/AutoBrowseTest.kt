package com.walktape.app.playback.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** W10-T01: browse identifiers are stable, profile-bound and reject foreign/malformed ids. */
class AutoBrowseIdsTest {
    private val tricky = listOf("al-42", "id with spaces", "slash/inside", "uni-çè", "a%20b+c", "pl:mf-7")

    @Test fun `round trips every node kind with plain and special resource ids`() {
        tricky.forEach { id ->
            assertEquals(AutoBrowseIds.Node.Album(7, id),
                AutoBrowseIds.parse(AutoBrowseIds.album(7, id)))
            assertEquals(AutoBrowseIds.Node.Artist(7, id),
                AutoBrowseIds.parse(AutoBrowseIds.artist(7, id)))
            assertEquals(AutoBrowseIds.Node.Playlist(7, id),
                AutoBrowseIds.parse(AutoBrowseIds.playlist(7, id)))
            assertEquals(AutoBrowseIds.Node.DownloadGroup(7, id),
                AutoBrowseIds.parse(AutoBrowseIds.downloadGroup(7, id)))
            assertEquals(AutoBrowseIds.Node.Song(7, id),
                AutoBrowseIds.parse(AutoBrowseIds.song(7, id)))
        }
        assertEquals(AutoBrowseIds.Node.Albums(3), AutoBrowseIds.parse(AutoBrowseIds.albums(3)))
        assertEquals(AutoBrowseIds.Node.Artists(3), AutoBrowseIds.parse(AutoBrowseIds.artists(3)))
        assertEquals(AutoBrowseIds.Node.Songs(3), AutoBrowseIds.parse(AutoBrowseIds.songs(3)))
        assertEquals(AutoBrowseIds.Node.Playlists(3), AutoBrowseIds.parse(AutoBrowseIds.playlists(3)))
        assertEquals(AutoBrowseIds.Node.Downloads(3), AutoBrowseIds.parse(AutoBrowseIds.downloads(3)))
        assertEquals(AutoBrowseIds.Node.DownloadedSongs(3),
            AutoBrowseIds.parse(AutoBrowseIds.downloadedSongs(3)))
    }

    @Test fun `ids belong to exactly one profile`() {
        assertEquals(2L, (AutoBrowseIds.parse(AutoBrowseIds.album(2, "x")) as AutoBrowseIds.Node.Album).profileId)
        val other = AutoBrowseIds.parse(AutoBrowseIds.album(2, "x"))
        assertTrue(other is AutoBrowseIds.Node.Album && other.profileId != 1L)
    }

    @Test fun `foreign and malformed ids are rejected`() {
        listOf(
            "", "wtb-root", "wtb", "wtb/", "wtb/1", "wtb/1/", "wtb/x/albums",
            "wtb/0/albums", "wtb/1/unknown", "wtb/1/album", "wtb/1/album/a/b",
            "wtb/1/album//", "wtb/1/song/%00", "queue/uuid-1", "https://server/x",
        ).forEach { id -> assertNull(id, AutoBrowseIds.parse(id)) }
    }

    @Test fun `pagination windows clamp page size and compute offsets`() {
        assertEquals(Pair(200, 0), AutoBrowseIds.window(0, 10_000))
        assertEquals(Pair(50, 100), AutoBrowseIds.window(2, 50))
        assertNull(AutoBrowseIds.window(-1, 50))
        assertNull(AutoBrowseIds.window(0, 0))
        assertNull(AutoBrowseIds.window(0, -5))
        assertNull(AutoBrowseIds.window(Int.MAX_VALUE, 200))
    }

    @Test fun `continuations remain bound to the same profile without nesting`() {
        val parent = AutoBrowseIds.songs(7)
        assertEquals(AutoBrowseIds.Node.Page(7, parent, 200), AutoBrowseIds.parse(AutoBrowseIds.page(7, parent, 200)))
        assertNull(AutoBrowseIds.parse(AutoBrowseIds.page(8, parent, 200)))
        assertNull(AutoBrowseIds.parse(AutoBrowseIds.page(7, AutoBrowseIds.page(7, parent, 200), 400)))
    }
}

/** W10-T01: cover URIs expose only profile-bound art requests. */
class CoverArtUrisTest {
    @Test fun `round trips profile art id and clamped size`() {
        val parsed = CoverArtUris.parse(CoverArtUris.uriString(4, "mf-42"))
        assertEquals(4L, parsed?.profileId)
        assertEquals("mf-42", parsed?.coverArtId)
        assertEquals(CoverArtUris.DEFAULT_SIZE_PX, parsed?.sizePx)
        assertEquals(96, CoverArtUris.parse(CoverArtUris.uriString(4, "a", 1))?.sizePx)
        assertEquals(1024, CoverArtUris.parse(CoverArtUris.uriString(4, "a", 99_999))?.sizePx)
        val tricky = CoverArtUris.parse(CoverArtUris.uriString(4, "art with/spaces è"))
        assertEquals("art with/spaces è", tricky?.coverArtId)
    }

    @Test fun `malformed cover uris are rejected`() {
        listOf(
            "content://other.authority/1/a/512",
            "content://com.walktape.app.art/",
            "content://com.walktape.app.art/1",
            "content://com.walktape.app.art/1/a",
            "content://com.walktape.app.art/1/a/512/extra",
            "content://com.walktape.app.art/0/a/512",
            "content://com.walktape.app.art/1//512",
            "content://com.walktape.app.art/1/a/notanumber",
        ).forEach { uri -> assertNull(uri, CoverArtUris.parse(uri)) }
    }
}

/** W10-T01: localized subtitles stay honest when artist/year are missing. */
class AutoSubtitleTest {
    @Test fun `subtitle joins present parts only`() {
        assertEquals("Artist • 1999", AutoBrowseItems.subtitle("Artist", 1999))
        assertEquals("Artist", AutoBrowseItems.subtitle("Artist", null))
        assertEquals("1999", AutoBrowseItems.subtitle(" ", 1999))
        assertNull(AutoBrowseItems.subtitle(null, null))
        assertNull(AutoBrowseItems.subtitle("", 0))
    }
}
