package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.queue.PlaybackTransport
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import io.cloudcauldron.bocan.playback.queue.ShuffleStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The one app-session player facade the mini player and every play action go through.
 * It connects the [QueueController] to the service once, exposes its [state], and
 * offers the play verbs the library screens call. Lives for the whole app session
 * (created at the navigation root), so it is not recreated per screen.
 */
class PlayerViewModel(private val queueController: PlaybackTransport, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<PlayerUiState> = queueController.state

    /**
     * Connect to the session so the mini player reflects any live playback. Call when the
     * UI first needs the session (from the shell). Idempotent, and a failure is tolerated:
     * play actions below reconnect as needed.
     */
    fun connect() {
        scope.launch { queueController.connect() }
    }

    /** Play [trackIds] from [startIndex] (the visible context: an album, a playlist, a filtered list). */
    fun play(trackIds: List<Long>, startIndex: Int) {
        if (trackIds.isEmpty()) return
        scope.launch {
            queueController.connect()
            queueController.playNow(trackIds, startIndex)
        }
    }

    /** Play [episodeIds] from [startIndex] (a podcast episode context). */
    fun playEpisodes(episodeIds: List<String>, startIndex: Int) {
        if (episodeIds.isEmpty()) return
        scope.launch {
            queueController.connect()
            queueController.playEpisodes(episodeIds, startIndex)
        }
    }

    /** Play [trackIds] in a fresh shuffle. */
    fun shuffle(trackIds: List<Long>) {
        if (trackIds.isEmpty()) return
        scope.launch {
            queueController.connect()
            queueController.playNow(trackIds, 0)
            queueController.setShuffle(ShuffleStrategy.FisherYates)
        }
    }

    fun playNext(trackIds: List<Long>) = launchOnController { queueController.playNext(trackIds) }

    fun addToQueue(trackIds: List<Long>) = launchOnController { queueController.addToQueue(trackIds) }

    fun togglePlayPause() = launchOnController { queueController.togglePlayPause() }

    private fun launchOnController(block: suspend () -> Unit) {
        scope.launch {
            queueController.connect()
            block()
        }
    }

    fun dispose() {
        queueController.release()
        scope.cancel()
    }
}
