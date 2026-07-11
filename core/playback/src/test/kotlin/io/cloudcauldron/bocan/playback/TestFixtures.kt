package io.cloudcauldron.bocan.playback

import android.net.Uri
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.entities.EpisodeEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.persistence.model.PlayState
import java.time.Instant

/** A resolver that echoes the path into a file Uri (needs Robolectric for Uri.parse). */
class FakeMediaFileResolver(private val artworkPresent: Boolean = true) : MediaFileResolver {
    override fun trackUri(relPath: String): Uri = Uri.parse("file:///media/library/$relPath")
    override fun episodeUri(relPath: String): Uri = Uri.parse("file:///media/$relPath")
    override fun artworkUri(hash: String): Uri? = if (artworkPresent) Uri.parse("file:///media/artwork/$hash") else null
}

/** A no-op AppLog for tests that do not assert on logging. */
object NoopLog : AppLog {
    override fun debug(event: String, fields: Map<String, Any?>) = Unit
    override fun info(event: String, fields: Map<String, Any?>) = Unit
    override fun warning(event: String, fields: Map<String, Any?>) = Unit
    override fun error(event: String, fields: Map<String, Any?>) = Unit
}

/** A synced track with sensible defaults; override only what a test cares about. */
@Suppress("LongParameterList")
fun track(
    id: Long = 1,
    title: String = "Title $id",
    artistName: String = "Artist",
    albumArtistName: String = "Album Artist",
    albumName: String = "Album",
    trackNumber: Int? = 1,
    trackTotal: Int? = 10,
    discNumber: Int? = 1,
    discTotal: Int? = 1,
    year: Int? = 2001,
    genre: String? = "Folk",
    composer: String? = "Composer",
    durationMs: Long = 200_000,
    relPath: String = "Artist/Album/$id.flac",
    artworkHash: String? = "arthash",
    rgTrackGain: Double? = -6.0,
    rgTrackPeak: Double? = 0.9,
    rgAlbumGain: Double? = -4.0,
    rgAlbumPeak: Double? = 0.95,
    clipSourceTrackId: Long? = null,
    clipStartMs: Long? = null,
    clipEndMs: Long? = null
): TrackEntity = TrackEntity(
    id = id,
    title = title,
    artistId = 1,
    artistName = artistName,
    albumArtistId = 1,
    albumArtistName = albumArtistName,
    albumId = 1,
    albumName = albumName,
    trackNumber = trackNumber,
    trackTotal = trackTotal,
    discNumber = discNumber,
    discTotal = discTotal,
    year = year,
    genre = genre,
    composer = composer,
    bpm = null,
    durationMs = durationMs,
    sampleRate = 44_100,
    bitDepth = 16,
    bitrate = null,
    channelCount = 2,
    isLossless = true,
    format = "flac",
    size = 1_000,
    sha256 = "sha$id",
    relPath = relPath,
    artworkHash = artworkHash,
    lyricsHash = null,
    rating = 0,
    loved = false,
    rgTrackGain = rgTrackGain,
    rgTrackPeak = rgTrackPeak,
    rgAlbumGain = rgAlbumGain,
    rgAlbumPeak = rgAlbumPeak,
    clipSourceTrackId = clipSourceTrackId,
    clipStartMs = clipStartMs,
    clipEndMs = clipEndMs,
    downloadState = DownloadState.Downloaded,
    syncedAt = Instant.EPOCH
)

/** A synced episode with sensible defaults. */
fun episode(
    id: String = "ep1",
    title: String = "Episode",
    durationMs: Long = 1_800_000,
    relPath: String = "Podcasts/Show/ep1.mp3"
): EpisodeEntity = EpisodeEntity(
    id = id,
    podcastId = 1,
    guid = "guid-$id",
    title = title,
    publishedAt = Instant.EPOCH,
    durationMs = durationMs,
    descriptionHtml = null,
    relPath = relPath,
    size = 2_000,
    sha256 = "epsha$id",
    hasChapters = false,
    downloadState = DownloadState.Downloaded,
    syncedAt = Instant.EPOCH,
    seedPositionMs = 0,
    seedPlayState = PlayState.Unplayed
)
