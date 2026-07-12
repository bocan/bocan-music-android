package io.cloudcauldron.bocan.app.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.app.effects.BandSlider
import io.cloudcauldron.bocan.app.player.SeekBar
import io.cloudcauldron.bocan.app.player.TransportControls
import io.cloudcauldron.bocan.app.podcasts.EpisodeProgressUi
import io.cloudcauldron.bocan.app.podcasts.EpisodeRow
import io.cloudcauldron.bocan.app.podcasts.EpisodeUi
import io.cloudcauldron.bocan.app.theme.BocanTheme
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Exact labels and state descriptions for the highest-traffic controls. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SemanticsTests {
    @get:Rule
    val compose = createComposeRule()

    private fun hasState(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    @Test
    fun `shuffle and repeat expose their state, play exposes its action`() {
        compose.setContent {
            BocanTheme {
                TransportControls(
                    isPlaying = false,
                    repeatMode = RepeatMode.One,
                    shuffleActive = true,
                    onPlayPause = {},
                    onPrevious = {},
                    onNext = {},
                    onShuffle = {},
                    onShuffleLongPress = {},
                    onCycleRepeat = {}
                )
            }
        }
        compose.onNodeWithContentDescription("Shuffle").assert(hasState("On"))
        compose.onNodeWithContentDescription("Repeat").assert(hasState("Repeating this track"))
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun `an eq band slider reads frequency and gain as text`() {
        compose.setContent {
            BocanTheme { BandSlider(centerHz = 1000.0, gainDb = 3.0, onGain = {}) }
        }
        compose.onNodeWithContentDescription("1 kilohertz").assert(hasState("plus 3.0 decibels"))
    }

    @Test
    fun `the seek bar reads a time position, not a percentage`() {
        compose.setContent {
            BocanTheme { SeekBar(positionMs = 90_000, durationMs = 180_000, onSeek = {}) }
        }
        val elapsed = android.text.format.DateUtils.formatElapsedTime(90)
        val total = android.text.format.DateUtils.formatElapsedTime(180)
        compose.onNodeWithContentDescription("Seek").assert(hasState("$elapsed of $total"))
    }

    @Test
    fun `an episode row merges into one sentence with its listening state`() {
        val episode = EpisodeUi(
            id = "ep1",
            title = "The Lost Music",
            publishedAt = Instant.now().minusSeconds(3600),
            durationMs = 1_800_000,
            podcastId = 4,
            hasChapters = false,
            descriptionHtml = null,
            progress = EpisodeProgressUi.Unplayed
        )
        compose.setContent {
            BocanTheme {
                EpisodeRow(episode = episode, onPlay = {}, onMarkPlayed = {}, onMarkUnplayed = {}, onShowNotes = {})
            }
        }
        compose.onNodeWithContentDescription("Unplayed", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("The Lost Music", substring = true).assertIsDisplayed()
    }
}
