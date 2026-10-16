package com.walktape.app.playback.browse

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.walktape.app.core.db.AlbumEntity
import com.walktape.app.core.db.ArtistEntity
import com.walktape.app.core.db.DownloadEntity
import com.walktape.app.core.db.PlaylistEntity
import com.walktape.app.core.db.SongEntity

/**
 * Pure entity → MediaItem mapping for the Android Auto browse tree (W10-T01). Items carry
 * browse ids, platform media types, localized titles/subtitles and cover-art content URIs;
 * they never contain stream URIs or credentials. Playback resolution happens exclusively in
 * [com.walktape.app.playback.PlaybackService] onAddMediaItems.
 */
object AutoBrowseItems {

    fun rootItem(appName: String): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseIds.ROOT)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(appName)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build())
        .build()

    /** Honest placeholder shown when no profile is configured: no fake library content. */
    fun setupItem(mediaId: String, title: String, body: String): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(body)
            .setIsBrowsable(false)
            .setIsPlayable(false)
            .build())
        .build()

    fun category(mediaId: String, title: String, folderType: Int): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(folderType)
            .build())
        .build()

    fun album(profileId: Long, entity: AlbumEntity): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseIds.album(profileId, entity.id))
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(entity.name)
            .setArtist(entity.artist?.takeIf { it.isNotBlank() })
            .setSubtitle(subtitle(entity.artist, entity.year))
            .setIsBrowsable(true)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
            .setArtworkUri(entity.coverArt?.let { artwork(profileId, it) })
            .build())
        .build()

    fun artist(profileId: Long, entity: ArtistEntity): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseIds.artist(profileId, entity.id))
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(entity.name)
            .setIsBrowsable(true)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
            .setArtworkUri(entity.coverArt?.let { artwork(profileId, it) })
            .build())
        .build()

    fun song(profileId: Long, entity: SongEntity): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseIds.song(profileId, entity.id))
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(entity.title)
            .setArtist((entity.displayArtist ?: entity.artist)?.takeIf { it.isNotBlank() })
            .setAlbumTitle(entity.album?.takeIf { it.isNotBlank() })
            .setDurationMs(entity.duration * 1000L)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(entity.coverArt?.let { artwork(profileId, it) })
            .build())
        .build()

    fun playlist(profileId: Long, entity: PlaylistEntity): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseIds.playlist(profileId, entity.id))
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(entity.name)
            .setArtist(entity.owner)
            .setIsBrowsable(true)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
            .setArtworkUri(entity.coverArt?.let { artwork(profileId, it) })
            .build())
        .build()

    fun downloadGroup(profileId: Long, origin: String, name: String, coverArt: String?,
        status: String? = null, playable: Boolean = true): MediaItem =
        MediaItem.Builder()
            .setMediaId(AutoBrowseIds.downloadGroup(profileId, origin))
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(name)
                .setSubtitle(status)
                .setIsBrowsable(true)
                .setIsPlayable(playable)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .setArtworkUri(coverArt?.let { artwork(profileId, it) })
                .build())
            .build()

    /** A downloaded song playable offline; metadata survives even if the song left the server. */
    fun downloadedSong(profileId: Long, entity: DownloadEntity): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseIds.song(profileId, entity.songId))
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(entity.title)
            .setArtist(entity.artist?.takeIf { it.isNotBlank() })
            .setAlbumTitle(entity.album?.takeIf { it.isNotBlank() })
            .setDurationMs(entity.duration * 1000L)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(entity.coverArt?.let { artwork(profileId, it) })
            .build())
        .build()

    fun subtitle(artist: String?, year: Int?): String? =
        listOfNotNull(artist?.takeIf { it.isNotBlank() }, year?.takeIf { it > 0 }?.toString())
            .joinToString(" • ").takeIf { it.isNotEmpty() }

    private fun artwork(profileId: Long, coverArtId: String): Uri =
        Uri.parse(CoverArtUris.uriString(profileId, coverArtId))
}
