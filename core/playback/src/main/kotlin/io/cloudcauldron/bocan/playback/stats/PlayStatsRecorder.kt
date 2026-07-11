package io.cloudcauldron.bocan.playback.stats

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.daos.PlayStatsDao
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.podcast.isPodcastMedia
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Records play and skip stats from the player, applying [PlayStatsRules]. It polls
 * the current position once a second while playing to learn the furthest point the
 * listener reached, and flushes that against the rules when an item transitions away
 * or playback ends. A per-item `counted` guard means pause and resume within one
 * item never double-count.
 *
 * A counted play emits a [ScrobbleEvent] on [scrobbleEvents] for phase 09. Episodes
 * are flagged so scrobbling skips them. This class is thin glue over the pure rules
 * and the DAO; the rules carry the coverage.
 */
@UnstableApi
class PlayStatsRecorder(
    private val statsDao: PlayStatsDao,
    private val dispatchers: CoroutineDispatchers,
    private val log: AppLog,
    private val now: () -> Instant = Instant::now
) {
    private val scrobbleFlow = MutableSharedFlow<ScrobbleEvent>(extraBufferCapacity = SCROBBLE_BUFFER)

    /** Scrobble-eligible plays, consumed by phase 09. */
    val scrobbleEvents: SharedFlow<ScrobbleEvent> = scrobbleFlow.asSharedFlow()

    // Mutated only from the player's (main) thread and the main-dispatched poller.
    private var currentMediaId: String? = null
    private var currentDurationMs: Long = 0
    private var reachedMs: Long = 0
    private var counted: Boolean = false

    /** Attach to a player and poll its position; call from the service's main scope. */
    fun attach(player: Player, scope: CoroutineScope) {
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    flushOutgoing(scope)
                    beginItem(mediaItem?.mediaId, player.duration)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) flushOutgoing(scope)
                }
            }
        )
        beginItem(player.currentMediaItem?.mediaId, player.duration)
        scope.launch { poll(player) }
    }

    private suspend fun poll(player: Player) {
        while (currentCoroutineContext().isActive) {
            if (player.isPlaying) {
                reachedMs = maxOf(reachedMs, player.currentPosition)
                if (player.duration > 0) currentDurationMs = player.duration
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun beginItem(mediaId: String?, durationMs: Long) {
        currentMediaId = mediaId
        currentDurationMs = if (durationMs > 0) durationMs else 0
        reachedMs = 0
        counted = false
    }

    /** Snapshot the outgoing item synchronously, then persist the outcome off-thread. */
    private fun flushOutgoing(scope: CoroutineScope) {
        val mediaId = currentMediaId ?: return
        if (counted) return
        counted = true
        val duration = currentDurationMs
        val reached = reachedMs
        val parsed = MediaId.parse(mediaId)
        when (val event = PlayStatsRules.classify(duration, reached)) {
            is PlayEvent.None -> Unit
            is PlayEvent.Play -> scope.launch { recordPlay(parsed, mediaId, reached) }
            is PlayEvent.Skip -> scope.launch { recordSkip(parsed, event.afterSeconds) }
        }
    }

    private suspend fun recordPlay(parsed: MediaId?, mediaId: String, reachedMs: Long) {
        val playedSec = reachedMs / MS_PER_SECOND
        val at = now()
        if (parsed is MediaId.Track) {
            withContext(dispatchers.io) { statsDao.recordPlay(parsed.trackId, playedSec, at) }
        }
        scrobbleFlow.tryEmit(
            ScrobbleEvent(
                mediaId = mediaId,
                trackId = (parsed as? MediaId.Track)?.trackId,
                playedAt = at,
                isPodcast = isPodcastMedia(mediaId)
            )
        )
        log.debug("playback.play.recorded", mapOf("mediaId" to mediaId, "sec" to playedSec))
    }

    private suspend fun recordSkip(parsed: MediaId?, afterSeconds: Int) {
        if (parsed is MediaId.Track) {
            withContext(dispatchers.io) { statsDao.recordSkip(parsed.trackId, afterSeconds) }
            log.debug("playback.skip.recorded", mapOf("trackId" to parsed.trackId, "sec" to afterSeconds))
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val MS_PER_SECOND = 1_000L
        const val SCROBBLE_BUFFER = 16
    }
}
