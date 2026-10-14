package com.walktape.app.playback

import com.walktape.app.core.net.SongDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionSelectionTest {

    private fun song(id: String, duration: Int = 200) = SongDto(
        id = id, parent = null, albumId = null, artistId = null, title = "Title $id", album = "Album",
        artist = "Artist", albumArtist = null, displayArtist = null, track = 1, discNumber = 1,
        year = null, genre = null, coverArt = null, duration = duration, bitRate = 0, suffix = "mp3",
        contentType = null, size = 1, path = null, created = null, starred = null, type = null,
    )

    @Test
    fun dropsTheSourceSongAndUnusableEntriesPreservingServerRanking() {
        val selected = SuggestionSelection.candidates(
            listOf(song("source"), song("b"), song("zero", duration = 0), song("c"), song("source")),
            sourceSongId = "source",
        )
        assertEquals(listOf("b", "c"), selected.map { it.id })
    }

    @Test
    fun capsTheCountAtTheConfiguredLimit() {
        val many = (1..30).map { song("s$it") }
        assertEquals(15, SuggestionSelection.candidates(many, "none").size)
        assertEquals(3, SuggestionSelection.candidates(many, "none", limit = 3).map { it.id }.size)
        assertEquals(listOf("s1", "s2", "s3"), SuggestionSelection.candidates(many, "none", limit = 3).map { it.id })
    }

    @Test
    fun emptyAnswerStaysEmpty() {
        assertTrue(SuggestionSelection.candidates(emptyList(), "source").isEmpty())
        assertTrue(SuggestionSelection.candidates(listOf(song("source")), "source").isEmpty())
    }
}
