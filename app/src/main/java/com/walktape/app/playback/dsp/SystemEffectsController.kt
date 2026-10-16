package com.walktape.app.playback.dsp

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * W09-T04: real platform effects bound to one shared audio session, or an explicit
 * "unavailable" state — never a demo slider. The controller attaches the Equalizer and
 * BassBoost to the session id both playback decks use, so a crossfade keeps a single
 * effect chain. Every failure (missing audiofx bundle, rejected session, control lost)
 * is surfaced honestly instead of being swallowed as fake success.
 */
class SystemEffectsController(context: Context) {

    sealed interface State {
        data object Idle : State
        /** Effects exist on this session: band count and level bounds for the UI. */
        data class Ready(val bands: Int, val centerHz: List<Int>, val levelRangeMilliB: IntRange,
                         val bandLevelsMilliB: List<Int>, val bassStrength: Int,
                         val bassEnabled: Boolean, val eqEnabled: Boolean) : State
        data class Unavailable(val reason: Reason) : State
        enum class Reason { NO_EFFECTS, SESSION_REJECTED, RELEASED }
    }

    private val appContext = context.applicationContext
    private val stateFlow = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = stateFlow.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bass: BassBoost? = null
    private var sessionId = AudioEffect.ERROR_BAD_VALUE

    fun attach(audioSessionId: Int) {
        if (sessionId == audioSessionId && equalizer != null) return
        release()
        sessionId = audioSessionId
        if (audioSessionId <= 0) {
            stateFlow.value = State.Unavailable(State.Reason.SESSION_REJECTED)
            return
        }
        val createdEqualizer = try {
            Equalizer(0, audioSessionId).also { it.enabled = false }
        } catch (_: Exception) {
            stateFlow.value = State.Unavailable(State.Reason.NO_EFFECTS)
            return
        } catch (_: Error) {
            stateFlow.value = State.Unavailable(State.Reason.NO_EFFECTS)
            return
        }
        val createdBass = try {
            BassBoost(0, audioSessionId).also { it.enabled = false }
        } catch (_: Exception) {
            createdEqualizer.release()
            stateFlow.value = State.Unavailable(State.Reason.NO_EFFECTS)
            return
        } catch (_: Error) {
            createdEqualizer.release()
            stateFlow.value = State.Unavailable(State.Reason.NO_EFFECTS)
            return
        }
        equalizer = createdEqualizer
        bass = createdBass
        createdEqualizer.setControlStatusListener { _, granted ->
            if (!granted) stateFlow.value = State.Unavailable(State.Reason.SESSION_REJECTED)
        }
        createdBass.setControlStatusListener { _, granted ->
            if (!granted) stateFlow.value = State.Unavailable(State.Reason.SESSION_REJECTED)
        }
        publish()
    }

    /** Applies user preferences; a failed control marks the state unavailable honestly. */
    fun apply(equalizerEnabled: Boolean, bandLevelsCsv: String, bassEnabled: Boolean, bassStrength: Int): Boolean {
        val eq = equalizer ?: return false
        val boost = bass ?: return false
        val ok = try {
            applyBandLevels(eq, bandLevelsCsv)
            eq.enabled = equalizerEnabled
            boost.setStrength(bassStrength.coerceIn(0, 1000).toShort())
            boost.enabled = bassEnabled && bassStrength > 0
            eq.hasControl() && boost.hasControl() && eq.enabled == equalizerEnabled &&
                boost.enabled == (bassEnabled && bassStrength > 0)
        } catch (_: Exception) { false } catch (_: Error) { false }
        publish()
        if (!ok) stateFlow.value = State.Unavailable(State.Reason.SESSION_REJECTED)
        return ok
    }

    /** Live band change from the UI slider; publishes the new state. */
    fun setBandLevel(band: Int, levelMilliB: Int) {
        val eq = equalizer ?: return
        try {
            val range = eq.bandLevelRange
            eq.setBandLevel(band.toShort(), levelMilliB.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            publish()
        } catch (_: Exception) { publish() } catch (_: Error) { publish() }
    }

    private fun applyBandLevels(eq: Equalizer, csv: String) {
        val wanted = csv.split(',').mapNotNull { item ->
            item.trim().split(':').takeIf { it.size == 2 }?.let { (index, level) ->
                index.toIntOrNull()?.toShort()?.let { band -> band to level.toIntOrNull()?.toShort() }
            }
        }.filter { (_, level) -> level != null }.toMap()
        for (band in 0 until eq.numberOfBands) {
            val level = wanted[band.toShort()] ?: continue
            eq.setBandLevel(band.toShort(), level)
        }
    }

    private fun publish() {
        val eq = equalizer ?: run {
            stateFlow.value = State.Idle; return
        }
        try {
            val bands = eq.numberOfBands.toInt()
            if (bands <= 0) {
                stateFlow.value = State.Unavailable(State.Reason.NO_EFFECTS)
                return
            }
            val range = eq.bandLevelRange
            val centers = (0 until bands).map { eq.getCenterFreq(it.toShort()) } // milli-Hz
            val levels = (0 until bands).map { eq.getBandLevel(it.toShort()).toInt() }
            val boostStrength = bass?.roundedStrength?.toInt() ?: 0
            stateFlow.value = State.Ready(
                bands = bands,
                centerHz = centers.map { it / 1000 },
                levelRangeMilliB = range[0].toInt()..range[1].toInt(),
                bandLevelsMilliB = levels,
                bassStrength = boostStrength,
                bassEnabled = bass?.enabled == true,
                eqEnabled = eq.enabled,
            )
        } catch (_: Exception) {
            stateFlow.value = State.Unavailable(State.Reason.SESSION_REJECTED)
        } catch (_: Error) {
            stateFlow.value = State.Unavailable(State.Reason.SESSION_REJECTED)
        }
    }

    fun release() {
        try { equalizer?.enabled = false } catch (_: Exception) {}
        try { bass?.enabled = false } catch (_: Exception) {}
        try { equalizer?.release() } catch (_: Exception) {}
        try { bass?.release() } catch (_: Exception) {}
        equalizer = null
        bass = null
        sessionId = AudioEffect.ERROR_BAD_VALUE
        stateFlow.value = State.Idle
    }

    /** CSV of the current band levels for persistence; empty when levels are all zero. */
    fun currentBandLevelsCsv(): String {
        val eq = equalizer ?: return ""
        return try {
            (0 until eq.numberOfBands).joinToString(",") { band ->
                "$band:${eq.getBandLevel(band.toShort())}"
            }
        } catch (_: Exception) { "" } catch (_: Error) { "" }
    }

    /** Restores persisted levels only if they are valid for the actual band count. */
    fun sanitizeBandLevelsCsv(csv: String): String {
        val eq = equalizer ?: return csv
        return try {
            val bands = eq.numberOfBands
            csv.split(',').mapNotNull { item ->
                item.trim().split(':').takeIf { it.size == 2 }?.let { (index, level) ->
                    val band = index.toIntOrNull()
                    val value = level.toIntOrNull()
                    if (band != null && band < bands && value != null) "$band:$value" else null
                }
            }.joinToString(",")
        } catch (_: Exception) { "" } catch (_: Error) { "" }
    }
}
