package com.walktape.app.core.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in read-only integration. No secrets/requests/server messages in assertions or output. */
class RealServerTest {
    @Test fun `real Navidrome authentication extensions and incorrect password`() = runBlocking {
        val path = System.getProperty("walktape.testServerFile")
        assumeTrue("Local server verification is opt-in", path != null)
        val config = File(path!!).readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val address = ServerAddress.parse(config.getValue("ND_URL"), true)!!
        val client = OpenSubsonicClient(address, Credentials(config.getValue("ND_USER"), config.getValue("ND_PASS")))
        val ping = client.ping()
        assertTrue("Real server ping must succeed (details suppressed)", ping is ApiResult.Success)
        assertTrue("Server must identify as Navidrome", (ping as ApiResult.Success).value.type == "navidrome")
        assertTrue("Real server extensions must load", client.extensions() is ApiResult.Success)
        val invalid = OpenSubsonicClient(address, Credentials(config.getValue("ND_USER"), "invalid-${randomSalt()}"))
        val result = invalid.ping()
        assertTrue("Incorrect password must be rejected", result is ApiResult.Failure &&
            result.error is ApiError.Protocol && result.error.kind == ProtocolErrorKind.AUTHENTICATION)
    }
}
