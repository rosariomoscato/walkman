package com.walktape.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CassetteLabelTest {

    private fun entry(album: String?) = PlaybackQueueEntry("e", "m", "Title", "Artist", album, albumId = album)

    @Test fun sameTitleWithDifferentAlbumIdsIsStillMixed() {
        val a = entry("Greatest Hits")
        assertEquals(CassetteLabel.Kind.MIXTAPE,
            cassetteLabel(listOf(a, a.copy(albumId = "other-album")), null)?.kind)
    }

    @Test fun emptyQueueHasNoLabel() {
        assertNull(cassetteLabel(emptyList(), null))
        assertNull(cassetteLabel(emptyList(), "Road trip"))
    }

    @Test fun wholeQueueFromOneAlbumLabelsTheAlbum() {
        val label = cassetteLabel(listOf(entry("Blue Lines"), entry("Blue Lines")), null)
        assertEquals(CassetteLabel.Kind.ALBUM, label?.kind)
        assertEquals("Blue Lines", label?.text)
    }

    @Test fun mixedAlbumsBecomeMixtape() {
        val label = cassetteLabel(listOf(entry("A"), entry("B")), null)
        assertEquals(CassetteLabel.Kind.MIXTAPE, label?.kind)
        assertNull(label?.text)
    }

    @Test fun unknownAlbumOnSomeEntriesBecomesMixtape() {
        val label = cassetteLabel(listOf(entry("A"), entry(null)), null)
        assertEquals(CassetteLabel.Kind.MIXTAPE, label?.kind)
    }

    @Test fun playlistOriginWinsOverContentsAndSurvivesBlankAlbums() {
        val label = cassetteLabel(listOf(entry(null), entry("X")), "Road trip")
        assertEquals(CassetteLabel.Kind.PLAYLIST, label?.kind)
        assertEquals("Road trip", label?.text)
    }

    @Test fun blankOriginIsIgnored() {
        val label = cassetteLabel(listOf(entry("A"), entry("A")), "   ")
        assertEquals(CassetteLabel.Kind.ALBUM, label?.kind)
    }

    @Test fun repeatedAlbumNamesAcrossEntriesStayAlbum() {
        val label = cassetteLabel(List(5) { entry("Live in Rome") }, null)
        assertEquals(CassetteLabel.Kind.ALBUM, label?.kind)
        assertEquals("Live in Rome", label?.text)
    }
}
