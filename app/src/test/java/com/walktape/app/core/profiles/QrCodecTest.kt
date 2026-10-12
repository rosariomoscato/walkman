package com.walktape.app.core.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodecTest {
    private val payload = QrPayload("Casa", "https://music.example.org", "owner")

    @Test fun `round trip preserves fields`() {
        assertEquals(payload, QrCodec.decode(QrCodec.encode(payload)))
    }

    @Test fun `encoded payload never contains a password field`() {
        val encoded = QrCodec.encode(payload)
        assertFalse(encoded.contains("pass", ignoreCase = true))
        assertEquals(4, com.google.gson.JsonParser.parseString(encoded).asJsonObject.size())
    }

    @Test fun `bare url is accepted as url-only payload`() {
        assertEquals(
            QrPayload("", "https://music.example.org", ""),
            QrCodec.decode("  https://music.example.org ")
        )
    }

    @Test fun `invalid payloads are rejected`() {
        listOf(
            "", "   ",
            "not a url",
            "ftp://example.org",
            "{}",
            """{"v":2,"n":"x","u":"https://a","un":"b"}""",
            """{"v":1,"n":"x","u":"ftp://a","un":"b"}""",
            """{"v":1,"n":"x","u":"https://a"}""",
            """{"v":1,"n":"${"x".repeat(81)}","u":"https://a","un":"b"}""",
            """{"v":1,"n":"x","u":"https://a","un":"b"} trailing""",
            """{"v":"1","n":"x","u":"https://a","un":"b"}""",
            "https://" + "a".repeat(600) + ".org",
        ).forEach { raw ->
            assertNull("must reject: ${raw.take(40)}", QrCodec.decode(raw))
        }
    }

    @Test fun `minimal valid json payload decodes`() {
        assertEquals(
            QrPayload("x", "https://a", "b"),
            QrCodec.decode("""{"v":1,"n":"x","u":"https://a","un":"b"}""")
        )
        // Empty name is legitimate for URL-only QR codes: the user completes the profile.
        assertEquals(
            QrPayload("", "https://a", "b"),
            QrCodec.decode("""{"v":1,"n":"","u":"https://a","un":"b"}""")
        )
    }

    @Test fun `credentials remain redacted in toString`() {
        assertFalse(payload.toString().contains("example.org"))
    }
}
