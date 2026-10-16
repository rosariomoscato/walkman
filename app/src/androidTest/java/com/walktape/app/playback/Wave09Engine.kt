package com.walktape.app.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.FileDataSource
import com.walktape.app.playback.dsp.ReplayGainProcessor
import com.walktape.app.playback.dsp.TapeProcessor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Test-only audio engine (W09-T06): renders real Media3 decoding into an in-memory
 * sink instead of the device output, so gapless transitions, crossfade ramps and DSP
 * gains are measured on the actual PCM the pipeline produced. The throttling mimics
 * an AudioTrack consuming at real time; `write` dispositions carry the media time of
 * each buffer, which is what gap measurement uses.
 */
@UnstableApi
class CapturingAudioOutput : AudioOutputProvider, AudioOutput {

    class Chunk(val dispositionUs: Long, val frames: Long, val pcm: ShortArray,
        val projectedPlayoutNanos: Long)

    private val lock = Any()
    private val chunks = ArrayList<Chunk>()
    private var sampleRate = 8000
    private var channels = 1
    private var bytesPerFrame = 2
    private var writtenFrames = 0L
    private var flushedFrames = 0L
    private var baseUs = 0L
    private var playStartedNanos = -1L
    @Volatile private var outputVolume = 1f
    @Volatile var releasedAtNanos = Long.MAX_VALUE
        private set
    @Volatile var failed = false
        private set

    val allChunks: List<Chunk> get() = synchronized(lock) { ArrayList(chunks) }
    val totalFrames: Long get() = synchronized(lock) { writtenFrames }

    fun pcm(): ShortArray {
        val out = ShortArray((totalFrames * channels).toInt())
        var at = 0
        for (chunk in allChunks) {
            chunk.pcm.copyInto(out, at)
            at += chunk.pcm.size
        }
        return out.copyOf(at)
    }

    // --- AudioOutputProvider ---

    override fun getFormatSupport(config: AudioOutputProvider.FormatConfig): AudioOutputProvider.FormatSupport =
        if (config.format.sampleMimeType == "audio/raw")
            AudioOutputProvider.FormatSupport.Builder()
                .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY).build()
        else AudioOutputProvider.FormatSupport.UNSUPPORTED

    override fun getOutputConfig(config: AudioOutputProvider.FormatConfig): AudioOutputProvider.OutputConfig {
        sampleRate = config.format.sampleRate
        channels = config.format.channelCount
        bytesPerFrame = 2 * channels // the test sink runs 16-bit PCM
        return AudioOutputProvider.OutputConfig.Builder()
            .setEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(if (channels == 1) android.media.AudioFormat.CHANNEL_OUT_MONO
                else android.media.AudioFormat.CHANNEL_OUT_STEREO)
            .setBufferSize(bufferFrames() * bytesPerFrame)
            .build()
    }

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        releasedAtNanos = Long.MAX_VALUE
        flush()
        synchronized(lock) { playStartedNanos = -1L }
        return this
    }
    override fun addListener(listener: AudioOutputProvider.Listener) = Unit
    override fun removeListener(listener: AudioOutputProvider.Listener) = Unit
    override fun release() { releasedAtNanos = android.os.SystemClock.elapsedRealtimeNanos() }

    // --- AudioOutput ---

    override fun play() {
        synchronized(lock) { if (playStartedNanos < 0) playStartedNanos = System.nanoTime() }
    }

    override fun pause() {
        synchronized(lock) {
            if (playStartedNanos >= 0) {
                baseUs += (System.nanoTime() - playStartedNanos) / 1000
                playStartedNanos = -1
            }
        }
    }

    override fun write(buffer: ByteBuffer, encodedAccessUnitCount: Int, dispositionUs: Long): Boolean {
        try {
            while (true) {
                synchronized(lock) {
                    val aheadUs = (writtenFrames - flushedFrames) * 1_000_000L / sampleRate - getPositionUsLocked()
                    if (aheadUs < BUFFER_US) {
                        val frames = buffer.remaining() / bytesPerFrame
                        val copy = ByteArray(frames * bytesPerFrame)
                        val duplicate = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        val position = duplicate.position()
                        var at = 0
                        duplicate.position(position)
                        repeat(frames * channels) {
                            val value = duplicate.short
                            val scaled = (value * outputVolume).toInt().coerceIn(-32768, 32767).toShort()
                            copy[at++] = (scaled.toInt() and 0xff).toByte()
                            copy[at++] = ((scaled.toInt() shr 8) and 0xff).toByte()
                        }
                        chunks.add(Chunk(dispositionUs, frames.toLong(), toShorts(copy),
                            android.os.SystemClock.elapsedRealtimeNanos() + maxOf(0, aheadUs) * 1000))
                        writtenFrames += frames
                        buffer.position(buffer.limit())
                        return true
                    }
                }
                return false
            }
        } catch (_: InterruptedException) {
            return false
        } catch (_: Exception) {
            failed = true
            return false
        }
    }

    override fun flush() {
        synchronized(lock) {
            flushedFrames = writtenFrames
            baseUs = 0L
            if (playStartedNanos >= 0) playStartedNanos = System.nanoTime()
        }
    }
    override fun stop() = Unit
    override fun setVolume(volume: Float) { outputVolume = volume }
    override fun isOffloadedPlayback() = false
    override fun getAudioSessionId() = 0
    override fun getSampleRate() = sampleRate
    override fun getBufferSizeInFrames(): Long = bufferFrames().toLong()
    override fun getPositionUs(): Long = synchronized(lock) { getPositionUsLocked() }
    override fun getPlaybackParameters() = androidx.media3.common.PlaybackParameters(1f)
    override fun isStalled() = false
    override fun addListener(listener: AudioOutput.Listener) = Unit
    override fun removeListener(listener: AudioOutput.Listener) = Unit
    override fun setPlaybackParameters(playbackParameters: androidx.media3.common.PlaybackParameters) = Unit
    override fun setOffloadDelayPadding(paddingInFrames: Int, encoderDelayFrames: Int) = Unit
    override fun setOffloadEndOfStream() = Unit
    override fun attachAuxEffect(effectId: Int) = Unit
    override fun setAuxEffectSendLevel(sendLevel: Float) = Unit
    override fun setPreferredDevice(preferredDevice: android.media.AudioDeviceInfo?) = Unit

    private fun getPositionUsLocked(): Long {
        val elapsed = if (playStartedNanos >= 0) (System.nanoTime() - playStartedNanos) / 1000 else 0L
        val wall = baseUs + elapsed
        val written = (writtenFrames - flushedFrames) * 1_000_000L / sampleRate
        return minOf(wall, written)
    }

    private fun bufferFrames() = sampleRate / 2

    private fun toShorts(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    companion object {
        private const val BUFFER_US = 500_000L
    }
}

/** Builds test players whose sink is the given capture, with optional DSP processors. */
@UnstableApi
class TestAudioEngine(
    private val context: android.content.Context,
    private val capture: CapturingAudioOutput,
    private val gain: ReplayGainProcessor = ReplayGainProcessor(),
    private val tape: TapeProcessor = TapeProcessor(),
) {
    fun newPlayer(): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean,
                enableAudioPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioPlaybackParams)
                    .setAudioProcessors(arrayOf(gain, tape))
                    .setAudioOutputProvider(capture)
                    .build()
            }
        }
        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(FileDataSource.Factory()))
            .build()
    }

    companion object {
        /** Mono 8 kHz sine WAV of the given amplitude and length. */
        fun wav(samples: ShortArray, sampleRate: Int = 8000): ByteArray {
            val data = samples.size * 2
            val buffer = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray()).putInt(36 + data).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(sampleRate)
                .putInt(sampleRate * 2).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(data)
            samples.forEach { buffer.putShort(it) }
            return buffer.array()
        }

        fun sine(samples: Int, amplitude: Int, frequencyHz: Int, sampleRate: Int = 8000,
            phaseOffset: Double = 0.0): ShortArray = ShortArray(samples) { i ->
            (amplitude * kotlin.math.sin(phaseOffset + i * Math.PI * 2 * frequencyHz / sampleRate)).toInt().toShort()
        }

        fun writeWav(dir: java.io.File, name: String, content: ShortArray): java.io.File {
            dir.mkdirs()
            val file = java.io.File(dir, name)
            file.writeBytes(wav(content))
            return file
        }
    }
}
