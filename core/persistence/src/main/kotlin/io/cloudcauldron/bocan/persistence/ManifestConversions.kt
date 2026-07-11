package io.cloudcauldron.bocan.persistence

import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.ArtistEntity
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.PlaylistEntity
import io.cloudcauldron.bocan.persistence.entities.PodcastEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.persistence.model.PlaylistKind
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestEpisode
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestPlaylist
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestPodcast
import io.cloudcauldron.bocan.persistence.model.manifest.ManifestTrack
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Pure manifest-to-entity conversions used by SyncApplier. Deterministic by
 * construction: every derivation sorts before grouping so repeated applies of
 * the same manifest produce byte-identical rows.
 *
 * The contract makes track metadata beyond the id/file core optional (an
 * untagged file on the Mac omits the keys), so absence is normalized here:
 * a missing title falls back to the relPath filename, and tracks without an
 * artist or album id group into the reserved UNKNOWN_ID bucket (manifest ids
 * are positive per the contract, so 0 can never collide).
 */

/** The reserved id for tracks whose artist or album is unknown. */
internal const val UNKNOWN_ID = 0L

/** Group by albumId in deterministic track order: min year, first non-null artwork. */
internal fun deriveAlbums(manifestTracks: List<ManifestTrack>): List<AlbumEntity> {
    val ordered = manifestTracks.sortedWith(
        compareBy(
            { it.albumId ?: UNKNOWN_ID },
            { it.discNumber ?: Int.MAX_VALUE },
            { it.trackNumber ?: Int.MAX_VALUE },
            { it.id }
        )
    )
    return ordered.groupBy { it.albumId ?: UNKNOWN_ID }.map { (albumId, tracks) ->
        AlbumEntity(
            id = albumId,
            name = tracks.first().album.orEmpty(),
            albumArtistName = tracks.first().albumArtist.orEmpty(),
            year = tracks.mapNotNull { it.year }.minOrNull(),
            artworkHash = tracks.firstNotNullOfOrNull { it.artworkHash },
            trackCount = tracks.size
        )
    }
}

internal fun deriveArtists(manifestTracks: List<ManifestTrack>): List<ArtistEntity> = manifestTracks
    .sortedBy { it.id }
    .groupBy { it.albumArtistId ?: UNKNOWN_ID }
    .map { (artistId, tracks) -> ArtistEntity(id = artistId, name = tracks.first().albumArtist.orEmpty()) }
    .sortedBy { it.id }

internal fun toTrackEntity(track: ManifestTrack, state: DownloadState, syncedAt: Instant): TrackEntity = TrackEntity(
    id = track.id,
    title = track.title ?: fallbackTitle(track.relPath),
    artistId = track.artistId ?: UNKNOWN_ID,
    artistName = track.artist.orEmpty(),
    albumArtistId = track.albumArtistId ?: UNKNOWN_ID,
    albumArtistName = track.albumArtist.orEmpty(),
    albumId = track.albumId ?: UNKNOWN_ID,
    albumName = track.album.orEmpty(),
    trackNumber = track.trackNumber,
    trackTotal = track.trackTotal,
    discNumber = track.discNumber,
    discTotal = track.discTotal,
    year = track.year,
    genre = track.genre,
    composer = track.composer,
    bpm = track.bpm?.roundToInt(),
    durationMs = track.durationMs,
    sampleRate = track.sampleRate,
    bitDepth = track.bitDepth,
    bitrate = track.bitrate,
    channelCount = track.channelCount,
    isLossless = track.isLossless,
    format = track.format,
    size = track.size,
    sha256 = track.sha256,
    relPath = track.relPath,
    artworkHash = track.artworkHash,
    lyricsHash = track.lyricsHash,
    rating = track.rating,
    loved = track.loved,
    rgTrackGain = track.replayGain?.trackGain,
    rgTrackPeak = track.replayGain?.trackPeak,
    rgAlbumGain = track.replayGain?.albumGain,
    rgAlbumPeak = track.replayGain?.albumPeak,
    clipSourceTrackId = track.clip?.sourceTrackId,
    clipStartMs = track.clip?.startMs,
    clipEndMs = track.clip?.endMs,
    downloadState = state,
    syncedAt = syncedAt
)

internal fun toEpisodeEntity(episode: ManifestEpisode, existing: EpisodeEntity?, syncedAt: Instant): EpisodeEntity = EpisodeEntity(
    id = episode.id,
    podcastId = episode.podcastId,
    guid = episode.guid,
    title = episode.title,
    publishedAt = episode.publishedAt?.let(Instant::parse) ?: Instant.EPOCH,
    durationMs = episode.durationMs ?: 0L,
    descriptionHtml = episode.descriptionHtml,
    relPath = episode.relPath,
    size = episode.size,
    sha256 = episode.sha256,
    hasChapters = episode.hasChapters,
    downloadState =
    if (existing != null && existing.sha256 == episode.sha256) existing.downloadState else DownloadState.Pending,
    syncedAt = syncedAt,
    seedPositionMs = episode.playPositionMs,
    seedPlayState = PlayState.fromWireOrNull(episode.playState) ?: PlayState.Unplayed
)

internal fun toPlaylistEntity(playlist: ManifestPlaylist): PlaylistEntity = PlaylistEntity(
    id = playlist.id,
    name = playlist.name,
    kind = PlaylistKind.fromWireOrNull(playlist.kind) ?: PlaylistKind.Manual,
    parentId = playlist.parentId,
    sortOrder = playlist.sortOrder,
    accentColor = playlist.accentColor,
    artworkHash = playlist.artworkHash
)

internal fun toPodcastEntity(podcast: ManifestPodcast): PodcastEntity = PodcastEntity(
    id = podcast.id,
    title = podcast.title,
    author = podcast.author,
    descriptionHtml = podcast.descriptionHtml,
    artworkHash = podcast.artworkHash,
    defaultSpeed = podcast.playbackSpeed
)

/** An untitled track displays as its filename, extension stripped. */
private fun fallbackTitle(relPath: String): String = relPath.substringAfterLast('/').substringBeforeLast('.').ifEmpty { relPath }
