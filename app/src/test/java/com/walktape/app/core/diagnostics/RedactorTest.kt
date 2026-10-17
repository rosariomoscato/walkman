package com.walktape.app.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun `redacts credentials embedded in urls`() {
        val input = "server http://admin:SuperSecret9@192.168.1.10:4533 reachable"
        val expected = "server http://admin:***@192.168.1.10:4533 reachable"
        assertEquals(expected, Redactor.redact(input))
    }

    @Test
    fun `redacts sensitive query parameters`() {
        val input = "request failed: /rest/ping?u=test&password=hunter2&v=1"
        val expected = "request failed: /rest/ping?u=***&password=***&v=1"
        assertEquals(expected, Redactor.redact(input))
    }

    @Test
    fun `redacts subsonic auth tokens`() {
        val input = "url /rest/stream?u=x&t=0123456789abcdef0123456789abcdef&s=abc123"
        val expected = "url /rest/stream?u=***&t=***&s=***"
        assertEquals(expected, Redactor.redact(input))
    }

    @Test
    fun `keeps ordinary values untouched`() {
        val input = "device.model: Moto G 55\nprofiles.configured: 0"
        assertEquals(input, Redactor.redact(input))
    }

    @Test
    fun `is case insensitive on parameter names`() {
        val input = "GET /x?TOKEN=abc&Secret=zz"
        val expected = "GET /x?TOKEN=***&Secret=***"
        assertEquals(expected, Redactor.redact(input))
    }

    // --- W11-T02 extensions ---

    @Test
    fun `redacts short openSubsonic auth parameters`() {
        val input = "call /rest/ping.view?u=demo&p=enc:1A2B3C4D&t=abcdef0123456789abcdef0123456789&v=1"
        val redacted = Redactor.redact(input)
        assertFalse(redacted.contains("demo"))
        assertFalse(redacted.contains("enc:"))
        assertFalse(redacted.contains("abcdef0123"))
        assertTrue(redacted.contains("v=1"))
    }

    @Test
    fun `redacts authorization and cookie header lines`() {
        val input = "Authorization: Bearer ghp_smallTokenValue123\nCookie: sid=abcdef0123456789"
        val redacted = Redactor.redact(input)
        assertFalse(redacted.contains("ghp_smallTokenValue123"))
        assertFalse(redacted.contains("sid=abcdef"))
        assertTrue(redacted.contains("Authorization: ***"))
        assertTrue(redacted.contains("Cookie: ***"))
    }

    @Test
    fun `redacts labeled secrets and credential references`() {
        val input = "profile encryptedPassword: QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZnaA== credentialId=cred-987-xyz"
        val redacted = Redactor.redact(input)
        assertFalse(redacted.contains("QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVphYmNkZWZnaA=="))
        assertFalse(redacted.contains("cred-987-xyz"))
        assertTrue(redacted.contains("encryptedPassword: ***"))
    }

    @Test
    fun `redacts long encoded blobs`() {
        val blob = "A".repeat(64) + "B".repeat(64)
        val redacted = Redactor.redact("payload $blob tail")
        assertFalse(redacted.contains(blob))
        assertTrue(redacted.contains("***"))
    }

    @Test
    fun `drops full url query strings`() {
        val input = "redirect https://example.com/path?token=xyz&u=bob"
        val expected = "redirect https://example.com/path?***"
        assertEquals(expected, Redactor.redact(input))
    }

    @Test
    fun `plain urls without query survive`() {
        val input = "docs https://docs.github.com/en/rest/releases page"
        assertEquals(input, Redactor.redact(input))
    }
}
