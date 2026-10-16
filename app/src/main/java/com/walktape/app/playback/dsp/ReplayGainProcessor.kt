package com.walktape.app.playback.dsp

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.pow

/**
 * W09-T03: applies the ReplayGain chosen for the current stream as a sample-accurate
 * gain with a short ramp (no clicks on track changes) and explicit clipping control:
 * a known tag peak caps the applied factor (1/peak), and everything is hard-clamped to
 * full scale anyway. Disabled processing copies bytes exactly, preserving runtime
 * activation without reconfiguring the decoder or changing Android STREAM_MUSIC.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ReplayGainProcessor : BaseAudioProcessor() {

    /** Target gain in dB for the current track; 0 dB keeps the signal untouched. */
    @Volatile var targetGainDb: Float = 0f
    /** Absolute peak from tags (0..1]; caps the factor so amplification cannot clip. */
    @Volatile var tagPeak: Float? = null
    /** Master switch (ReplayGain mode); while false the processor stays bypassed. */
    @Volatile var enabled: Boolean = false

    private var currentFactor = 1f
    private var targetFactor = 1f
    private var rampRemaining = 0
    private var rampStep = 0f
    private var pendingConfigure = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encodable = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!encodable) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // A new stream starts from the exact target: no stale ramp across tracks.
        currentFactor = if (enabled) factorFor(targetGainDb) else 1f
        targetFactor = currentFactor
        rampRemaining = 0
        pendingConfigure = false
    }

    override fun onReset() {
        currentFactor = 1f
        targetFactor = 1f
        rampRemaining = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (!enabled) {
            currentFactor = 1f
            targetFactor = 1f
            rampRemaining = 0
            replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
            return
        }
        val factor = factorFor(targetGainDb)
        if (factor != targetFactor || pendingConfigure) {
            targetFactor = factor
            rampRemaining = RAMP_SAMPLES
            rampStep = (targetFactor - currentFactor) / RAMP_SAMPLES
            pendingConfigure = false
        }
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        val useFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        while (inputBuffer.hasRemaining()) {
            if (rampRemaining > 0) {
                currentFactor += rampStep
                rampRemaining--
                if (rampRemaining == 0) currentFactor = targetFactor
            }
            if (useFloat) {
                val value = inputBuffer.float * currentFactor
                output.putFloat(value.coerceIn(-1f, 1f))
            } else {
                // little-endian 16-bit PCM
                val lo = inputBuffer.get().toInt() and 0xff
                val hi = inputBuffer.get().toInt()
                val sample = ((hi shl 8) or lo).toShort()
                val scaled = (sample * currentFactor).coerceIn(-32768f, 32767f)
                val rounded = scaled.toInt()
                output.put((rounded and 0xff).toByte())
                output.put(((rounded shr 8) and 0xff).toByte())
            }
        }
        output.flip()
    }

    private fun factorFor(gainDb: Float): Float {
        val raw = 10f.pow((gainDb.takeIf { it.isFinite() } ?: 0f).coerceIn(-60f, 24f) / 20f)
        val peak = tagPeak
        val cap = if (peak != null && peak > 0f) 1f / peak else Float.MAX_VALUE
        return raw.coerceAtMost(cap)
    }

    companion object {
        private const val RAMP_SAMPLES = 735 // ~16 ms at 44.1 kHz
    }
}
