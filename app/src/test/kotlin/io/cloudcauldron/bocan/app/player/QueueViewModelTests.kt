package io.cloudcauldron.bocan.app.player

import app.cash.turbine.test
import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.queue.NowPlayingItem
import io.cloudcauldron.bocan.playback.queue.PlayerUiState
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTests {
    private fun dispatchers(): CoroutineDispatchers {
        val d = UnconfinedTestDispatcher()
        return CoroutineDispatchers(io = d, default = d, main = d)
    }

    private fun item(id: Long, durationMs: Long = 200_000) = NowPlayingItem("track:$id", "Track $id", "Artist", "Album", null, durationMs)

    @Test
    fun `reorder delegates to transport move`() = runTest {
        val transport = FakePlaybackTransport()
        val vm = QueueViewModel(transport, dispatchers())
        vm.move(from = 3, to = 1)
        assertEquals(listOf(3 to 1), transport.moves)
        vm.dispose()
    }

    @Test
    fun `removing the current item delegates to transport removeAt so playback can advance`() = runTest {
        val transport = FakePlaybackTransport(
            PlayerUiState(queue = listOf(item(1), item(2), item(3)), queueIndex = 0)
        )
        val vm = QueueViewModel(transport, dispatchers())
        vm.removeAt(0)
        assertEquals(listOf(0), transport.removes)
        vm.dispose()
    }

    @Test
    fun `clear delegates to transport clear`() = runTest {
        val transport = FakePlaybackTransport()
        val vm = QueueViewModel(transport, dispatchers())
        vm.clear()
        assertEquals(1, transport.clears)
        vm.dispose()
    }

    @Test
    fun `state maps the queue with an up next tally`() = runTest {
        val transport = FakePlaybackTransport(
            PlayerUiState(
                queue = listOf(item(1, 100_000), item(2, 200_000), item(3, 300_000)),
                queueIndex = 0
            )
        )
        val vm = QueueViewModel(transport, dispatchers())
        vm.state.test {
            var ui = awaitItem()
            while (ui.items.isEmpty()) ui = awaitItem()
            assertEquals(3, ui.items.size)
            assertEquals(0, ui.currentIndex)
            assertEquals(2, ui.upNextCount)
            assertEquals(500_000, ui.upNextRemainingMs)
            assertEquals(true, ui.items.first().isCurrent)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }
}
