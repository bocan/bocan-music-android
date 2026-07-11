package io.cloudcauldron.bocan.playback.podcast

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.persistence.daos.EpisodeStateDao
import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Applies [EpisodePlaybackRules] to the player: resumes an episode to its saved position
 * on load, writes the position back every five seconds and on pause and transition, and
 * marks an episode played on completion. Episodes are the single owner of their resume;
 * the music resume path never touches them. Thin glue over the pure rules and the DAO.
 */
@UnstableApi
class EpisodeProgressRecorder(
    private val episodeStateDao: EpisodeStateDao,
    private val podcastDao: PodcastDao,
    private val dispatchers: CoroutineDispatchers,
    private val log: AppLog,
    private val now: () -> Instant = Instant::now
) {
    private var currentEpisodeId: String? = null

    fun attach(player: Player, scope: CoroutineScope) {
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    flushOutgoing(player, scope)
                    beginItem(player, mediaItem?.mediaId, scope)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) completeCurrent(player, scope)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) flushOutgoing(player, scope)
                }
            }
        )
        beginItem(player, player.currentMediaItem?.mediaId, scope)
        scope.launch { poll(player) }
    }

    private fun beginItem(player: Player, mediaId: String?, scope: CoroutineScope) {
        val episodeId = episodeIdOf(mediaId)
        currentEpisodeId = episodeId
        if (episodeId != null) scope.launch { resume(player, episodeId) }
    }

    private suspend fun resume(player: Player, episodeId: String) {
        val state = withContext(dispatchers.io) { episodeStateDao.state(episodeId) }
        val durationMs = durationFor(player, episodeId)
        val target = EpisodePlaybackRules.resumePosition(state, durationMs)
        if (target > 0) player.seekTo(target)
        log.debug("episode.resume", mapOf("episodeId" to episodeId, "positionMs" to target))
    }

    private fun flushOutgoing(player: Player, scope: CoroutineScope) {
        val episodeId = currentEpisodeId ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        scope.launch { withContext(dispatchers.io) { episodeStateDao.updatePosition(episodeId, position, now()) } }
    }

    private fun completeCurrent(player: Player, scope: CoroutineScope) {
        val episodeId = currentEpisodeId ?: return
        val durationMs = player.duration
        scope.launch {
            if (EpisodePlaybackRules.isCompleted(player.currentPosition, durationMs)) {
                withContext(dispatchers.io) { episodeStateDao.markPlayed(episodeId, now()) }
                log.debug("episode.completed", mapOf("episodeId" to episodeId))
            }
        }
    }

    /** Restart a played (or any) episode from the list: reset to in-progress at zero. */
    suspend fun restart(episodeId: String) {
        withContext(dispatchers.io) {
            episodeStateDao.upsert(
                EpisodeStateEntity(episodeId, playPositionMs = 0, playState = PlayState.InProgress, lastPlayedAt = now())
            )
        }
    }

    private suspend fun poll(player: Player) {
        while (currentCoroutineContext().isActive) {
            delay(WRITE_INTERVAL_MS)
            val episodeId = currentEpisodeId
            if (episodeId != null && player.isPlaying) {
                val position = player.currentPosition.coerceAtLeast(0)
                withContext(dispatchers.io) { episodeStateDao.updatePosition(episodeId, position, now()) }
            }
        }
    }

    private suspend fun durationFor(player: Player, episodeId: String): Long {
        val playerDuration = player.duration
        if (playerDuration > 0) return playerDuration
        return withContext(dispatchers.io) { podcastDao.episode(episodeId)?.durationMs ?: 0 }
    }

    private fun episodeIdOf(mediaId: String?): String? = (mediaId?.let(MediaId::parse) as? MediaId.Episode)?.episodeId

    private companion object {
        const val WRITE_INTERVAL_MS = 5_000L
    }
}
