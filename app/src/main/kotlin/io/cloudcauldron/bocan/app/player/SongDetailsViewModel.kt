package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.persistence.daos.EpisodeStateDao
import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.daos.PlayStatsDao
import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.queue.PlaybackTransport
import io.cloudcauldron.bocan.playback.session.AudioPipelineFormat
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The read-only song details, folded from the DB row, play stats, and the live pipeline. */
sealed interface SongDetailsUiState {
    /** Still resolving the current item. */
    data object Loading : SongDetailsUiState

    /** Nothing is playing, or the item resolved to no row. */
    data object Empty : SongDetailsUiState

    /** A music track. Every nullable field is omitted from the sheet when null. */
    data class Track(
        val title: String,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val year: Int?,
        val genre: String?,
        val trackNumber: Int?,
        val trackTotal: Int?,
        val discNumber: Int?,
        val discTotal: Int?,
        val format: String?,
        val lossless: Boolean,
        val durationMs: Long,
        val sizeBytes: Long,
        val bitrateKbps: Long?,
        val playCount: Long?,
        val lastPlayedAt: Instant?,
        val loved: Boolean,
        val rating: Int,
        val pipeline: AudioPipelineFormat? = null
    ) : SongDetailsUiState

    /** A podcast episode. */
    data class Episode(
        val title: String,
        val show: String?,
        val publishedAt: Instant,
        val durationMs: Long,
        val sizeBytes: Long,
        val format: String?,
        val playPositionMs: Long
    ) : SongDetailsUiState
}

/**
 * Resolves the current item (from the transport's media id) to a read-only details sheet
 * state: a track folds its DB row, phone-owned play stats, and, lazily when the sheet asks,
 * the live decoder pipeline; an episode folds its row, show, and listening position. It
 * follows the playing item, so a track change under an open sheet updates the content
 * rather than dismissing it. Nothing here edits: the Mac owns metadata.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SongDetailsViewModel(
    private val transport: PlaybackTransport,
    private val libraryDao: LibraryDao,
    private val podcastDao: PodcastDao,
    private val episodeStateDao: EpisodeStateDao,
    private val playStatsDao: PlayStatsDao,
    dispatchers: CoroutineDispatchers
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val pipeline = MutableStateFlow<AudioPipelineFormat?>(null)

    private val details: Flow<SongDetailsUiState> =
        transport.state.map { it.current?.mediaId }
            .distinctUntilChanged()
            .flatMapLatest { mediaId ->
                pipeline.value = null
                flow { emit(resolve(mediaId)) }
            }

    val state: StateFlow<SongDetailsUiState> =
        combine(details, pipeline) { base, pipe ->
            if (base is SongDetailsUiState.Track) base.copy(pipeline = pipe) else base
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), SongDetailsUiState.Loading)

    /** Fetch the live audio pipeline line; the sheet calls this once its content is shown. */
    fun refreshPipeline() {
        scope.launch { pipeline.value = transport.currentAudioFormat() }
    }

    fun dispose() = scope.cancel()

    private suspend fun resolve(mediaId: String?): SongDetailsUiState = when (val id = mediaId?.let(MediaId::parse)) {
        is MediaId.Track -> resolveTrack(id.trackId)
        is MediaId.Episode -> resolveEpisode(id.episodeId)
        null -> SongDetailsUiState.Empty
    }

    private suspend fun resolveTrack(trackId: Long): SongDetailsUiState {
        val track = libraryDao.tracksByIds(listOf(trackId)).firstOrNull() ?: return SongDetailsUiState.Empty
        val stats = playStatsDao.stats(trackId)
        return SongDetailsUiState.Track(
            title = track.title,
            artist = track.artistName.nullIfBlank(),
            album = track.albumName.nullIfBlank(),
            albumArtist = track.albumArtistName.nullIfBlank(),
            year = track.year,
            genre = track.genre?.nullIfBlank(),
            trackNumber = track.trackNumber,
            trackTotal = track.trackTotal,
            discNumber = track.discNumber,
            discTotal = track.discTotal,
            format = track.format.nullIfBlank(),
            lossless = track.isLossless,
            durationMs = track.durationMs,
            sizeBytes = track.size,
            bitrateKbps = averageBitrateKbps(track.size, track.durationMs),
            playCount = stats?.playCount?.takeIf { it > 0 },
            lastPlayedAt = stats?.lastPlayedAt,
            loved = track.loved,
            rating = track.rating
        )
    }

    private suspend fun resolveEpisode(episodeId: String): SongDetailsUiState {
        val episode = podcastDao.episode(episodeId) ?: return SongDetailsUiState.Empty
        val show = podcastDao.observeShow(episode.podcastId).first()?.title
        val position = episodeStateDao.state(episodeId)?.playPositionMs ?: episode.seedPositionMs
        return SongDetailsUiState.Episode(
            title = episode.title,
            show = show?.nullIfBlank(),
            publishedAt = episode.publishedAt,
            durationMs = episode.durationMs,
            sizeBytes = episode.size,
            format = formatFromPath(episode.relPath),
            playPositionMs = position
        )
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        const val BITS_PER_BYTE = 8L

        /** Average bitrate in kbps: (bytes x 8) / durationMs. Absent when either is unknown. */
        fun averageBitrateKbps(sizeBytes: Long, durationMs: Long): Long? =
            if (sizeBytes > 0 && durationMs > 0) sizeBytes * BITS_PER_BYTE / durationMs else null

        /** The file extension of a synced path, uppercased, as the episode's format label. */
        fun formatFromPath(relPath: String): String? = relPath.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.uppercase()

        fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }
    }
}
