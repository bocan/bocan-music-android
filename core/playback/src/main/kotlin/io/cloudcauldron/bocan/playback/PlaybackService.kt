package io.cloudcauldron.bocan.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.playback.queue.QueuePersistence
import io.cloudcauldron.bocan.playback.queue.QueueSnapshot
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import io.cloudcauldron.bocan.playback.queue.fromPlayer
import io.cloudcauldron.bocan.playback.queue.toPlayer
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
 * The notification comes from Media3's default provider (customised in phase 10);
 * [onGetLibraryRoot] returns a stub browse tree until phase 10 fills it for Auto.
 * Task removal keeps playback alive when something is playing.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private val log = AppLog.forCategory(LogCategory.Playback)
    private lateinit var scope: CoroutineScope
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var persistence: QueuePersistence

    private val components: PlaybackComponents get() = (application as PlaybackHost).playbackComponents

    override fun onCreate() {
        super.onCreate()
        val graph = components
        scope = CoroutineScope(SupervisorJob() + graph.dispatchers.main)
        player = graph.playerFactory.create()
        persistence = graph.queuePersistence

        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .build()

        graph.statsRecorder.attach(player, scope)
        graph.episodeRecorder.attach(player, scope)
        restoreQueuePaused()
        observeForPersistence()
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

    /** A minimal browse tree so library controllers can connect; phase 10 fills it for Auto. */
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(com.google.common.collect.ImmutableList.of(), params))
    }

    private companion object {
        const val ROOT_ID = "root"
        const val PERSIST_INTERVAL_MS = 5_000L
    }
}
