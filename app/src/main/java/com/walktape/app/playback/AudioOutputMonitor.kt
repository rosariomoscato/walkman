package com.walktape.app.playback

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local volatile diagnostic: last gain actually forwarded to the sink, never audio samples. */
class AudioOutputMonitor {
    private val gain = MutableStateFlow<Float?>(null)
    val outputGain = gain.asStateFlow()
    fun reset() { gain.value = null }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun renderers(context: Context) = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean): AudioSink? {
            val delegate = super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams) ?: return null
            return object : ForwardingAudioSink(delegate) {
                override fun setVolume(volume: Float) {
                    super.setVolume(volume)
                    gain.value = volume
                }
            }
        }
    }
}
