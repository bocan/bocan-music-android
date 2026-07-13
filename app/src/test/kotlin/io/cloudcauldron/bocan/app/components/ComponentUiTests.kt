package io.cloudcauldron.bocan.app.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.app.library.AlbumUi
import io.cloudcauldron.bocan.app.library.TrackUi
import io.cloudcauldron.bocan.app.theme.BocanTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ComponentUiTests {
    @get:Rule
    val compose = createComposeRule()

    private fun track() = TrackUi(
        id = 1, title = "Subdivisions", artist = "Rush", album = "Signals", albumId = 5, albumArtistId = 7,
        durationLabel = "5:34", durationMs = 334_000, loved = false, rating = 0, artworkHash = null, pending = false
    )

    @Test
    fun `track row merges into one talkback sentence`() {
        compose.setContent {
            BocanTheme { TrackRow(track = track(), onClick = {}, onLongClick = {}) }
        }
        compose.onNodeWithContentDescription("Subdivisions, Rush, Signals, 5:34").assertIsDisplayed()
    }

    @Test
    fun `a pending track appends not synced yet to its description`() {
        compose.setContent {
            BocanTheme { TrackRow(track = track().copy(pending = true), onClick = {}, onLongClick = {}) }
        }
        compose.onNodeWithContentDescription("Subdivisions, Rush, Signals, 5:34, not synced yet").assertIsDisplayed()
    }

    @Test
    fun `empty state renders its message and fires its action`() {
        var clicked = false
        compose.setContent {
            BocanTheme {
                EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "Nothing here",
                    message = "Sync to fill it",
                    actionLabel = "Sync now",
                    onAction = { clicked = true }
                )
            }
        }
        compose.onNodeWithText("Nothing here").assertIsDisplayed()
        compose.onNodeWithText("Sync to fill it").assertIsDisplayed()
        compose.onNodeWithText("Sync now").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun `an album cell taps with its own id`() {
        var tappedId = -1L
        val album = AlbumUi(id = 42, name = "Moving Pictures", artist = "Rush", year = 1981, artworkHash = null, trackCount = 7)
        compose.setContent {
            BocanTheme { AlbumCell(album = album, onClick = { tappedId = album.id }) }
        }
        compose.onNodeWithContentDescription("Moving Pictures, Rush, 1981, 7 songs").performClick()
        assertEquals(42L, tappedId)
    }
}
