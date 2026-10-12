package com.walktape.app.core.diagnostics

import org.junit.Assert.assertEquals
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
        val expected = "request failed: /rest/ping?u=test&password=***&v=1"
        assertEquals(expected, Redactor.redact(input))
    }

    @Test
    fun `redacts subsonic auth tokens`() {
        val input = "url /rest/stream?u=x&t=0123456789abcdef0123456789abcdef&s=abc123"
        val expected = "url /rest/stream?u=x&t=***&s=abc123"
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
}
