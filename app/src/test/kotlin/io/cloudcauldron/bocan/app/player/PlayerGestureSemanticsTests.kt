package io.cloudcauldron.bocan.app.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.app.theme.BocanTheme
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The gesture surface must expose exactly the four custom actions, and the sheet must label rows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PlayerGestureSemanticsTests {
    @get:Rule
    val compose = createComposeRule()

    private val labels = PlayerGestureLabels(
        next = "Next track",
        previous = "Previous track",
        details = "Song details",
        close = "Close player"
    )

    @Test
    fun `the gesture surface exposes exactly the four custom actions`() {
        compose.setContent {
            BocanTheme {
                val state = rememberPlayerGestureState()
                val actions = PlayerGestureActions(onNext = {}, onPrevious = {}, onOpenDetails = {}, onDismiss = {})
                Box(
                    Modifier
                        .size(200.dp)
                        .testTag("surface")
                        .playerGestures(state, reducedMotion = false, actions = actions, labels = labels)
                )
            }
        }
        val node = compose.onNodeWithTag("surface").fetchSemanticsNode()
        val custom = node.config[SemanticsActions.CustomActions]
        assertEquals(listOf("Next track", "Previous track", "Song details", "Close player"), custom.map { it.label })
    }

    @Test
    fun `a details row merges its label and value into one sentence`() {
        val track = SongDetailsUiState.Track(
            title = "Blue Nile",
            artist = "The Blue Nile",
            album = "Hats",
            albumArtist = null,
            year = 1989,
            genre = null,
            trackNumber = 3,
            trackTotal = 7,
            discNumber = null,
            discTotal = null,
            format = "flac",
            lossless = true,
            durationMs = 240_000,
            sizeBytes = 30_000_000,
            bitrateKbps = 1000,
            playCount = null,
            lastPlayedAt = null,
            loved = false,
            rating = 0
        )
        compose.setContent { BocanTheme { SongDetailsSheet(state = track, onDismiss = {}) } }
        compose.onNodeWithContentDescription("Artist, The Blue Nile").assertExists()
        compose.onNodeWithContentDescription("Format, flac, lossless").assertExists()
        compose.onNodeWithText("Blue Nile").assertExists()
    }
}
