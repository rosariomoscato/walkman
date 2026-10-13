package com.walktape.app.playback

/** Immutable UI state, without media URLs, credentials or exception messages. */
data class PlaybackSnapshot(
    val connected: Boolean = false,
    val itemKey: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val repeatMode: Int = 0,
    val canPlayPause: Boolean = false,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val canSeek: Boolean = false,
    val canRepeat: Boolean = false,
    val volume: Int = 0,
    val maxVolume: Int = 1,
    val volumeFixed: Boolean = false,
    val queue: List<PlaybackQueueEntry> = emptyList(),
    val shuffleOn: Boolean = false,
    val canEditQueue: Boolean = false,
    val canShuffle: Boolean = false,
    val failure: PlaybackFailure? = null,
) {
    val hasMedia: Boolean get() = itemKey != null
}

/** One queue row as shown in the app: no URIs, no credentials. */
data class PlaybackQueueEntry(
    val entryId: String,
    val mediaId: String,
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val isCurrent: Boolean = false,
)

enum class PlaybackFailure { CONNECTION, PLAYBACK, REQUEST, VOLUME }
