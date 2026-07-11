package io.cloudcauldron.bocan.app.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.app.FakeLibraryPreferences
import io.cloudcauldron.bocan.app.FakeSearchDao
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SearchViewModelTests {
    private val searchDao = FakeSearchDao()
    private val prefs = FakeLibraryPreferences()

    private fun viewModel(debounceMs: Long = 0) = SearchViewModel(
        searchDao = searchDao,
        prefs = prefs,
        dispatchers = CoroutineDispatchers(io = Dispatchers.IO, default = UnconfinedTestDispatcher()),
        debounceMs = debounceMs
    )

    @Test
    fun `results are sectioned into tracks, albums, and artists`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("rush")
        vm.state.test {
            var ui = awaitItem()
            while (ui.tracks.isEmpty()) ui = awaitItem()
            assertEquals("rush", ui.query)
            assertEquals(listOf("rush"), ui.tracks.map { it.title })
            assertEquals(listOf("rush"), ui.albums.map { it.name })
            assertEquals(listOf("rush"), ui.artists.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }

    @Test
    fun `a settled query issues a single search, not one per keystroke`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("r")
        vm.onQueryChange("ru")
        vm.onQueryChange("rush")
        vm.state.test {
            var ui = awaitItem()
            while (ui.tracks.isEmpty()) ui = awaitItem()
            assertEquals(listOf("rush"), ui.tracks.map { it.title })
            // Only the settled "rush" reaches the DAO; the intermediate keystrokes never do.
            assertEquals(1, searchDao.invocations)
            cancelAndIgnoreRemainingEvents()
        }
        vm.dispose()
    }

    @Test
    fun `a blank query shows no result sections`() = runTest {
        val vm = viewModel()
        val ui = vm.state.value
        assertTrue(ui.tracks.isEmpty() && ui.albums.isEmpty() && ui.artists.isEmpty())
        assertTrue(!ui.hasQuery)
        vm.dispose()
    }

    @Test
    fun `submitting a query records it as recent`() = runTest {
        val vm = viewModel()
        vm.onQueryChange("marillion")
        vm.onSubmit()
        runCurrent()
        assertEquals(listOf("marillion"), prefs.recentSearches.value)
        vm.dispose()
    }
}
