package com.walktape.app.playback

import com.walktape.app.core.net.Credentials
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class SubsonicStreamTest {
    @Test fun `queue uri round-trips plain and special song ids`() {
        listOf("abc123", "mf-42", "id with spaces", "slash/inside", "uni-çè", "a%20b+c").forEach { id ->
            assertEquals(id, SubsonicStream.songId(SubsonicStream.uri(1, id), 1))
            assertNull(SubsonicStream.songId(SubsonicStream.uri(2, id), 1))
        }
    }

    @Test fun `foreign and malformed uris are not queue entries`() {
        listOf(
            "https://server/rest/stream.view?id=x",
            "wtstream://album/x",
            "wtstream://song",
            "wtstream://song/",
            "wtstream://song/a/b",
            "",
        ).forEach { uri -> assertNull(SubsonicStream.songId(uri, 1)) }
    }

    @Test fun `queue uri uses the custom scheme only`() {
        val uri = SubsonicStream.uri(1, "abc")
        assertTrue(uri.startsWith("wtstream://song/"))
    }
}

class StreamUrlTest {
    private val client = OpenSubsonicClient(
        ServerAddress.parse("https://server.example", false)!!,
        Credentials("user", "password"),
    )

    private fun md5(value: String) = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun `stream url authenticates with token and salt without json envelope`() {
        val url: HttpUrl = client.streamUrl("mf-42", "cafe01")
        assertEquals("/rest/stream.view", url.encodedPath)
        assertEquals("user", url.queryParameter("u"))
        assertEquals(md5("passwordcafe01"), url.queryParameter("t"))
        assertEquals("cafe01", url.queryParameter("s"))
        assertEquals("1.16.1", url.queryParameter("v"))
        assertEquals("WalkTape", url.queryParameter("c"))
        assertEquals("mf-42", url.queryParameter("id"))
        assertEquals("raw", url.queryParameter("format"))
        assertNull(url.queryParameter("f"))
        assertNull(url.queryParameter("maxBitRate"))
    }

    @Test fun `stream url carries quality overrides when requested`() {
        val url = client.streamUrl("x", "s1", format = null, maxBitRate = 192)
        assertNull(url.queryParameter("format"))
        assertEquals("192", url.queryParameter("maxBitRate"))
    }

    @Test fun `token changes with the salt`() {
        val a = client.streamUrl("x", "salt-a").queryParameter("t")
        val b = client.streamUrl("x", "salt-b").queryParameter("t")
        assertTrue(a != null && b != null && a != b)
        assertEquals(md5("passwordsalt-a"), a)
    }
}
