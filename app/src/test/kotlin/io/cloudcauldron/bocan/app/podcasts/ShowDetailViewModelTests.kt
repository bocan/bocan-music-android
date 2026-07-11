package io.cloudcauldron.bocan.app.podcasts

import app.cash.turbine.test
import io.cloudcauldron.bocan.app.player.FakePlaybackTransport
import io.cloudcauldron.bocan.persistence.model.EpisodeProgressRow
import io.cloudcauldron.bocan.persistence.model.PlayState
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShowDetailViewModelTests {
    private val dao = FakePodcastDao()
    private val stateDao = FakeEpisodeStateDao()
    private val transport = FakePlaybackTransport()
    private val preferences = FakePodcastPreferences()
    private val fixedNow = Instant.parse("2026-07-11T00:00:00Z")

    private fun viewModel(): ShowDetailViewModel {
        val d = UnconfinedTestDispatcher()
        return ShowDetailViewModel(
            podcastId = 1,
            podcastDao = dao,
            episodeStateDao = stateDao,
            transport = transport,
            preferences = preferences,
            dispatchers = CoroutineDispatchers(io = d, default = d, main = d),
            now = { fixedNow }
        )
    }

    @Test
    fun `mark played writes a played state row`() = runTest {
        val vm = viewModel()
        vm.markPlayed("e1")
        assertEquals(PlayState.Played, stateDao.states["e1"]?.playState)
        assertEquals(fixedNow, stateDao.states["e1"]?.completedAt)
        vm.dispose()
    }

    @Test
    fun `mark unplayed clears progress to an unplayed row`() = runTest {
        val vm = viewModel()
        vm.markUnplayed("e1")
        assertEquals(PlayState.Unplayed, stateDao.states["e1"]?.playState)
        assertEquals(0L, stateDao.states["e1"]?.playPositionMs)
        vm.dispose()
    }

    @Test
    fun `playing a played episode restarts it and applies the show speed`() = runTest {
        dao.shows.value = listOf(podcast(1, defaultSpeed = 1.5))
        dao.episodesWithState.value = listOf(
            EpisodeProgressRow(episode("e1", podcastId = 1), playStateWire = PlayState.Played.wire, playPositionMs = 600_000)
        )
        val vm = viewModel()

        vm.state.test {
            var state = awaitItem()
            while (state.episodes.isEmpty()) state = awaitItem()
            vm.play("e1")
            cancelAndIgnoreRemainingEvents()
        }

        // Restarted to in-progress at 0, played back in the show's list, at the show's default speed.
        assertEquals(PlayState.InProgress, stateDao.states["e1"]?.playState)
        assertEquals(0L, stateDao.states["e1"]?.playPositionMs)
        assertEquals(listOf(listOf("e1")), transport.playedEpisodes)
        assertEquals(1.5f, transport.speeds.last())
        vm.dispose()
    }
}
