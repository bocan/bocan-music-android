package io.cloudcauldron.bocan.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import io.cloudcauldron.bocan.playback.browse.MediaTree
import io.cloudcauldron.bocan.playback.podcast.EpisodeProgressRecorder
import io.cloudcauldron.bocan.playback.queue.QueuePersistence
import io.cloudcauldron.bocan.playback.stats.PlayStatsRecorder

/**
 * The [PlaybackService] reaches its collaborators through the Application, which
 * implements this interface (manual DI, the same pattern as SyncHost). :core:playback
 * defines the seam; :app's AppGraph builds the components and the Application exposes
 * them here.
 */
@UnstableApi
interface PlaybackHost {
    val playbackComponents: PlaybackComponents
}

/** The object graph the service needs to build its player, session, and side effects. */
// A value holder for the service's collaborators; its width is the graph, not a smell.
@Suppress("LongParameterList")
@UnstableApi
class PlaybackComponents(
    val playerFactory: PlayerFactory,
    val mediaItemSource: MediaItemSource,
    val statsRecorder: PlayStatsRecorder,
    val episodeRecorder: EpisodeProgressRecorder,
    val queuePersistence: QueuePersistence,
    val mediaTree: MediaTree,
    val episodeSkipButtons: List<CommandButton>,
    val dispatchers: CoroutineDispatchers
) {
    /** The effects chain the service binds to its player, reached through the [playerFactory]. */
    val effectsChain get() = playerFactory.effectsChain
}
