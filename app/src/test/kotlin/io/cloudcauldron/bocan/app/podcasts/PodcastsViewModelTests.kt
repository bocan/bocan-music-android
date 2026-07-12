package io.cloudcauldron.bocan.app.podcasts

import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.model.EpisodeWithProgress
import io.cloudcauldron.bocan.persistence.model.UnplayedCount
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastsViewModelTests {
    private val dao = FakePodcastDao()

    private fun viewModel(): PodcastsViewModel {
        val d = UnconfinedTestDispatcher()
        return PodcastsViewModel(dao, CoroutineDispatchers(io = d, default = d, main = d))
    }

    @Test
    fun `continue-listening preserves DAO order and computes remaining time`() = runTest {
        dao.continueListening.value = listOf(
            EpisodeWithProgress(
                episode("e2", podcastId = 1, durationMs = 1_000_000),
                playPositionMs = 250_000,
                lastPlayedAt = Instant.EPOCH
            ),
            EpisodeWithProgress(episode("e1", podcastId = 1, durationMs = 600_000), playPositionMs = 300_000, lastPlayedAt = Instant.EPOCH)
        )
        val vm = viewModel()

        vm.state.test {
            var state = awaitItem()
            while (state.continueListening.isEmpty()) state = awaitItem()
            assertEquals(listOf("e2", "e1"), state.continueListening.map { it.episodeId })
            assertEquals(0.25f, state.continueListening[0].progress)
            assertEquals(750_000L, state.continueListening[0].remainingMs)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }

    @Test
    fun `a continue-listening card carries its show artwork hash`() = runTest {
        dao.shows.value = listOf(podcast(1, artworkHash = "showart1"))
        dao.continueListening.value = listOf(
            EpisodeWithProgress(episode("e1", podcastId = 1, durationMs = 600_000), playPositionMs = 100_000, lastPlayedAt = Instant.EPOCH)
        )
        val vm = viewModel()

        vm.state.test {
            var state = awaitItem()
            while (state.continueListening.isEmpty()) state = awaitItem()
            assertEquals("showart1", state.continueListening.first().artworkHash)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }

    @Test
    fun `unplayed badges compute per show and update reactively when marked played`() = runTest {
        dao.shows.value = listOf(podcast(1), podcast(2))
        dao.unplayedCounts.value = listOf(UnplayedCount(podcastId = 1, unplayed = 3), UnplayedCount(podcastId = 2, unplayed = 0))
        val vm = viewModel()

        vm.state.test {
            var state = awaitItem()
            while (state.shows.isEmpty()) state = awaitItem()
            assertEquals(3, state.shows.first { it.id == 1L }.unplayedCount)
            assertEquals(0, state.shows.first { it.id == 2L }.unplayedCount)

            // Marking one episode played drops the badge; the shelf reflects it live.
            dao.unplayedCounts.value = listOf(UnplayedCount(podcastId = 1, unplayed = 2))
            var next = awaitItem()
            while (next.shows.firstOrNull { it.id == 1L }?.unplayedCount != 2) next = awaitItem()
            assertEquals(2, next.shows.first { it.id == 1L }.unplayedCount)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }
}
