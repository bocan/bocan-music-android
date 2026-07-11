package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.persistence.daos.LibraryDao
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.SleepDuration
import io.cloudcauldron.bocan.playback.SleepTimer
import io.cloudcauldron.bocan.playback.SleepTimerState
import io.cloudcauldron.bocan.playback.queue.PlaybackTransport
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import io.cloudcauldron.bocan.playback.queue.ShuffleStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The current track's Mac-owned display metadata (loved and rating are display-only here). */
data class TrackDisplay(val loved: Boolean = false, val rating: Int = 0, val albumId: Long? = null, val artistId: Long? = null)

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
    val sleepTimer: SleepTimerState = SleepTimerState.Idle
)

/**
 * Drives the full-screen Now Playing. Folds the transport state, the current track's
 * loved and rating (looked up from the DB, display-only), and the sleep timer state.
 * Transport actions delegate to the [PlaybackTransport]; loved and rating expose no
 * edit path.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModel(
    private val transport: PlaybackTransport,
    private val libraryDao: LibraryDao,
    private val sleepTimer: SleepTimer,
    dispatchers: CoroutineDispatchers
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val currentDisplay =
        transport.state.map { it.current?.mediaId }
            .distinctUntilChanged()
            .flatMapLatest { mediaId -> displayFor(mediaId) }

    val state: StateFlow<NowPlayingUiState> =
        combine(transport.state, currentDisplay, sleepTimer.state) { player, display, timer ->
            val current = player.current
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
                sleepTimer = timer
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), NowPlayingUiState())

    fun togglePlayPause() = launch { transport.togglePlayPause() }

    fun next() = launch { transport.skipNext() }

    fun previous() = launch { transport.skipPrevious() }

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

    private fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
