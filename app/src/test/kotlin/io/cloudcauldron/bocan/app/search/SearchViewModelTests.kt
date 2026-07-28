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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
    fun `a query still searching is not reported as no results`() = runTest {
        val vm = SearchViewModel(
            searchDao = searchDao,
            prefs = prefs,
            dispatchers = CoroutineDispatchers(io = Dispatchers.IO, default = UnconfinedTestDispatcher(testScheduler)),
            debounceMs = 200
        )
        val subscriber = launch { vm.state.collect {} }
        advanceTimeBy(250) // the initial blank query settles

        vm.onQueryChange("rush")
        runCurrent()
        val typing = vm.state.value
        assertEquals("rush", typing.query)
        assertTrue(typing.tracks.isEmpty())
        // The sections are empty only because the debounce has not fired yet;
        // claiming no results here is the flash this guards against.
        assertTrue(!typing.noResults)

        advanceTimeBy(250)
        assertEquals(listOf("rush"), vm.state.value.tracks.map { it.title })
        subscriber.cancel()
        vm.dispose()
    }

    @Test
    fun `no results is reported only for the query the results were computed for`() {
        val stillSearching = SearchUiState(query = "x", resultsFor = "")
        assertTrue(!stillSearching.noResults)
        val settledEmpty = SearchUiState(query = "x", resultsFor = "x")
        assertTrue(settledEmpty.noResults)
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
