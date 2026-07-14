package io.cloudcauldron.bocan.playback.queue

import io.cloudcauldron.bocan.playback.session.AudioPipelineFormat
import kotlinx.coroutines.flow.StateFlow

/**
 * The app-facing transport and queue surface, so view models depend on an interface
 * and tests pass a fake instead of a real MediaController. [QueueController] is the one
 * production implementation. Every call is main-safe.
 */
@Suppress("TooManyFunctions")
interface PlaybackTransport {
    val state: StateFlow<PlayerUiState>

    suspend fun connect()

    fun release()

    suspend fun playNow(trackIds: List<Long>, startIndex: Int)

    /** Play [trackIds] in a fresh shuffle, set already shuffled so no per-item reorder is needed. */
    suspend fun shuffleNow(trackIds: List<Long>)

    suspend fun playEpisodes(episodeIds: List<String>, startIndex: Int)

    suspend fun playNext(trackIds: List<Long>)

    suspend fun addToQueue(trackIds: List<Long>)

    suspend fun removeAt(index: Int)

    suspend fun move(from: Int, to: Int)

    suspend fun clear()

    suspend fun skipNext()

    suspend fun skipPrevious()

    suspend fun seekTo(positionMs: Long)

    suspend fun togglePlayPause()

    suspend fun pause()

    suspend fun setRepeat(mode: RepeatMode)

    suspend fun setSpeed(rate: Float)

    suspend fun setShuffle(strategy: ShuffleStrategy?)

    suspend fun currentVolume(): Float

    suspend fun setVolume(volume: Float)

    /**
     * The live decoder format of the current item (sample rate, channels, codec, bit
     * depth), for the song details sheet. Null when not connected, when the session does
     * not answer, or when nothing usable is playing; callers render no pipeline line and
     * never surface an error.
     */
    suspend fun currentAudioFormat(): AudioPipelineFormat?
}
