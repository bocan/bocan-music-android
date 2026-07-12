package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.app.data.PodcastPreferencesSource
import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.SleepDuration
import io.cloudcauldron.bocan.playback.SleepTimer
import io.cloudcauldron.bocan.playback.SleepTimerState
import io.cloudcauldron.bocan.playback.podcast.Chapter
import io.cloudcauldron.bocan.playback.podcast.ChaptersParser
import io.cloudcauldron.bocan.playback.podcast.ChaptersRepository
import io.cloudcauldron.bocan.playback.queue.NowPlayingItem
import io.cloudcauldron.bocan.playback.queue.PlaybackTransport
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import io.cloudcauldron.bocan.playback.queue.ShuffleStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The current track's Mac-owned display metadata (loved and rating are display-only here). */
data class TrackDisplay(val loved: Boolean = false, val rating: Int = 0, val albumId: Long? = null, val artistId: Long? = null)

/**
 * The display fields for a queue neighbor, so the gesture peek can render the real card of
 * the previous or next item before the user commits. Null in the UI state at an end of the
 * queue, which is the gesture machine's cue to rubber-band.
 */
data class NeighborDisplay(val title: String, val artist: String, val artworkUri: String?)

/** The podcast slice of Now Playing state: present only while an episode is current. */
data class PodcastNowPlaying(
    val isPodcast: Boolean = false,
    val podcastId: Long? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapterTitle: String? = null,
    val skipBackSeconds: Int = DEFAULT_SKIP_BACK,
    val skipForwardSeconds: Int = DEFAULT_SKIP_FORWARD
) {
    private companion object {
        const val DEFAULT_SKIP_BACK = 15
        const val DEFAULT_SKIP_FORWARD = 30
    }
}

/** Everything the Now Playing screen renders. */
data class NowPlayingUiState(
    val hasItem: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffleActive: Boolean = false,
    val speed: Float = 1.0f,
    val display: TrackDisplay = TrackDisplay(),
    val sleepTimer: SleepTimerState = SleepTimerState.Idle,
    val podcast: PodcastNowPlaying = PodcastNowPlaying(),
    val previous: NeighborDisplay? = null,
    val next: NeighborDisplay? = null
)

/**
 * Drives the full-screen Now Playing. Folds the transport state, the current track's
 * loved and rating (looked up from the DB, display-only), and the sleep timer state.
 * Transport actions delegate to the [PlaybackTransport]; loved and rating expose no
 * edit path.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModel(
    private val transport: PlaybackTransport,
    private val libraryDao: LibraryDao,
    private val podcastDao: PodcastDao,
    private val chaptersRepository: ChaptersRepository,
    private val preferences: PodcastPreferencesSource,
    private val sleepTimer: SleepTimer,
    dispatchers: CoroutineDispatchers,
    private val prefetchArtwork: (String?) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    init {
        // Warm both neighbors' artwork the moment a transition settles, keyed by their uris,
        // so the first frame of a drag renders the real image instead of a placeholder flash.
        // Read from the queue's actual (possibly shuffled) order, never library order.
        scope.launch {
            transport.state
                .map { neighborsOf(it) }
                .distinctUntilChanged()
                .collect { (previous, next) ->
                    prefetchArtwork(previous?.artworkUri)
                    prefetchArtwork(next?.artworkUri)
                }
        }
    }

    private val currentDisplay =
        transport.state.map { it.current?.mediaId }
            .distinctUntilChanged()
            .flatMapLatest { mediaId -> displayFor(mediaId) }

    /** Resolves the podcast context (chapters, show id) once per episode; empty for music. */
    private val podcastContext: Flow<PodcastContext> =
        transport.state.map { it.current?.mediaId }
            .distinctUntilChanged()
            .flatMapLatest { mediaId -> flow { emit(podcastContextFor(mediaId)) } }

    private val skipIntervals: Flow<Pair<Int, Int>> =
        combine(preferences.skipBackSeconds, preferences.skipForwardSeconds) { back, forward -> back to forward }

    val state: StateFlow<NowPlayingUiState> =
        combine(transport.state, currentDisplay, sleepTimer.state, podcastContext, skipIntervals) {
                player,
                display,
                timer,
                context,
                intervals
            ->
            val current = player.current
            val (previous, next) = neighborsOf(player)
            NowPlayingUiState(
                hasItem = current != null,
                title = current?.title.orEmpty(),
                artist = current?.artist.orEmpty(),
                album = current?.album.orEmpty(),
                artworkUri = current?.artworkUri,
                positionMs = player.positionMs,
                durationMs = player.durationMs,
                isPlaying = player.isPlaying,
                repeatMode = player.repeatMode,
                shuffleActive = player.shuffleActive,
                speed = player.speed,
                display = display,
                sleepTimer = timer,
                podcast = context.toUi(player.positionMs, intervals.first, intervals.second),
                previous = previous,
                next = next
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), NowPlayingUiState())

    fun togglePlayPause() = launch { transport.togglePlayPause() }

    fun next() = launch { transport.skipNext() }

    fun previous() = launch { transport.skipPrevious() }

    /** Skip back by the configured interval, clamped at the start of the episode. */
    fun skipBack() = launch {
        val player = transport.state.value
        val delta = state.value.podcast.skipBackSeconds * MILLIS_PER_SECOND
        transport.seekTo((player.positionMs - delta).coerceAtLeast(0))
    }

    /** Skip forward by the configured interval, clamped at the episode duration. */
    fun skipForward() = launch {
        val player = transport.state.value
        val delta = state.value.podcast.skipForwardSeconds * MILLIS_PER_SECOND
        transport.seekTo((player.positionMs + delta).coerceAtMost(player.durationMs))
    }

    /** Cycle the podcast speed presets and persist the choice as a per-show override. */
    fun cycleSpeed() = launch {
        val current = state.value.speed
        val next = SPEED_PRESETS.firstOrNull { it > current + SPEED_EPSILON } ?: SPEED_PRESETS.first()
        transport.setSpeed(next)
        state.value.podcast.podcastId?.let { preferences.setShowSpeed(it, next.toDouble()) }
    }

    /** Seek to a chapter's start; used by the chapters sheet. */
    fun seekToChapter(chapter: Chapter) = launch { transport.seekTo(chapter.startTimeMs) }

    fun seekTo(positionMs: Long) = launch { transport.seekTo(positionMs) }

    fun cycleRepeat() = launch {
        val next = when (transport.state.value.repeatMode) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        transport.setRepeat(next)
    }

    fun setShuffle(strategy: ShuffleStrategy?) = launch { transport.setShuffle(strategy) }

    fun toggleShuffle() = launch {
        transport.setShuffle(if (transport.state.value.shuffleActive) null else ShuffleStrategy.FisherYates)
    }

    fun setSpeed(rate: Float) = launch { transport.setSpeed(rate) }

    fun startSleepTimer(duration: SleepDuration) = sleepTimer.start(duration)

    fun extendSleepTimer(minutes: Int) = sleepTimer.extend(minutes)

    fun cancelSleepTimer() = sleepTimer.cancel()

    fun dispose() = scope.cancel()

    private fun displayFor(mediaId: String?) = flowOf(mediaId).map { id ->
        val trackId = (id?.let(MediaId::parse) as? MediaId.Track)?.trackId ?: return@map TrackDisplay()
        val track = libraryDao.tracksByIds(listOf(trackId)).firstOrNull() ?: return@map TrackDisplay()
        TrackDisplay(track.loved, track.rating, track.albumId, track.albumArtistId)
    }

    private suspend fun podcastContextFor(mediaId: String?): PodcastContext {
        val episodeId = (mediaId?.let(MediaId::parse) as? MediaId.Episode)?.episodeId ?: return PodcastContext()
        val episode = podcastDao.episode(episodeId)
        return if (episode == null) {
            PodcastContext(isPodcast = true)
        } else {
            PodcastContext(
                isPodcast = true,
                podcastId = episode.podcastId,
                chapters = chaptersRepository.chaptersFor(episodeId, episode.hasChapters)
            )
        }
    }

    private fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    /** The previous and next queue entries as neighbor display, null at the ends. */
    private fun neighborsOf(player: PlayerUiState): Pair<NeighborDisplay?, NeighborDisplay?> {
        val index = player.queueIndex
        if (index < 0) return null to null
        return player.queue.getOrNull(index - 1)?.toNeighbor() to player.queue.getOrNull(index + 1)?.toNeighbor()
    }

    private fun NowPlayingItem.toNeighbor() = NeighborDisplay(title = title, artist = artist.orEmpty(), artworkUri = artworkUri)

    /** The per-episode facts resolved off the transport; folded with live position into UI. */
    private data class PodcastContext(
        val isPodcast: Boolean = false,
        val podcastId: Long? = null,
        val chapters: List<Chapter> = emptyList()
    ) {
        fun toUi(positionMs: Long, skipBack: Int, skipForward: Int): PodcastNowPlaying {
            if (!isPodcast) return PodcastNowPlaying()
            val activeIndex = ChaptersParser.activeChapterIndex(chapters, positionMs)
            return PodcastNowPlaying(
                isPodcast = true,
                podcastId = podcastId,
                chapters = chapters,
                chapterTitle = chapters.getOrNull(activeIndex)?.title,
                skipBackSeconds = skipBack,
                skipForwardSeconds = skipForward
            )
        }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val SPEED_EPSILON = 0.01f
        val SPEED_PRESETS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f)
    }
}
