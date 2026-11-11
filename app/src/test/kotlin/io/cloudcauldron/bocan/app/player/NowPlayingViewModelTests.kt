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
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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

    private fun viewModel(transport: FakePlaybackTransport, prefetch: (String?) -> Unit = {}): NowPlayingViewModel {
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
            dispatchers = dispatchers,
            prefetchArtwork = prefetch
        )
    }

    private fun item(mediaId: String, title: String, art: String?) = io.cloudcauldron.bocan.playback.queue.NowPlayingItem(
        mediaId = mediaId,
        title = title,
        artist = "A",
        album = "Al",
        artworkUri = art,
        durationMs = 1
    )

    private fun queueState(index: Int, vararg items: io.cloudcauldron.bocan.playback.queue.NowPlayingItem) =
        io.cloudcauldron.bocan.playback.queue.PlayerUiState(current = items.getOrNull(index), queue = items.toList(), queueIndex = index)

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

    @Test
    fun `a middle item resolves both neighbors from the queue order`() = runTest {
        val transport = FakePlaybackTransport(
            queueState(1, item("track:1", "One", "artA"), item("track:2", "Two", "artB"), item("track:3", "Three", "artC"))
        )
        val vm = viewModel(transport)
        val state = vm.state.first { it.hasItem }
        assertEquals("One", state.previous?.title)
        assertEquals("Three", state.next?.title)
        assertEquals("artC", state.next?.artworkUri)
        vm.dispose()
    }

    @Test
    fun `the ends of the queue yield null neighbors`() = runTest {
        val transport = FakePlaybackTransport(queueState(0, item("track:1", "One", null), item("track:2", "Two", null)))
        val vm = viewModel(transport)
        val first = vm.state.first { it.hasItem }
        assertNull(first.previous)
        assertEquals("Two", first.next?.title)
        transport.emit(queueState(1, item("track:1", "One", null), item("track:2", "Two", null)))
        val last = vm.state.first { it.hasItem && it.next == null }
        assertEquals("One", last.previous?.title)
        assertNull(last.next)
        vm.dispose()
    }

    @Test
    fun `under repeat all the ends wrap, as the transport's next and previous do`() = runTest {
        val items = arrayOf(item("track:1", "One", null), item("track:2", "Two", null), item("track:3", "Three", null))
        val transport = FakePlaybackTransport(queueState(2, *items).copy(repeatMode = RepeatMode.All))
        val vm = viewModel(transport)
        val last = vm.state.first { it.hasItem }
        assertEquals("Two", last.previous?.title)
        assertEquals("One", last.next?.title)
        transport.emit(queueState(0, *items).copy(repeatMode = RepeatMode.All))
        val first = vm.state.first { it.hasItem && it.title == "One" }
        assertEquals("Three", first.previous?.title)
        assertEquals("Two", first.next?.title)
        vm.dispose()
    }

    @Test
    fun `repeat one and a single item never wrap`() = runTest {
        val items = arrayOf(item("track:1", "One", null), item("track:2", "Two", null))
        val transport = FakePlaybackTransport(queueState(1, *items).copy(repeatMode = RepeatMode.One))
        val vm = viewModel(transport)
        assertNull(vm.state.first { it.hasItem }.next)
        transport.emit(queueState(0, item("track:1", "One", null)).copy(repeatMode = RepeatMode.All))
        val single = vm.state.first { it.hasItem && it.previous == null && it.next == null }
        assertEquals("One", single.title)
        vm.dispose()
    }

    @Test
    fun `a queue mutation re-warms both neighbors through Coil`() = runTest {
        val warmed = mutableListOf<String?>()
        val transport = FakePlaybackTransport(
            queueState(1, item("track:1", "One", "artA"), item("track:2", "Two", "artB"), item("track:3", "Three", "artC"))
        )
        val vm = viewModel(transport) { warmed += it }
        assertTrue(warmed.contains("artA") && warmed.contains("artC"))
        transport.emit(
            queueState(1, item("track:1", "One", "artA"), item("track:2", "Two", "artB"), item("track:9", "Nine", "artZ"))
        )
        assertTrue(warmed.contains("artZ"))
        vm.dispose()
    }
}
