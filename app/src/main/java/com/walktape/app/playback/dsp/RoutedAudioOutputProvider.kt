package com.walktape.app.playback.dsp

import android.media.AudioDeviceInfo
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.AudioTrackAudioOutput

/** Observes real track routing without choosing devices, changing samples or volume. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RoutedAudioOutputProvider(
    private val delegate: AudioOutputProvider,
    private val effects: SystemEffectsController
) : AudioOutputProvider by delegate {
    private val main = Handler(Looper.getMainLooper())

    override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
        val output = delegate.getAudioOutput(config)
        val track = (output as? AudioTrackAudioOutput)?.audioTrack
        val token = Any()
        val released = java.util.concurrent.atomic.AtomicBoolean(false)
        val playingRequested = java.util.concurrent.atomic.AtomicBoolean(false)
        fun report() {
            if (released.get()) return
            val playing = playingRequested.get() && track?.playState == AudioTrack.PLAYSTATE_PLAYING
            effects.outputRouteChanged(token, route(track?.routedDevice?.type), playing)
        }
        // Initial routing may still be null at play(), and the platform does not
        // guarantee a later routing callback for that initial assignment. Re-read
        // only while playing; also covers missed OEM device-change notifications.
        val poll = object : Runnable {
            override fun run() {
                if (released.get() || !playingRequested.get()) return
                report()
                main.postDelayed(this, 500L)
            }
        }
        val routing = AudioRouting.OnRoutingChangedListener { report() }
        track?.addOnRoutingChangedListener(routing, main)
        return object : AudioOutput by output {
            override fun play() {
                output.play()
                playingRequested.set(true)
                main.post {
                    main.removeCallbacks(poll)
                    poll.run()
                }
            }

            override fun pause() {
                playingRequested.set(false)
                output.pause()
                main.post {
                    main.removeCallbacks(poll)
                    if (!released.get()) effects.outputRouteChanged(token, BassBoostRouting.Route.UNKNOWN, false)
                }
            }

            override fun stop() {
                playingRequested.set(false)
                output.stop()
                main.post {
                    main.removeCallbacks(poll)
                    if (!released.get()) effects.outputRouteChanged(token, BassBoostRouting.Route.UNKNOWN, false)
                }
            }

            override fun release() {
                released.set(true)
                playingRequested.set(false)
                track?.removeOnRoutingChangedListener(routing)
                main.post {
                    main.removeCallbacks(poll)
                    effects.outputReleased(token)
                }
                output.release()
            }
        }
    }

    private fun route(type: Int?): BassBoostRouting.Route = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> BassBoostRouting.Route.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID -> BassBoostRouting.Route.HEADPHONES
        else -> BassBoostRouting.Route.UNKNOWN
    }
}
