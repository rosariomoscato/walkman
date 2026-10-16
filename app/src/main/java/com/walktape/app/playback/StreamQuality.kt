package com.walktape.app.playback

/**
 * Stream representation selection (W09-T01): originals by default on Wi-Fi, an optional
 * server-transcoded copy on metered networks. The decision is pure and synchronous —
 * it reads persisted per-profile settings and the current connectivity state, never the
 * network — so offline-only stays a zero-request mode. Cache keys carry the selection
 * suffix so bytes of different codecs/bitrates are never mixed or concatenated.
 */
data class StreamSelection(
    /** "raw" for originals; null lets the server choose its transcoding format. */
    val format: String?,
    val maxBitRate: Int?,
    /** Empty for originals; unique per bitrate for reduced representations. */
    val cacheSuffix: String,
) {
    val reduced: Boolean get() = maxBitRate != null

    companion object {
        val ORIGINAL = StreamSelection("raw", null, "")
    }
}

fun streamSelection(mobileReduced: Boolean, reducedBitRate: Int, metered: Boolean): StreamSelection =
    if (mobileReduced && metered && reducedBitRate > 0)
        reducedBitRate.coerceIn(MIN_REDUCED_KBPS, MAX_REDUCED_KBPS).let {
            StreamSelection("opus", it, "opus-r$it")
        }
    else StreamSelection.ORIGINAL

const val MIN_REDUCED_KBPS = 64
const val MAX_REDUCED_KBPS = 320
val REDUCED_KBPS_CHOICES = listOf(96, 128, 192, 320)

/** Cache key of the concrete bytes representation for a song of a profile. */
fun streamCacheKey(profileId: Long, songId: String, selection: StreamSelection): String =
    if (selection.cacheSuffix.isEmpty()) "wtcache/$profileId/$songId"
    else "wtcache/$profileId/$songId?${selection.cacheSuffix}"

/** Extension for a downloaded reduced copy derived from the transfer's content type. */
fun reducedExtension(contentType: String?): String = when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
    "audio/ogg", "application/ogg", "audio/opus" -> "ogg"
    "audio/mpeg" -> "mp3"
    "audio/flac" -> "flac"
    "audio/mp4" -> "m4a"
    "audio/aac" -> "aac"
    "audio/wav" -> "wav"
    else -> "audio"
}
