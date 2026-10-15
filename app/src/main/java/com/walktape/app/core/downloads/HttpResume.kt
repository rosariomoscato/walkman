package com.walktape.app.core.downloads

/** A 206 must describe exactly the requested tail of the same original file. */
object HttpResume {
    data class Tail(val start: Long, val end: Long, val total: Long)

    fun tail(header: String?, requestedOffset: Long, bodyLength: Long): Tail? {
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header ?: "") ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        if (start != requestedOffset || end < start || total <= end || end != total - 1) return null
        if (bodyLength >= 0 && bodyLength != end - start + 1) return null
        return Tail(start, end, total)
    }

    fun strongEtag(value: String?): String? = value?.takeIf {
        it.startsWith('"') && it.endsWith('"') && !it.contains('\n') && !it.contains('\r')
    }
}
