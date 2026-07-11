package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.playback.queue.PlaybackTransport
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import io.cloudcauldron.bocan.playback.queue.ShuffleStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A recording PlaybackTransport for view-model tests: no real player, just calls captured. */
class FakePlaybackTransport(initial: PlayerUiState = PlayerUiState()) : PlaybackTransport {
    private val stateFlow = MutableStateFlow(initial)
    override val state: StateFlow<PlayerUiState> = stateFlow

    val moves = mutableListOf<Pair<Int, Int>>()
    val removes = mutableListOf<Int>()
    var clears = 0
        private set
    val repeats = mutableListOf<RepeatMode>()
    val shuffles = mutableListOf<ShuffleStrategy?>()
    val speeds = mutableListOf<Float>()
    val seeks = mutableListOf<Long>()
    var togglePlayPauseCalls = 0
        private set
    var volume = 1f

    fun emit(state: PlayerUiState) {
        stateFlow.value = state
    }

    override suspend fun connect() = Unit
    override fun release() = Unit
    val playedEpisodes = mutableListOf<List<String>>()
    override suspend fun playNow(trackIds: List<Long>, startIndex: Int) = Unit
    override suspend fun playEpisodes(episodeIds: List<String>, startIndex: Int) {
        playedEpisodes += episodeIds
    }
    override suspend fun playNext(trackIds: List<Long>) = Unit
    override suspend fun addToQueue(trackIds: List<Long>) = Unit
    override suspend fun removeAt(index: Int) {
        removes += index
    }
    override suspend fun move(from: Int, to: Int) {
        moves += (from to to)
    }
    override suspend fun clear() {
        clears++
    }
    override suspend fun skipNext() = Unit
    override suspend fun skipPrevious() = Unit
    override suspend fun seekTo(positionMs: Long) {
        seeks += positionMs
    }
    override suspend fun togglePlayPause() {
        togglePlayPauseCalls++
    }
    override suspend fun pause() = Unit
    override suspend fun setRepeat(mode: RepeatMode) {
        repeats += mode
    }
    override suspend fun setSpeed(rate: Float) {
        speeds += rate
    }
    override suspend fun setShuffle(strategy: ShuffleStrategy?) {
        shuffles += strategy
    }
    override suspend fun currentVolume(): Float = volume
    override suspend fun setVolume(volume: Float) {
        this.volume = volume
    }
}
