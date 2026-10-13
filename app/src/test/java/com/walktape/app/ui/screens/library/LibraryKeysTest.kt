package com.walktape.app.ui.screens.library

import com.walktape.app.core.db.ArtistEntity
import com.walktape.app.core.db.GenreEntity
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryKeysTest {
    @Test fun `key remains safe across tab refresh and profile switches`() {
        val artist = ArtistEntity(1, "same", "Artist", null, 0)
        val genre = GenreEntity(1, "same", "Genre", 0, 0)
        assertNotEquals(libraryItemKey(artist), libraryItemKey(genre))
        assertNotEquals(libraryItemKey(artist), libraryItemKey(artist.copy(profileId = 2)))
    }
}
