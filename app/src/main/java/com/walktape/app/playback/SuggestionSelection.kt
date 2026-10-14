package com.walktape.app.playback

import com.walktape.app.core.net.SongDto

/** Pure selection rules for suggested continuations: no Android, network or player types. */
internal object SuggestionSelection {
    const val REQUEST_COUNT = 15

    /** Drops the source song itself and unusable entries, preserving the server's ranking. */
    fun candidates(songs: List<SongDto>, sourceSongId: String, limit: Int = REQUEST_COUNT): List<SongDto> =
        songs.filter { it.id != sourceSongId && it.duration > 0 }.take(limit)
}
