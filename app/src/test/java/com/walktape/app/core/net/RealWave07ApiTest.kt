package com.walktape.app.core.net

import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Only the explicitly dedicated test account; newly created playlists are deleted in finally. */
class RealWave07ApiTest {
    @Test fun `dedicated server account playlist CRUD duplicates order favorites and lyrics`() = runBlocking {
        val path = System.getProperty("walktape.testServerFile")
        assumeTrue(path != null)
        val config = File(path!!).readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        require(config.getValue("ND_USER") == "walktape-test")
        val client = OpenSubsonicClient(ServerAddress.parse(config.getValue("ND_URL"), true)!!,
            Credentials(config.getValue("ND_USER"), config.getValue("ND_PASS")))
        val albums = client.albumList("newest", 1, 0)
        assertTrue("Test server album list must load: $albums", albums is ApiResult.Success)
        val sample = client.album((albums as ApiResult.Success).value.first().id)
        assertTrue("Test server sample must load: $sample", sample is ApiResult.Success)
        val songs = (sample as ApiResult.Success).value.songs.take(2)
        require(songs.size == 2)
        val name = "wt-test-w07-${UUID.randomUUID()}"
        val created = (client.createPlaylist(name, listOf(songs[0].id, songs[1].id, songs[0].id)) as ApiResult.Success).value
        val id = created.playlist.id
        try {
            assertEquals(listOf(songs[0].id, songs[1].id, songs[0].id), created.entries.map { it.id })
            assertFalse(created.playlist.readOnly)
            assertTrue(client.updatePlaylist(id, name = "$name-renamed") is ApiResult.Success)
            assertTrue(client.updatePlaylist(id, songIdsToAdd = listOf(songs[1].id, songs[0].id, songs[0].id),
                songIndexesToRemove = listOf(0, 1, 2)) is ApiResult.Success)
            val reordered = (client.playlist(id) as ApiResult.Success).value
            assertEquals("$name-renamed", reordered.playlist.name)
            assertEquals(listOf(songs[1].id, songs[0].id, songs[0].id), reordered.entries.map { it.id })
            assertTrue(client.updatePlaylist(id, songIndexesToRemove = listOf(1)) is ApiResult.Success)
            assertEquals(listOf(songs[1].id, songs[0].id), (client.playlist(id) as ApiResult.Success).value.entries.map { it.id })
            assertTrue(client.updatePlaylist(id, songIdsToAdd = listOf(songs[0].id)) is ApiResult.Success)
            assertEquals(3, (client.playlist(id) as ApiResult.Success).value.entries.size)
            assertTrue(client.playlist("__walktape_nonexistent_test__") is ApiResult.Failure)
            val s = songs[0]
            val original = s.starred != null
            try {
                assertTrue(client.star(songIds = listOf(s.id)) is ApiResult.Success)
                val marked = (client.request("getSong", listOf("id" to s.id)) as ApiResult.Success).value.obj("song")!!
                assertNotNull(marked.optString("starred"))
                assertTrue(client.unstar(songIds = listOf(s.id)) is ApiResult.Success)
                val unmarked = (client.request("getSong", listOf("id" to s.id)) as ApiResult.Success).value.obj("song")!!
                assertNull(unmarked.optString("starred"))
            } finally { if (original) client.star(songIds = listOf(s.id)) else client.unstar(songIds = listOf(s.id)) }
            val lyrics = client.lyrics("s4O9TKx8oDM7Uh2IkntD09")
            assertTrue(lyrics is ApiResult.Success && lyrics.value.any { it.lines.any { l -> l.value.isNotBlank() } })
        } finally {
            assertTrue(client.deletePlaylist(id) is ApiResult.Success)
            assertTrue((client.playlists() as ApiResult.Success).value.none { it.id == id })
        }
    }
}
