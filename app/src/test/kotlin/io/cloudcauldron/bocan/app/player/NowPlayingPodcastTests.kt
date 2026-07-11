package io.cloudcauldron.bocan.app.player

import app.cash.turbine.test
import io.cloudcauldron.bocan.app.FakeLibraryDao
import io.cloudcauldron.bocan.app.podcasts.FakePodcastDao
import io.cloudcauldron.bocan.app.podcasts.FakePodcastPreferences
import io.cloudcauldron.bocan.app.podcasts.episode
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.MediaId
import io.cloudcauldron.bocan.playback.PlayerVolume
import io.cloudcauldron.bocan.playback.SleepTimer
import io.cloudcauldron.bocan.playback.podcast.ChaptersRepository
import io.cloudcauldron.bocan.playback.queue.NowPlayingItem
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingPodcastTests {
    private val podcastDao = FakePodcastDao()
    private val preferences = FakePodcastPreferences()
    private val noopVolume = object : PlayerVolume {
        override suspend fun getVolume(): Float = 1f
        override suspend fun setVolume(volume: Float) = Unit
        override suspend fun pause() = Unit
    }

    private fun viewModel(transport: FakePlaybackTransport): NowPlayingViewModel {
        val d = UnconfinedTestDispatcher()
        val dispatchers = CoroutineDispatchers(io = d, default = d, main = d)
        val sleepTimer = SleepTimer(noopVolume, emptyFlow(), dispatchers)
        val chapters = ChaptersRepository({ null }, dispatchers, AppLog.forCategory(LogCategory.Podcast))
        return NowPlayingViewModel(
            transport = transport,
            libraryDao = FakeLibraryDao(),
            podcastDao = podcastDao,
            chaptersRepository = chapters,
            preferences = preferences,
            sleepTimer = sleepTimer,
            dispatchers = dispatchers
        )
    }

    private fun episodePlaying(positionMs: Long = 60_000, durationMs: Long = 600_000): PlayerUiState = PlayerUiState(
        current = NowPlayingItem(MediaId.Episode("e1").raw, "Episode e1", "Show", null, null, durationMs),
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = true
    )

    @Test
    fun `episode is flagged as podcast in state`() = runTest {
        podcastDao.episodes["e1"] = episode("e1", podcastId = 7)
        val transport = FakePlaybackTransport(episodePlaying())
        val vm = viewModel(transport)

        vm.state.test {
            var state = awaitItem()
            while (!state.podcast.isPodcast) state = awaitItem()
            assertTrue(state.podcast.isPodcast)
            assertEquals(7L, state.podcast.podcastId)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }

    @Test
    fun `skip back and forward seek by the configured intervals clamped to bounds`() = runTest {
        podcastDao.episodes["e1"] = episode("e1", podcastId = 7)
        preferences.skipBackSeconds.value = 15
        preferences.skipForwardSeconds.value = 30
        val transport = FakePlaybackTransport(episodePlaying(positionMs = 60_000, durationMs = 600_000))
        val vm = viewModel(transport)

        vm.state.test {
            var state = awaitItem()
            while (!state.podcast.isPodcast) state = awaitItem()
            vm.skipBack()
            vm.skipForward()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(45_000L, 90_000L), transport.seeks)
        vm.dispose()
    }

    @Test
    fun `cycle speed advances a preset and persists it as a per-show override`() = runTest {
        podcastDao.episodes["e1"] = episode("e1", podcastId = 7)
        val transport = FakePlaybackTransport(episodePlaying())
        val vm = viewModel(transport)

        vm.state.test {
            var state = awaitItem()
            while (!state.podcast.isPodcast) state = awaitItem()
            vm.cycleSpeed()
            cancelAndIgnoreRemainingEvents()
        }

        // From 1.0x the next preset is 1.2x, written to the show's override, not any synced table.
        assertEquals(1.2f, transport.speeds.last())
        assertEquals(1.2f.toDouble(), preferences.showSpeedValue(7))
        vm.dispose()
    }
}
