package io.cloudcauldron.bocan.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.playback.audio.ReplayGainValues
import io.cloudcauldron.bocan.playback.queue.NowPlayingItem

/**
 * Builds Media3 [MediaItem]s from synced entities. Every item carries a stable
 * [MediaId] (`track:<id>` or `episode:<id>`), a local file [android.net.Uri] from
 * the [MediaFileResolver], full [MediaMetadata] for the lock screen and Bluetooth,
 * and, for tracks, a [ReplayGainValues] tag the audio processor reads on transitions.
 *
 * A CUE clip track (clipSourceTrackId set) points its Uri at the shared source
 * file and carries a [MediaItem.ClippingConfiguration] for its window. Its media id
 * is still its own clip id, never the source track id, so two clips of one file stay
 * distinct queue entries.
 */
class MediaItemFactory(private val resolver: MediaFileResolver) {
    fun forTrack(track: TrackEntity): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(MediaId.of(track).raw)
            .setUri(resolver.trackUri(track.relPath))
            .setTag(track.replayGainValues())
            .setMediaMetadata(track.metadata())

        if (track.clipStartMs != null && track.clipEndMs != null) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(track.clipStartMs!!)
                    .setEndPositionMs(track.clipEndMs!!)
                    .build()
            )
        }
        return builder.build()
    }

    fun forEpisode(episode: EpisodeEntity): MediaItem = MediaItem.Builder()
        .setMediaId(MediaId.of(episode).raw)
        .setUri(resolver.episodeUri(episode.relPath))
        .setTag(ReplayGainValues.NONE)
        .setMediaMetadata(episode.metadata())
        .build()

    private fun TrackEntity.replayGainValues() = ReplayGainValues(
        trackGainDb = rgTrackGain,
        trackPeak = rgTrackPeak,
        albumGainDb = rgAlbumGain,
        albumPeak = rgAlbumPeak
    )

    private fun TrackEntity.metadata(): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artistName)
            .setAlbumTitle(albumName)
            .setAlbumArtist(albumArtistName)
            .setDurationMs(durationMs)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        trackNumber?.let(builder::setTrackNumber)
        trackTotal?.let(builder::setTotalTrackCount)
        discNumber?.let(builder::setDiscNumber)
        discTotal?.let(builder::setTotalDiscCount)
        year?.let(builder::setRecordingYear)
        genre?.let(builder::setGenre)
        composer?.let(builder::setComposer)
        artworkHash?.let { resolver.artworkUri(it)?.let(builder::setArtworkUri) }
        return builder.build()
    }

    private fun EpisodeEntity.metadata(): MediaMetadata = MediaMetadata.Builder()
        .setTitle(title)
        .setDurationMs(durationMs)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
        .build()

    companion object {
        /** Read the display fields back off a built MediaItem for the UI state. */
        fun toNowPlaying(item: MediaItem): NowPlayingItem {
            val metadata = item.mediaMetadata
            return NowPlayingItem(
                mediaId = item.mediaId,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString(),
                album = metadata.albumTitle?.toString(),
                artworkUri = metadata.artworkUri?.toString(),
                durationMs = metadata.durationMs ?: 0L
            )
        }
    }
}
