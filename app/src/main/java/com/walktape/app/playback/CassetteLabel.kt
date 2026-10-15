package com.walktape.app.playback

/**
 * Cassette label derived from the real queue contents; never invents content.
 * An empty queue has no label (the cassette stays blank, honestly).
 * A user-named mixtape shows that name instead of the generic one.
 */
data class CassetteLabel(val kind: Kind, val text: String?) {
    enum class Kind { ALBUM, PLAYLIST, MIXTAPE }
}

fun cassetteLabel(entries: List<PlaybackQueueEntry>, originName: String?, mixtapeName: String? = null): CassetteLabel? {
    if (entries.isEmpty()) return null
    val origin = originName?.trim().takeIf { it?.isNotEmpty() == true }
    if (origin != null) return CassetteLabel(CassetteLabel.Kind.PLAYLIST, origin)
    val custom = mixtapeName?.trim()?.takeIf { it.isNotEmpty() }
    if (custom != null) return CassetteLabel(CassetteLabel.Kind.MIXTAPE, custom)
    val albums = entries.mapNotNull { it.album?.trim()?.takeIf(String::isNotEmpty) }.distinct()
    val albumIds = entries.map { it.albumId }.distinct()
    if (albums.size == 1 && entries.all { !it.album.isNullOrBlank() } &&
        albumIds.size == 1 && !albumIds.first().isNullOrBlank()) {
        return CassetteLabel(CassetteLabel.Kind.ALBUM, albums.first())
    }
    return CassetteLabel(CassetteLabel.Kind.MIXTAPE, mixtapeName?.trim()?.takeIf { it.isNotEmpty() })
}
