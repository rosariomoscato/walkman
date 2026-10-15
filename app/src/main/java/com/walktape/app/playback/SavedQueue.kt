package com.walktape.app.playback

/** Durable musical state only: no credentials, network/file URIs, passwords or tokens. */
data class SavedQueueItem(
    val entryId: String,
    val songId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val originName: String? = null,
)

data class SavedQueue(
    val profileId: Long,
    val items: List<SavedQueueItem>,
    val traversal: List<String>,
    val currentId: String?,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean,
    val updatedAt: Long,
    /** User-chosen label for a mixed queue; playlist-origin queues keep their own name. */
    val mixtapeName: String? = null,
) {
    fun isValid(): Boolean {
        if (profileId <= 0 || positionMs < 0 || repeatMode !in 0..2) return false
        if (items.any { !it.entryId.startsWith(QueueIdentity.PREFIX) || it.songId.isBlank() ||
                (it.durationMs != null && it.durationMs <= 0) }) return false
        val ids = items.map { it.entryId }.toHashSet()
        if (ids.size != items.size || traversal.size != items.size || traversal.toSet() != ids) return false
        return if (items.isEmpty()) currentId == null && positionMs == 0L && !shuffle else currentId in ids
    }
}
