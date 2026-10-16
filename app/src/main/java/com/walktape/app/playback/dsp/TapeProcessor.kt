package com.walktape.app.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * W09-T05: light tape flavour — a very quiet hiss floor and the slow speed wobble of a
 * cassette transport (wow ~0.7 Hz depth ~12 ms plus flutter ~9.5 Hz depth ~1.2 ms) via a
 * modulated delay line with linear interpolation. Intensity is deliberately gentle; the
 * disabled effect is a byte-exact copy that supports changing it during playback, which
 * also means silence in pause/stop: with no input buffers there is no output at all and
 * the hiss generator only runs inside queueInput. This is independent from the W06 key
 * clicks and from the visual reels.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TapeProcessor : BaseAudioProcessor() {

    @Volatile var enabled: Boolean = false

    // delay line (per channel, interleaved handling via stride)
    private var delay = FloatArray(0)
    private var delayFrames = 0
    private var writePos = 0
    private var sampleRate = 44100
    private var channels = 2
    // LFO phases in radians-per-sample
    private var wowPhase = 0.0
    private var flutterPhase = 0.0
    // xorshift hiss generator state
    private var noiseState = 0x12345678L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encodable = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!encodable) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // A new stream restarts the delay content: no wobble or noise carried across tracks.
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        allocateDelay()
        delay.fill(0f)
        wowPhase = 0.0
        flutterPhase = 0.0
    }

    override fun onReset() {
        delay = FloatArray(0)
        delayFrames = 0
        writePos = 0
        wowPhase = 0.0
        flutterPhase = 0.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (!enabled) {
            delay.fill(0f)
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }
        if (inputAudioFormat.sampleRate != sampleRate || inputAudioFormat.channelCount != channels) {
            sampleRate = inputAudioFormat.sampleRate
            channels = inputAudioFormat.channelCount
            allocateDelay()
        }
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        val useFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (useFloat) 4 else 2
        while (inputBuffer.hasRemaining()) {
            val frame = writePos * channels
            // one hiss value per frame, shared by all channels like a real tape floor
            val hiss = nextNoise() * HISS_AMPLITUDE
            for (ch in 0 until channels) {
                val dry: Float = if (useFloat) inputBuffer.float
                else {
                    val lo = inputBuffer.get().toInt() and 0xff
                    val hi = inputBuffer.get().toInt()
                    (((hi shl 8) or lo).toShort()).toFloat() / 32768f
                }
                // modulated read position: base delay + wow + flutter, per frame
                val wowDepth = WOW_DEPTH_MS * sampleRate / 1000.0
                val flutterDepth = FLUTTER_DEPTH_MS * sampleRate / 1000.0
                val modulation = wowDepth * sin(wowPhase) + flutterDepth * sin(flutterPhase)
                val baseFrames = BASE_DELAY_MS * sampleRate / 1000.0
                val readPos = (writePos - (baseFrames + modulation)) % delayFrames
                val norm = if (readPos < 0) readPos + delayFrames else readPos
                val i0 = norm.toInt() % delayFrames
                val i1 = (i0 + 1) % delayFrames
                val frac = (norm - i0).toFloat()
                val s0 = delay[i0 * channels + ch].toFloat()
                val s1 = delay[i1 * channels + ch].toFloat()
                val wet = s0 + (s1 - s0) * frac
                // dry signal stays dominant; the wobble is a light parallel path
                val mixed = dry * (1f - WET_MIX) + wet * WET_MIX + hiss
                if (useFloat) output.putFloat(mixed.coerceIn(-1f, 1f))
                else {
                    val scaled = (mixed.coerceIn(-1f, 1f) * 32767f).toInt()
                    output.put((scaled and 0xff).toByte())
                    output.put(((scaled shr 8) and 0xff).toByte())
                }
            }
            // store the dry frame for future reads
            val frameStart = inputBuffer.position() - channels * bytesPerSample
            val saved = inputBuffer.position()
            inputBuffer.position(frameStart)
            for (ch in 0 until channels) {
                val value: Float = if (useFloat) inputBuffer.float.coerceIn(-1f, 1f)
                else {
                    val lo = inputBuffer.get().toInt() and 0xff
                    val hi = inputBuffer.get().toInt()
                    ((hi shl 8) or lo).toShort().toFloat() / 32768f
                }
                delay[frame + ch] = value
            }
            inputBuffer.position(saved)
            writePos = (writePos + 1) % delayFrames
            wowPhase += 2 * PI * WOW_HZ / sampleRate
            flutterPhase += 2 * PI * FLUTTER_HZ / sampleRate
        }
        output.flip()
    }

    private fun allocateDelay() {
        val neededFrames = ((BASE_DELAY_MS + WOW_DEPTH_MS + FLUTTER_DEPTH_MS + 2) * sampleRate / 1000).toInt() + 1
        if (delayFrames != neededFrames || delay.size < neededFrames * channels) {
            delayFrames = neededFrames
            delay = FloatArray(delayFrames * channels)
        }
        writePos = 0
    }

    /** Uniform-ish white noise in [-1,1] from an xorshift state; cheap and allocation-free. */
    private fun nextNoise(): Float {
        noiseState = noiseState xor (noiseState shl 13)
        noiseState = noiseState xor (noiseState ushr 7)
        noiseState = noiseState xor (noiseState shl 17)
        val bits = (noiseState and 0xffffffffL).toInt()
        return bits.toFloat() / 2147483648f
    }

    companion object {
        const val BASE_DELAY_MS = 25.0
        const val WOW_HZ = 0.7
        const val WOW_DEPTH_MS = 12.0
        const val FLUTTER_HZ = 9.5
        const val FLUTTER_DEPTH_MS = 1.2
        const val WET_MIX = 0.18f
        /** about -50 dBFS: audible on quiet passages, far below typical music levels */
        const val HISS_AMPLITUDE = 0.0032f

        /** Upper bound of the hiss contribution, used by tests as the presence threshold. */
        const val HISS_RMS_BOUND = HISS_AMPLITUDE * 0.61f
    }
}
