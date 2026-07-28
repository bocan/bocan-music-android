package io.cloudcauldron.bocan.app.search

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.app.FakeLibraryPreferences
import io.cloudcauldron.bocan.app.FakeSearchDao
import io.cloudcauldron.bocan.app.library.LibraryCallbacks
import io.cloudcauldron.bocan.app.theme.BocanTheme
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression cover for the jumping search cursor: the field used to display the
 * view model's copy of the query, which arrives a beat late and yanked the
 * cursor back mid-word. The field text must never depend on the view model
 * round trip, so it is pinned here against a view model that never responds at
 * all (a StandardTestDispatcher that is never advanced): the worst-case lag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SearchFieldTests {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `typed text stays in the field even when the view model state lags behind`() {
        val vm = SearchViewModel(
            searchDao = FakeSearchDao(),
            prefs = FakeLibraryPreferences(),
            dispatchers = CoroutineDispatchers(io = Dispatchers.IO, default = StandardTestDispatcher())
        )
        compose.setContent {
            BocanTheme {
                SearchScreen(viewModel = vm, callbacks = LibraryCallbacks())
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("marillion")

        compose.onNode(hasSetTextAction()).assertTextContains("marillion")
        vm.dispose()
    }
}
