package com.walktape.app.playback

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.audio.AudioFocusManager

/** One Media3 focus owner for the logical player, independent from internal deck lifetimes. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LogicalAudioFocus(context: Context, attributes: AudioAttributes,
    private val command: (Int) -> Unit, private val ducking: (Float) -> Unit) {
    private val thread = HandlerThread("WalkTapeFocus").apply { start() }
    private val background = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private var revision = 0L
    private val manager = AudioFocusManager(context.applicationContext, thread.looper,
        object : AudioFocusManager.PlayerControl {
            override fun setVolumeMultiplier(volumeMultiplier: Float) {
                main.post { if (!closed) ducking(volumeMultiplier) }
            }
            override fun executePlayerCommand(playerCommand: Int) {
                main.post { if (!closed) command(playerCommand) }
            }
        })

    init { background.post { manager.setAudioAttributes(attributes) } }

    fun update(play: Boolean, state: Int) {
        val version = ++revision
        background.post {
            if (closed) return@post
            val result = manager.updateAudioFocus(play, state)
            main.post {
                if (!closed && version == revision && play && state != androidx.media3.common.Player.STATE_IDLE)
                    command(result)
            }
        }
    }

    fun release() {
        if (closed) return
        closed = true
        background.post { manager.release(); thread.quitSafely() }
    }
}
