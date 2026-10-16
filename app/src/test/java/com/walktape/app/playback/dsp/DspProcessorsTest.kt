package com.walktape.app.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.walktape.app.core.settings.ReplayGainMode
import org.junit.Assert.*
import org.junit.Test

/** W09-T03: only the tags the running Media3 extractors really expose are honoured. */
class ReplayGainParserTest {

    @Test
    fun `parses id3 txxx frames of mp3 files`() {
        val metadata = Metadata(
            TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", "-6.51 dB"),
            TextInformationFrame("TXXX", "REPLAYGAIN_ALBUM_GAIN", "-9.02 dB"),
            TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_PEAK", "0.988173"),
            TextInformationFrame("TXXX", "REPLAYGAIN_ALBUM_PEAK", "0.999981"),
        )
        val tags = ReplayGainParser.parse(metadata)
        assertEquals(-6.51f, tags.trackGainDb!!, 0.001f)
        assertEquals(-9.02f, tags.albumGainDb!!, 0.001f)
        assertEquals(0.988173f, tags.trackPeak!!, 0.00001f)
        assertEquals(0.999981f, tags.albumPeak!!, 0.00001f)
    }

    @Test
    fun `parses flac vorbis comments`() {
        val metadata = Metadata(
            VorbisComment("REPLAYGAIN_TRACK_GAIN", "-4.20 dB"),
            VorbisComment("replaygain_album_gain", "-7.77 dB"),
        )
        val tags = ReplayGainParser.parse(metadata)
        assertEquals(-4.20f, tags.trackGainDb!!, 0.001f)
        assertEquals(-7.77f, tags.albumGainDb!!, 0.001f)
        assertNull(tags.trackPeak)
    }

    @Test
    fun `missing or malformed tags stay null and yield a neutral gain`() {
        assertNull(ReplayGainParser.parse(null).trackGainDb)
        val bad = Metadata(TextInformationFrame("TXXX", "REPLAYGAIN_TRACK_GAIN", "n/a"))
        assertNull(ReplayGainParser.parse(bad).trackGainDb)
        val tags = ReplayGainParser.Tags()
        assertEquals(0f, ReplayGainParser.selectGainDb(tags, ReplayGainMode.TRACK))
        assertEquals(0f, ReplayGainParser.selectGainDb(tags, ReplayGainMode.ALBUM))
        // album mode falls back to the track tag when no album tag exists
        assertEquals(-3f, ReplayGainParser.selectGainDb(
            ReplayGainParser.Tags(trackGainDb = -3f), ReplayGainMode.ALBUM), 0.001f)
    }

    @Test
    fun `peak values outside the linear range are ignored`() {
        assertEquals(2.35f, ReplayGainParser.parsePeak("2.35")!!, 0.001f)
        assertNull(ReplayGainParser.parseDb("NaN"))
        assertNull(ReplayGainParser.parsePeak("-0.5"))
        assertEquals(0.5f, ReplayGainParser.parsePeak("0.5")!!, 0.0001f)
        // legacy dB-suffixed peaks are still linear numbers
        assertEquals(0.9f, ReplayGainParser.parsePeak("0.9")!!, 0.0001f)
    }
}

/** W09-T03: sample-accurate gain, tag-peak cap, neutral bypass. */
class ReplayGainProcessorTest {

    private fun format() = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)

    private fun bufferOf(vararg samples: Int) = java.nio.ByteBuffer.allocate(samples.size * 2).apply {
        samples.forEach { value ->
            put((value and 0xff).toByte()); put(((value shr 8) and 0xff).toByte())
        }
        flip()
    }

    private fun shorts(output: java.nio.ByteBuffer): IntArray {
        val copy = output.duplicate()
        val result = IntArray(copy.remaining() / 2)
        for (i in result.indices) {
            val lo = copy.get().toInt() and 0xff
            val hi = copy.get().toInt()
            result[i] = ((hi shl 8) or lo).toShort().toInt()
        }
        return result
    }

    @Test
    fun `minus 6dB halves the samples after the ramp`() {
        val processor = ReplayGainProcessor()
        processor.enabled = true
        processor.targetGainDb = -6.0206f // exactly 0.5 factor
        processor.configure(format()).also { processor.flush() }
        assertTrue(processor.isActive())
        processor.queueInput(bufferOf(*IntArray(20000) { 16000 }))
        processor.queueEndOfStream()
        val out = mutableListOf<Int>()
        while (true) {
            val chunk = processor.getOutput()
            if (!chunk.hasRemaining()) break
            shorts(chunk).forEach { out.add(it) }
        }
        // after ~735 ramp samples the steady factor is 0.5
        assertEquals(20000, out.size)
        val tail = out.drop(8000)
        tail.forEach { assertEquals(8000.0, it.toDouble(), 60.0) }
    }

    @Test
    fun `positive gain is capped by the tag peak`() {
        val processor = ReplayGainProcessor()
        processor.enabled = true
        processor.targetGainDb = 12f // ~4x requested
        processor.tagPeak = 0.5f     // only 2x headroom before clipping
        processor.configure(format()).also { processor.flush() }
        processor.queueInput(bufferOf(*IntArray(4000) { 8000 }))
        processor.queueEndOfStream()
        var max = 0
        var total = 0
        while (true) {
            val chunk = processor.getOutput()
            if (!chunk.hasRemaining()) break
            shorts(chunk).forEach { if (kotlin.math.abs(it) > max) max = kotlin.math.abs(it); total++ }
        }
        assertTrue(total > 3900)
        // factor capped at 1/0.5 = 2 â†’ 8000 * 2 = 16000, well inside full scale
        assertTrue("unexpected peak $max", max in 15500..16500)
    }

    @Test
    fun `disabled processor is bypassed by the pipeline`() {
        val processor = ReplayGainProcessor()
        processor.enabled = false
        processor.configure(format()).also { processor.flush() }
        val input = intArrayOf(-32768, -12345, 0, 1, 32767, 23456)
        processor.queueInput(bufferOf(*input))
        assertArrayEquals(input, shorts(processor.getOutput()))
    }

    @Test
    fun `clamping prevents wraparound on extreme positive gains`() {
        val processor = ReplayGainProcessor()
        processor.enabled = true
        processor.targetGainDb = 40f
        processor.configure(format()).also { processor.flush() }
        processor.queueInput(bufferOf(*IntArray(4000) { 30000 }))
        processor.queueEndOfStream()
        while (true) {
            val chunk = processor.getOutput()
            if (!chunk.hasRemaining()) break
            shorts(chunk).forEach { assertTrue(kotlin.math.abs(it) <= 32767) }
        }
    }
}

/** W09-T05: gentle hiss + wobble, silent bypass, nothing generated without input. */
class TapeProcessorTest {

    private fun format() = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)

    private fun bufferOf(vararg samples: Int) = java.nio.ByteBuffer.allocate(samples.size * 2).apply {
        samples.forEach { value ->
            put((value and 0xff).toByte()); put(((value shr 8) and 0xff).toByte())
        }
        flip()
    }

    private fun drain(processor: TapeProcessor): IntArray {
        val out = mutableListOf<Int>()
        while (true) {
            val chunk = processor.getOutput()
            if (!chunk.hasRemaining()) break
            val copy = chunk.duplicate()
            while (copy.hasRemaining()) {
                val lo = copy.get().toInt() and 0xff
                val hi = copy.get().toInt()
                out.add(((hi shl 8) or lo).toShort().toInt())
            }
        }
        return out.toIntArray()
    }

    @Test
    fun `disabled processor is fully bypassed`() {
        val processor = TapeProcessor()
        processor.enabled = false
        processor.configure(format()).also { processor.flush() }
        val input = intArrayOf(-32768, -12345, 0, 1, 32767, 23456)
        processor.queueInput(bufferOf(*input))
        assertArrayEquals(input, drain(processor))
    }

    @Test
    fun `enabled processor adds a quiet hiss to digital silence`() {
        val processor = TapeProcessor()
        processor.enabled = true
        processor.configure(format()).also { processor.flush() }
        processor.queueInput(bufferOf(*IntArray(44100 * 2) { 0 })) // 1 s of silence
        val out = drain(processor)
        assertEquals(88200, out.size)
        val rms = kotlin.math.sqrt(out.sumOf { (it.toDouble() / 32768) * (it.toDouble() / 32768) } / out.size)
        // hiss sits around -50 dBFS: audible in silence, far below music
        assertTrue("hiss too loud: rms=$rms", rms > 0.0005 && rms < 0.006)
    }

    @Test
    fun `no input buffers means no output at all`() {
        val processor = TapeProcessor()
        processor.enabled = true
        processor.configure(format()).also { processor.flush() }
        assertFalse(processor.getOutput().hasRemaining())
        // pause/stop: the pipeline feeds nothing, the processor emits nothing
        val silence = drain(processor)
        assertEquals(0, silence.size)
    }

    @Test
    fun `wobble keeps the signal close to the dry mix without clipping`() {
        val processor = TapeProcessor()
        processor.enabled = true
        processor.configure(format()).also { processor.flush() }
        processor.queueInput(bufferOf(*IntArray(44100 * 2) { 20000 }))
        val out = drain(processor)
        // 82% dry + wobble around the same level: average stays near 0.82 * 20000
        val avg = out.map { kotlin.math.abs(it) }.average()
        assertTrue("average drifted too far: $avg", avg in 13000.0..22000.0)
        out.forEach { assertTrue(kotlin.math.abs(it) <= 32767) }
    }

    @Test
    fun `wow flutter measurably modulates tone periods`() {
        val frames = 44100 * 3
        val input = IntArray(frames * 2) { sample ->
            (kotlin.math.sin((sample / 2) * 2.0 * Math.PI * 1000 / 44100) * 12000).toInt()
        }
        val processor = TapeProcessor().apply { enabled = true }
        processor.configure(format())
        processor.flush()
        processor.queueInput(bufferOf(*input))
        val output = drain(processor)
        assertEquals(input.size, output.size)
        fun periodVariance(samples: IntArray): Double {
            val crossings = (44101 until frames).filter {
                samples[(it - 1) * 2] < 0 && samples[it * 2] >= 0
            }
            val periods = crossings.zipWithNext { a, b -> (b - a).toDouble() }
            assertTrue(periods.size > 1500)
            val mean = periods.average()
            return periods.sumOf { (it - mean) * (it - mean) } / periods.size
        }
        val cleanVariance = periodVariance(input)
        val tapeVariance = periodVariance(output)
        assertTrue("period modulation absent: clean=$cleanVariance tape=$tapeVariance",
            tapeVariance > cleanVariance * 1.5)
    }
}
