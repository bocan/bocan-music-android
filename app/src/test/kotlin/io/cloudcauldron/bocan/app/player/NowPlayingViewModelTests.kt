package io.cloudcauldron.bocan.app.player

import io.cloudcauldron.bocan.app.FakeLibraryDao
import io.cloudcauldron.bocan.app.podcasts.FakePodcastDao
import io.cloudcauldron.bocan.app.podcasts.FakePodcastPreferences
import io.cloudcauldron.bocan.observability.AppLog
import io.cloudcauldron.bocan.observability.LogCategory
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.PlayerVolume
import io.cloudcauldron.bocan.playback.SleepTimer
import io.cloudcauldron.bocan.playback.podcast.ChaptersRepository
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import io.cloudcauldron.bocan.playback.queue.ShuffleStrategy
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTests {
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
            podcastDao = FakePodcastDao(),
            chaptersRepository = chapters,
            preferences = FakePodcastPreferences(),
            sleepTimer = sleepTimer,
            dispatchers = dispatchers
        )
    }

    @Test
    fun `cycle repeat advances off to all to one to off`() = runTest {
        val transport = FakePlaybackTransport()
        val vm = viewModel(transport)
        vm.cycleRepeat()
        assertEquals(RepeatMode.All, transport.repeats.last())
        vm.dispose()
    }

    @Test
    fun `toggle shuffle turns shuffle on from off`() = runTest {
        val transport = FakePlaybackTransport()
        val vm = viewModel(transport)
        vm.toggleShuffle()
        assertEquals(ShuffleStrategy.FisherYates, transport.shuffles.last())
        vm.dispose()
    }

    @Test
    fun `toggle shuffle turns shuffle off when active`() = runTest {
        val transport = FakePlaybackTransport(
            io.cloudcauldron.bocan.playback.queue.PlayerUiState(shuffleActive = true)
        )
        val vm = viewModel(transport)
        vm.toggleShuffle()
        assertNull(transport.shuffles.last())
        vm.dispose()
    }
}
