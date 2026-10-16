package com.walktape.app.playback.dsp

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.walktape.app.core.settings.ReplayGainMode

/**
 * W09-T03: reads the ReplayGain tags actually exposed by the Media3 extractors of the
 * running version — ID3 TXXX frames for MP3 and Vorbis comments for FLAC — and nothing
 * else. Missing tags stay null: the caller applies a neutral 0 dB gain instead of
 * pretending a normalisation happened.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object ReplayGainParser {
    data class Info(val mode: ReplayGainMode = ReplayGainMode.OFF, val hasTags: Boolean = false,
        val gainDb: Float = 0f)

    data class Tags(
        val trackGainDb: Float? = null,
        val albumGainDb: Float? = null,
        val trackPeak: Float? = null,
        val albumPeak: Float? = null,
    )

    fun parse(metadata: Metadata?): Tags {
        if (metadata == null) return Tags()
        var trackGain: Float? = null
        var albumGain: Float? = null
        var trackPeak: Float? = null
        var albumPeak: Float? = null
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is TextInformationFrame -> {
                    val key = entry.description?.trim()?.uppercase()
                    val value = entry.values.firstOrNull()
                    when (key) {
                        "REPLAYGAIN_TRACK_GAIN" -> trackGain = trackGain ?: parseDb(value)
                        "REPLAYGAIN_ALBUM_GAIN" -> albumGain = albumGain ?: parseDb(value)
                        "REPLAYGAIN_TRACK_PEAK" -> trackPeak = trackPeak ?: parsePeak(value)
                        "REPLAYGAIN_ALBUM_PEAK" -> albumPeak = albumPeak ?: parsePeak(value)
                    }
                }
                is VorbisComment -> {
                    val key = entry.key.trim().uppercase()
                    when (key) {
                        "REPLAYGAIN_TRACK_GAIN" -> trackGain = trackGain ?: parseDb(entry.value)
                        "REPLAYGAIN_ALBUM_GAIN" -> albumGain = albumGain ?: parseDb(entry.value)
                        "REPLAYGAIN_TRACK_PEAK" -> trackPeak = trackPeak ?: parsePeak(entry.value)
                        "REPLAYGAIN_ALBUM_PEAK" -> albumPeak = albumPeak ?: parsePeak(entry.value)
                    }
                }
            }
        }
        return Tags(trackGain, albumGain, trackPeak, albumPeak)
    }

    /** Gain selected for the mode; album mode falls back to track tags, then to neutral. */
    fun selectGainDb(tags: Tags, mode: ReplayGainMode): Float =
        when (mode) {
            ReplayGainMode.OFF -> 0f
            ReplayGainMode.TRACK -> tags.trackGainDb ?: 0f
            ReplayGainMode.ALBUM -> tags.albumGainDb ?: tags.trackGainDb ?: 0f
        }

    /** Peak matching the tag the gain was taken from (clip cap). */
    fun selectPeak(tags: Tags, mode: ReplayGainMode): Float? =
        when (mode) {
            ReplayGainMode.OFF -> null
            ReplayGainMode.TRACK -> tags.trackPeak
            ReplayGainMode.ALBUM -> if (tags.albumGainDb != null) tags.albumPeak else tags.trackPeak
        }

    /** Parses "-6.51 dB" / "-6.51 dB" / "+3.2"; null for anything unparseable. */
    fun parseDb(value: String?): Float? = parseNumber(value, "db")

    /** Linear peaks may exceed 1 (intersample/decoder overs); retain these for attenuation. */
    fun parsePeak(value: String?): Float? {
        val parsed = parseNumber(value, "db") ?: return null
        return parsed.takeIf { it.isFinite() && it > 0f && it <= 64f }
    }

    private fun parseNumber(value: String?, unit: String): Float? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.trim().lowercase().removeSuffix(unit).trim()
        return cleaned.toFloatOrNull()?.takeIf { it.isFinite() }
    }
}
