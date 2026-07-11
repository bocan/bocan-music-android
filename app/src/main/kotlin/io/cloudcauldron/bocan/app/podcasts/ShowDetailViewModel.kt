package io.cloudcauldron.bocan.app.podcasts

import io.cloudcauldron.bocan.app.data.PodcastPreferencesSource
import io.cloudcauldron.bocan.persistence.daos.EpisodeStateDao
import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.podcast.EpisodePlaybackRules
import io.cloudcauldron.bocan.playback.queue.PlaybackTransport
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The show detail header. */
data class ShowHeaderUi(
    val title: String = "",
    val author: String? = null,
    val descriptionHtml: String? = null,
    val artworkHash: String? = null
)

/** The show detail screen state: header and episodes newest-first. */
data class ShowDetailUiState(val header: ShowHeaderUi = ShowHeaderUi(), val episodes: List<EpisodeUi> = emptyList())

/**
 * Drives one show's detail. Playing an episode plays it in the context of the show's
 * list; mark played and mark unplayed write only the phone-local episode_state (allowed,
 * and syncs never overwrite it). A played episode played again resets to in-progress at 0.
 */
@Suppress("LongParameterList") // Distinct injected collaborators, kept explicit for testability; no cohesive sub-object.
class ShowDetailViewModel(
    private val podcastId: Long,
    private val podcastDao: PodcastDao,
    private val episodeStateDao: EpisodeStateDao,
    private val transport: PlaybackTransport,
    private val preferences: PodcastPreferencesSource,
    dispatchers: CoroutineDispatchers,
    private val now: () -> Instant = Instant::now
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<ShowDetailUiState> =
        combine(podcastDao.observeShow(podcastId), podcastDao.observeEpisodesWithState(podcastId)) { show, rows ->
            ShowDetailUiState(
                header = ShowHeaderUi(show?.title.orEmpty(), show?.author, show?.descriptionHtml, show?.artworkHash),
                episodes = rows.map { it.toUi() }
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), ShowDetailUiState())

    /** Play [episodeId] in the context of the show's episode list. A played episode restarts. */
    fun play(episodeId: String) {
        scope.launch {
            val ids = state.value.episodes.map { it.id }
            val index = ids.indexOf(episodeId).coerceAtLeast(0)
            if (state.value.episodes.firstOrNull { it.id == episodeId }?.progress is EpisodeProgressUi.Played) {
                episodeStateDao.upsert(EpisodeStateEntity(episodeId, 0, PlayState.InProgress, now()))
            }
            transport.playEpisodes(ids, index)
            applyShowSpeed()
        }
    }

    private suspend fun applyShowSpeed() {
        val showDefault = podcastDao.observeShow(podcastId).first()?.defaultSpeed
        val speed = EpisodePlaybackRules.effectiveSpeed(
            showOverride = preferences.showSpeed(podcastId).first(),
            showDefault = showDefault,
            appDefault = preferences.appDefaultSpeed.first()
        )
        transport.setSpeed(speed.toFloat())
    }

    fun markPlayed(episodeId: String) {
        scope.launch { episodeStateDao.markPlayed(episodeId, now()) }
    }

    fun markUnplayed(episodeId: String) {
        scope.launch { episodeStateDao.upsert(EpisodeStateEntity(episodeId, 0, PlayState.Unplayed, now())) }
    }

    fun dispose() = scope.cancel()

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
