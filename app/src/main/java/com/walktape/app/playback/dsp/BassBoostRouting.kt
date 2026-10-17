package com.walktape.app.playback.dsp

/** Session-wide policy: both audible decks must allow the shared bass effect. */
class BassBoostRouting {
    enum class Route { UNKNOWN, SPEAKER, HEADPHONES }
    private val outputs = mutableMapOf<Any, Route>()

    val route: Route
        get() = when {
            outputs.isEmpty() -> Route.UNKNOWN
            Route.SPEAKER in outputs.values -> Route.SPEAKER
            outputs.values.all { it == Route.HEADPHONES } -> Route.HEADPHONES
            else -> Route.UNKNOWN
        }

    fun update(output: Any, route: Route, playing: Boolean) {
        if (playing) outputs[output] = route else outputs.remove(output)
    }

    fun remove(output: Any) { outputs.remove(output) }
    fun clear() { outputs.clear() }

    fun shouldEnable(requested: Boolean, strength: Int): Boolean =
        requested && strength > 0 && route == Route.HEADPHONES
}
