package io.cloudcauldron.bocan.playback.browse

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.playback.MediaId

/**
 * Builds every car browse item and its content-style extras in one place (phase 15).
 *
 * A folder's style hints describe how the car renders that folder's children: [folder]
 * with `grid = true` shows browsable children (albums, shows) as a grid of covers, and
 * playable children are always a list. A group title on an item makes the car draw a
 * section header above each contiguous run of items with the same title (search results).
 * The hints are additive extras: a browser that ignores them sees the same tree.
 */
@UnstableApi
class BrowseStyle(private val artworkUri: (String?) -> Uri?) {
    /** A browsable folder. [grid] styles its browsable children as a grid; [group] heads a search section. */
    fun folder(mediaId: String, title: String, artworkHash: String?, grid: Boolean, group: String? = null): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                if (grid) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM else MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            group?.let { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .setExtras(extras)
            .apply { artworkUri(artworkHash)?.let(::setArtworkUri) }
            .build()
        return MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build()
    }

    /** A playable track or episode; the session resolves the real item from [mediaId] when it is played. */
    @Suppress("LongParameterList") // the fields a browse row needs to render, not a smell
    fun playable(
        mediaId: String,
        title: String,
        subtitle: String?,
        artworkHash: String?,
        durationMs: Long,
        isPodcast: Boolean,
        group: String? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setDurationMs(durationMs)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(if (isPodcast) MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE else MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { group?.let { setExtras(Bundle().apply { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }) } }
            .apply { artworkUri(artworkHash)?.let(::setArtworkUri) }
            .build()
        return MediaItem.Builder().setMediaId(mediaId).setMediaMetadata(metadata).build()
    }

    /** A synced track as a playable item. */
    fun track(track: TrackEntity, group: String? = null): MediaItem = playable(
        mediaId = MediaId.of(track).raw,
        title = track.title,
        subtitle = track.artistName,
        artworkHash = track.artworkHash,
        durationMs = track.durationMs,
        isPodcast = false,
        group = group
    )

    /** A synced episode as a playable item. */
    fun episode(episode: EpisodeEntity, group: String? = null): MediaItem = playable(
        mediaId = MediaId.of(episode).raw,
        title = episode.title,
        subtitle = null,
        artworkHash = null,
        durationMs = episode.durationMs,
        isPodcast = true,
        group = group
    )
}
