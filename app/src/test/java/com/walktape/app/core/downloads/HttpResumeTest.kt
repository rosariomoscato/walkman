package com.walktape.app.core.downloads

import org.junit.Assert.*
import org.junit.Test

class HttpResumeTest {
    @Test fun validatesTailAndDoesNotDoubleCountOffset() {
        val tail = HttpResume.tail("bytes 400-999/1000", 400, 600)!!
        assertEquals(1000L, tail.total)
        assertEquals(400L, tail.start)
    }
    @Test fun refusesWrongOffsetLengthAndTruncatedRanges() {
        assertNull(HttpResume.tail("bytes 399-999/1000", 400, 601))
        assertNull(HttpResume.tail("bytes 400-999/1000", 400, 599))
        assertNull(HttpResume.tail("bytes 400-899/1000", 400, 500))
        assertNull(HttpResume.tail("bytes 400-999/*", 400, 600))
        assertNull(HttpResume.tail("bytes 99999999999999999999-999/1000", 400, 600))
    }
    @Test fun acceptsOnlyStrongValidators() {
        assertEquals("\"v1\"", HttpResume.strongEtag("\"v1\""))
        assertNull(HttpResume.strongEtag("W/\"v1\""))
        assertNull(HttpResume.strongEtag(null))
    }
}
