package com.walktape.app.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local volatile diagnostic: last gain actually forwarded to the sink, never audio samples. */
class AudioOutputMonitor {
    private val gain = MutableStateFlow<Float?>(null)
    val outputGain = gain.asStateFlow()
    fun reset() { gain.value = null }

    /**
     * W09 renderers factory: the sink runs the configured build hook (audio processors
     * for ReplayGain/tape). Each logical-player deck can wrap its sink with this monitor.
     * It records the last actual sink write, not public player volume or STREAM_MUSIC.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun renderers(context: Context, sinkHook: (DefaultAudioSink.Builder) -> DefaultAudioSink.Builder = { it },
        monitor: Boolean = true) = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
            enableAudioPlaybackParams: Boolean): AudioSink? {
            val delegate = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioPlaybackParams)
                .let(sinkHook)
                .build()
            if (!monitor) return delegate
            return object : ForwardingAudioSink(delegate) {
                override fun setVolume(volume: Float) {
                    super.setVolume(volume)
                    gain.value = volume
                }
            }
        }
    }
}
