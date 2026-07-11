package io.cloudcauldron.bocan.playback.queue

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaItemFactory
import io.cloudcauldron.bocan.playback.MediaItemSource
import io.cloudcauldron.bocan.playback.PlaybackService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The app-facing transport and queue API, and the single choke point that keeps
 * every [MediaController] call on the main thread (Media3's requirement). Every
 * public function is suspend and main-safe: it switches to the main dispatcher
 * internally, so callers (view models) never need withContext.
 *
 * Shuffle reorders the real queue via [ShuffleStrategy] (deterministic and testable)
 * rather than ExoPlayer's opaque built-in order. State is published as a
 * [PlayerUiState] StateFlow the UI collects.
 */
// The transport surface (playNow, playNext, addToQueue, removeAt, move, skip, seek,
// repeat, speed, shuffle, connect, release) is the phase contract; its breadth is
// intentional, not a decomposition smell.
@Suppress("TooManyFunctions")
@UnstableApi
class QueueController(
    private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    private val mediaItemSource: MediaItemSource,
    private val scope: CoroutineScope,
    private val shuffleInputs: suspend (List<String>) -> List<ShuffleItem> = { ids -> ids.map(::ShuffleItem) },
    private val random: () -> Random = { Random.Default }
) : PlaybackTransport {
    private val uiState = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = uiState.asStateFlow()

    private var controller: MediaController? = null
    private var shuffleActive: Boolean = false

    /**
     * Connect to the running service's session. Idempotent. If the session is not
     * reachable (nothing is playing yet, or the service was rejected), the controller
     * stays null and a later call retries: a failed connection must never crash the UI.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun connect() = withContext(dispatchers.main) {
        if (controller != null) return@withContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val connected = try {
            MediaController.Builder(context, token).buildAsync().await()
        } catch (expected: Exception) {
            // Expected when no session exists yet; leave the controller null to retry later.
            return@withContext
        }
        connected.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = pushState()
            }
        )
        controller = connected
        startPositionTicker()
        pushState()
    }

    /** Release the controller. */
    override fun release() {
        controller?.release()
        controller = null
    }

    override suspend fun playNow(trackIds: List<Long>, startIndex: Int) = onController { controller ->
        val items = mediaItemSource.resolveTracks(trackIds)
        if (items.isEmpty()) return@onController
        controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        controller.prepare()
        controller.play()
    }

    override suspend fun playEpisodes(episodeIds: List<String>, startIndex: Int) = onController { controller ->
        val items = mediaItemSource.resolveEpisodes(episodeIds)
        if (items.isEmpty()) return@onController
        controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        controller.prepare()
        controller.play()
    }

    override suspend fun playNext(trackIds: List<Long>) = onController { controller ->
        val items = mediaItemSource.resolveTracks(trackIds)
        val insertIndex = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItems(insertIndex, items)
    }

    override suspend fun addToQueue(trackIds: List<Long>) = onController { controller ->
        controller.addMediaItems(mediaItemSource.resolveTracks(trackIds))
    }

    override suspend fun removeAt(index: Int) = onController { controller ->
        if (index in 0 until controller.mediaItemCount) controller.removeMediaItem(index)
    }

    override suspend fun move(from: Int, to: Int) = onController { controller ->
        val count = controller.mediaItemCount
        if (from in 0 until count && to in 0 until count) controller.moveMediaItem(from, to)
    }

    override suspend fun clear() = onController { it.clearMediaItems() }

    override suspend fun skipNext() = onController { it.seekToNextMediaItem() }

    override suspend fun skipPrevious() = onController { it.seekToPreviousMediaItem() }

    override suspend fun seekTo(positionMs: Long) = onController { it.seekTo(positionMs) }

    override suspend fun togglePlayPause() = onController { controller ->
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    override suspend fun setRepeat(mode: RepeatMode) = onController { it.repeatMode = mode.toPlayer() }

    override suspend fun setSpeed(rate: Float) = onController { it.setPlaybackSpeed(rate) }

    /** Pause without toggling; used by the sleep timer. */
    override suspend fun pause() = onController { it.pause() }

    /** The current output volume (0..1), or full volume when not connected. */
    override suspend fun currentVolume(): Float = withContext(dispatchers.main) { controller?.volume ?: FULL_VOLUME }

    /** Set the output volume (0..1); used by the sleep timer fade. Does not push UI state. */
    override suspend fun setVolume(volume: Float) {
        withContext(dispatchers.main) { controller?.volume = volume.coerceIn(0f, FULL_VOLUME) }
    }

    /**
     * Turn shuffle on with [strategy] (reordering the upcoming items in place, the
     * current item kept where it is), or off with null (the queue keeps its shuffled
     * order; only the flag clears, matching the Mac).
     */
    override suspend fun setShuffle(strategy: ShuffleStrategy?) = onController { controller ->
        if (strategy == null) {
            shuffleActive = false
            pushState()
            return@onController
        }
        val count = controller.mediaItemCount
        val currentIndex = controller.currentMediaItemIndex
        if (count <= 1 || currentIndex < 0) {
            shuffleActive = true
            pushState()
            return@onController
        }
        val upcomingIds = ((currentIndex + 1) until count).map { controller.getMediaItemAt(it).mediaId }
        val order = strategy.order(shuffleInputs(upcomingIds), random())
        applyOrder(controller, currentIndex, order)
        shuffleActive = true
        pushState()
    }

    /** Reorder the items after [afterIndex] to match [order] (a permutation of their media ids). */
    private fun applyOrder(controller: MediaController, afterIndex: Int, order: List<String>) {
        var target = afterIndex + 1
        for (mediaId in order) {
            val from = (target until controller.mediaItemCount).firstOrNull {
                controller.getMediaItemAt(it).mediaId == mediaId
            } ?: continue
            if (from != target) controller.moveMediaItem(from, target)
            target++
        }
    }

    private suspend fun onController(block: suspend (MediaController) -> Unit) = withContext(dispatchers.main) {
        controller?.let { block(it) }
        pushState()
    }

    private fun startPositionTicker() {
        scope.launch {
            while (currentCoroutineContext().isActive) {
                delay(POSITION_TICK_MS)
                if (controller?.isPlaying == true) pushState()
            }
        }
    }

    private fun pushState() {
        val controller = controller ?: return
        val queue = (0 until controller.mediaItemCount).map { MediaItemFactory.toNowPlaying(controller.getMediaItemAt(it)) }
        val current: MediaItem? = controller.currentMediaItem
        uiState.value = PlayerUiState(
            current = current?.let(MediaItemFactory::toNowPlaying),
            positionMs = controller.currentPosition.coerceAtLeast(0),
            durationMs = controller.duration.coerceAtLeast(0),
            isPlaying = controller.isPlaying,
            queue = queue,
            queueIndex = controller.currentMediaItemIndex,
            repeatMode = RepeatMode.fromPlayer(controller.repeatMode),
            shuffleActive = shuffleActive,
            speed = controller.playbackParameters.speed
        )
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (failure: java.util.concurrent.ExecutionException) {
                cont.resumeWithException(failure.cause ?: failure)
            } catch (interrupted: InterruptedException) {
                cont.resumeWithException(interrupted)
            }
        }, MoreExecutors.directExecutor())
        cont.invokeOnCancellation { cancel(false) }
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val FULL_VOLUME = 1f
    }
}
