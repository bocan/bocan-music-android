package io.cloudcauldron.bocan.app.podcasts

import io.cloudcauldron.bocan.persistence.daos.PodcastDao
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The Podcasts home: the continue-listening shelf and the subscribed-shows grid. */
data class PodcastsUiState(val continueListening: List<ContinueUi> = emptyList(), val shows: List<ShowUi> = emptyList())

/**
 * Drives the Podcasts home. The continue-listening shelf and unplayed badges come
 * straight from the DB and update live as listening progress changes: no writes here.
 */
class PodcastsViewModel(podcastDao: PodcastDao, dispatchers: CoroutineDispatchers) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    val state: StateFlow<PodcastsUiState> =
        combine(
            podcastDao.observeContinueListening(),
            podcastDao.observeShows(),
            podcastDao.observeUnplayedCounts()
        ) { continuing, shows, counts ->
            val unplayedById = counts.associate { it.podcastId to it.unplayed }
            PodcastsUiState(
                continueListening = continuing.map { it.toContinueUi() },
                shows = shows.map { it.toUi(unplayedById[it.id] ?: 0) }
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), PodcastsUiState())

    fun dispose() = scope.cancel()

    private companion object {
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
