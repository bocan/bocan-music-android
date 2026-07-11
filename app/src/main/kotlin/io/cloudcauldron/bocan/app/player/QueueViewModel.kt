package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.queue.PlaybackTransport
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row in the queue sheet. */
data class QueueItemUi(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val isCurrent: Boolean
)

/** The queue sheet's state: the items, which is current, and the Up Next tally. */
data class QueueUiState(
    val items: List<QueueItemUi> = emptyList(),
    val currentIndex: Int = -1,
    val upNextCount: Int = 0,
    val upNextRemainingMs: Long = 0
)

/**
 * Drives the queue sheet. Reorder and remove are session-local queue edits (allowed;
 * only the library is read-only) and delegate straight to the [PlaybackTransport], so
 * what the user sees is what plays. Removing the current item lets Media3 advance.
 */
class QueueViewModel(private val transport: PlaybackTransport, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<QueueUiState> = transport.state
        .map { toQueueUi(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), QueueUiState())

    fun move(from: Int, to: Int) {
        scope.launch { transport.move(from, to) }
    }

    fun removeAt(index: Int) {
        scope.launch { transport.removeAt(index) }
    }

    fun clear() {
        scope.launch { transport.clear() }
    }

    fun dispose() = scope.cancel()

    private fun toQueueUi(player: PlayerUiState): QueueUiState {
        val current = player.queueIndex
        val items = player.queue.mapIndexed { index, item ->
            QueueItemUi(
                index = index,
                mediaId = item.mediaId,
                title = item.title,
                artist = item.artist,
                artworkUri = item.artworkUri,
                isCurrent = index == current
            )
        }
        val upNext = if (current in player.queue.indices) player.queue.drop(current + 1) else emptyList()
        return QueueUiState(
            items = items,
            currentIndex = current,
            upNextCount = upNext.size,
            upNextRemainingMs = upNext.sumOf { it.durationMs }
        )
    }

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
