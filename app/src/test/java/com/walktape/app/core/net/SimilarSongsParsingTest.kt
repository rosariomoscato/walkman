package com.walktape.app.core.net

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarSongsParsingTest {

    @Test
    fun parsesTheSongArrayWithFullMetadata() {
        val root = JsonParser.parseString(
            """{"similarSongs2":{"song":[
                {"id":"a","title":"Alpha","artist":"Artist A","duration":120},
                {"id":"b","title":"Beta","artist":"Artist B","duration":90}
            ]}}"""
        ).asJsonObject
        val songs = OpenSubsonicClient.similarSongsFrom(root)
        assertEquals(listOf("a", "b"), songs.map { it.id })
        assertEquals(120, songs[0].duration)
        assertEquals("Artist A", songs[0].artist)
    }

    @Test
    fun emptyOrMissingSongArrayYieldsAnEmptyList() {
        val empty = JsonParser.parseString("""{"similarSongs2":{"song":[]}}""").asJsonObject
        assertTrue(OpenSubsonicClient.similarSongsFrom(empty).isEmpty())
        val noArray = JsonParser.parseString("""{"similarSongs2":{}}""").asJsonObject
        assertTrue(OpenSubsonicClient.similarSongsFrom(noArray).isEmpty())
    }

    @Test
    fun missingContainerIsAProtocolFailure() {
        val missing = JsonParser.parseString("""{"status":"ok"}""").asJsonObject
        assertThrows(IllegalArgumentException::class.java) {
            OpenSubsonicClient.similarSongsFrom(missing)
        }
    }
}
