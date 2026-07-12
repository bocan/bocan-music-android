package io.cloudcauldron.bocan.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.playback.audio.EffectsChain
import io.cloudcauldron.bocan.playback.audio.ReplayGainValues
import io.cloudcauldron.bocan.playback.browse.MediaTree
import io.cloudcauldron.bocan.playback.queue.QueuePersistence
import io.cloudcauldron.bocan.playback.queue.QueueSnapshot
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import io.cloudcauldron.bocan.playback.queue.fromPlayer
import io.cloudcauldron.bocan.playback.queue.toPlayer
import io.cloudcauldron.bocan.playback.session.AudioFormatResult
import io.cloudcauldron.bocan.playback.session.SessionCommands as BocanCommands
import io.cloudcauldron.bocan.playback.stats.ResumePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The single [MediaLibraryService] and session every UI surface (app, Auto, widget,
 * Bluetooth) talks to. It owns the one [ExoPlayer], wires the play-stats recorder,
 * restores the persisted queue paused on start, and writes the queue back on a five
 * second cadence plus every item transition.
 *
 * The notification comes from Media3's default provider; the [BrowseCallback] serves the
 * Android Auto tree, advertises the custom session commands, and resolves browse items to
 * playable ones. Task removal keeps playback alive when something is playing.
 */
// The service is the single Media3 surface: lifecycle, session, browse, and command
// handling all live here by design, not as a decomposition smell.
@Suppress("TooManyFunctions")
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private val log = AppLog.forCategory(LogCategory.Playback)
    private lateinit var scope: CoroutineScope
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var persistence: QueuePersistence
    private lateinit var effectsChain: EffectsChain
    private lateinit var mediaTree: MediaTree
    private lateinit var episodeSkipButtons: List<CommandButton>

    private val components: PlaybackComponents get() = (application as PlaybackHost).playbackComponents

    override fun onCreate() {
        super.onCreate()
        val graph = components
        scope = CoroutineScope(SupervisorJob() + graph.dispatchers.main)
        player = graph.playerFactory.create()
        persistence = graph.queuePersistence
        effectsChain = graph.effectsChain
        mediaTree = graph.mediaTree
        episodeSkipButtons = graph.episodeSkipButtons

        session = MediaLibrarySession.Builder(this, player, BrowseCallback())
            .build()

        graph.statsRecorder.attach(player, scope)
        graph.episodeRecorder.attach(player, scope)
        bindEffects()
        restoreQueuePaused()
        observeForPersistence()
    }

    /**
     * Bind the effects chain to the running player: skip silence is a player property,
     * the per-item ReplayGain factor comes from the current item's tag, and the fade
     * envelope tracks position. The chain applies the rest (EQ, bass, limiter) through
     * its processors, driven by the settings observer in the app graph.
     */
    private fun bindEffects() {
        effectsChain.bind(
            EffectsChain.Binding(
                skipSilence = { enabled -> player.skipSilenceEnabled = enabled },
                currentItemValues = { player.currentMediaItem?.localConfiguration?.tag as? ReplayGainValues },
                scope = scope
            )
        )
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val values = mediaItem?.localConfiguration?.tag as? ReplayGainValues ?: ReplayGainValues.NONE
                    effectsChain.onItemTransition(values)
                    updateNotificationButtons(mediaItem)
                }
            }
        )
        updateNotificationButtons(player.currentMediaItem)
        scope.launch {
            while (currentCoroutineContext().isActive) {
                delay(FADE_TICK_MS)
                if (player.isPlaying) effectsChain.crossfader.applyPositionFade(player.currentPosition, player.duration)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Keep playing when the user swipes the app away; only tear down if idle.
        if (!player.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.launch { persistence.save(snapshot()) }
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun restoreQueuePaused() {
        scope.launch {
            val snapshot = persistence.load() ?: return@launch
            if (snapshot.mediaIds.isEmpty()) return@launch
            val items = components.mediaItemSource.resolve(snapshot.mediaIds.mapNotNull(MediaId::parse))
            if (items.isEmpty()) return@launch
            val startIndex = snapshot.index.coerceIn(0, items.lastIndex)
            // Episodes own their own resume via EpisodeProgressRecorder; the music heuristic
            // must not also seek them, or the two resume paths fight.
            val currentIsEpisode = snapshot.mediaIds.getOrNull(startIndex)?.let(MediaId::parse) is MediaId.Episode
            val startPositionMs = if (!currentIsEpisode && ResumePolicy.shouldResume(snapshot.currentDurationMs)) {
                snapshot.positionMs
            } else {
                0L
            }
            player.setMediaItems(items, startIndex, startPositionMs)
            player.repeatMode = snapshot.repeatMode.toPlayer()
            player.shuffleModeEnabled = snapshot.shuffleActive
            player.prepare()
            // Restored paused, never auto-resumed.
            player.playWhenReady = false
            log.debug("playback.restore", mapOf("count" to items.size, "index" to startIndex))
        }
    }

    private fun observeForPersistence() {
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    scope.launch { persistence.save(snapshot()) }
                }
            }
        )
        scope.launch {
            while (currentCoroutineContext().isActive) {
                delay(PERSIST_INTERVAL_MS)
                if (player.mediaItemCount > 0) persistence.save(snapshot())
            }
        }
    }

    private fun snapshot(): QueueSnapshot {
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        return QueueSnapshot(
            mediaIds = ids,
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition.coerceAtLeast(0),
            repeatMode = RepeatMode.fromPlayer(player.repeatMode),
            shuffleActive = player.shuffleModeEnabled,
            currentDurationMs = player.duration.coerceAtLeast(0)
        )
    }

    /**
     * The Android Auto and browser callback: it serves the [MediaTree], advertises the
     * custom session commands so the notification, Auto, and widget can trigger skip,
     * speed, and shuffle, and resolves browse items to playable ones so a tap in Auto
     * plays the real local file (phase 10).
     */
    private inner class BrowseCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val available = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            BocanCommands.all().forEach { available.add(it) }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(available.build())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == BocanCommands.GET_AUDIO_FORMAT) {
                val format = AudioFormatResult.fromExoFormat(player.audioFormat)
                return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_SUCCESS, AudioFormatResult.toBundle(format))
                )
            }
            handleCommand(customCommand.customAction)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(mediaTree.rootItem(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(ImmutableList.copyOf(mediaTree.children(parentId, page, pageSize)), params)
        }

        // Browse items carry only a media id; resolve them to real playable items before they enter the queue.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> = future {
            val ids = mediaItems.mapNotNull { MediaId.parse(it.mediaId) }
            components.mediaItemSource.resolve(ids).ifEmpty { mediaItems }
        }
    }

    /**
     * Set the notification and Auto custom layout to the episode skip buttons when a
     * podcast episode is current, and clear it (leaving the built-in transport) for music.
     */
    private fun updateNotificationButtons(mediaItem: MediaItem?) {
        val isEpisode = mediaItem?.mediaId?.let(MediaId::parse) is MediaId.Episode
        session.setCustomLayout(if (isEpisode) episodeSkipButtons else emptyList())
    }

    /** Apply a custom session command to the player, on the session's application thread. */
    private fun handleCommand(action: String) {
        when (action) {
            BocanCommands.SKIP_BACK -> player.seekTo((player.currentPosition - SKIP_BACK_MS).coerceAtLeast(0))
            BocanCommands.SKIP_FORWARD -> player.seekTo(player.currentPosition + SKIP_FORWARD_MS)
            BocanCommands.TOGGLE_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
            BocanCommands.CYCLE_SPEED -> player.setPlaybackSpeed(nextSpeed(player.playbackParameters.speed))
            else -> log.warning("playback.command.unknown", mapOf("action" to action))
        }
    }

    private fun nextSpeed(current: Float): Float = SPEED_CYCLE.firstOrNull { it > current + SPEED_EPSILON } ?: SPEED_CYCLE.first()

    /** Bridge a suspend browse/resolve to a [ListenableFuture] on the service scope. */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (error: Exception) {
                log.error("playback.browse.failed", mapOf("error" to error.toString()))
                future.setException(error)
            }
        }
        return future
    }

    private companion object {
        const val PERSIST_INTERVAL_MS = 5_000L
        const val FADE_TICK_MS = 200L
        const val SKIP_BACK_MS = 15_000L
        const val SKIP_FORWARD_MS = 30_000L
        const val SPEED_EPSILON = 0.01f
        val SPEED_CYCLE = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.8f)
    }
}
