package io.cloudcauldron.bocan.app.surfaces

import android.content.Intent
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Back
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Action.TapLink
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Gone
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.IntentSent
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.PlayerState
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Text
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import io.cloudcauldron.bocan.persistence.model.PlayState
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Podcasts home (continue listening, the shows grid), show detail (description, every
 * overflow entry, show notes with a secure and an insecure link), and playing an episode.
 */
class PodcastsCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("podcasts", table(ctx))
        finishCrawl()
    }

    companion object {
        private val SHOW_SCREEN = Navigated("show", E2ETags.Show.DESCRIPTION_TOGGLE)
        private val NOTES_OPEN = Visible(E2ETags.Common.SHEET_HANDLE)

        fun table(c: CrawlContext): List<Step> = home(c) + tidewater(c) + nightShift()

        private fun home(c: CrawlContext) = listOf(
            Step(E2ETags.Nav.PODCASTS, Tap, Navigated("podcasts", E2ETags.Podcasts.SHOWS_GRID)),
            Step(
                E2ETags.Rows.CONTINUE,
                Tap,
                PlayerState(playing = true, mediaIdPrefix = c.episode("Fixture Episode Two")),
                row = "Fixture Episode Two"
            ),
            Step(E2ETags.MiniPlayer.PLAY_PAUSE, Tap, PlayerState(playing = false))
        )

        private fun tidewater(c: CrawlContext): List<Step> {
            val one = "Fixture Episode One"
            fun stateOfOne() = runBlocking { c.graph.database.episodeStateDao().state("fixture-episode-1")?.playState }
            return listOf(
                Step(E2ETags.Rows.SHOW, Tap, SHOW_SCREEN, row = "Tidewater Talks"),
                Step(E2ETags.Show.DESCRIPTION_TOGGLE, Tap, Text(E2ETags.Show.DESCRIPTION_TOGGLE, c.string(R.string.show_description_less))),
                Step(E2ETags.Show.DESCRIPTION_TOGGLE, Tap, Text(E2ETags.Show.DESCRIPTION_TOGGLE, c.string(R.string.show_description_more))),
                Step(E2ETags.Rows.EPISODE_MENU, Tap, Visible(E2ETags.EpisodeMenu.MARK_PLAYED), row = one),
                Step(E2ETags.EpisodeMenu.MARK_PLAYED, Tap, Holds("'$one' stored as played") { stateOfOne() == PlayState.Played }),
                Step(E2ETags.Rows.EPISODE_MENU, Tap, Visible(E2ETags.EpisodeMenu.MARK_UNPLAYED), row = one),
                Step(E2ETags.EpisodeMenu.MARK_UNPLAYED, Tap, Holds("'$one' stored as unplayed") { stateOfOne() != PlayState.Played }),
                Step(E2ETags.Rows.EPISODE_MENU, Tap, Visible(E2ETags.EpisodeMenu.SHOW_NOTES), row = one),
                Step(E2ETags.EpisodeMenu.SHOW_NOTES, Tap, NOTES_OPEN),
                Step(CrawlTags.SYSTEM_BACK, Back, Gone(E2ETags.Common.SHEET_HANDLE)),
                Step(E2ETags.Rows.EPISODE, Tap, PlayerState(playing = true, mediaIdPrefix = c.episode(one)), row = one),
                Step(E2ETags.Common.BACK, Tap, Navigated("podcasts", E2ETags.Podcasts.SHOWS_GRID))
            )
        }

        private fun nightShift(): List<Step> {
            val pilot = "Night Shift Pilot"
            return listOf(
                Step(E2ETags.Rows.SHOW, Tap, SHOW_SCREEN, row = "The Night Shift Radio"),
                Step(E2ETags.Rows.EPISODE_MENU, Tap, Visible(E2ETags.EpisodeMenu.SHOW_NOTES), row = pilot),
                Step(E2ETags.EpisodeMenu.SHOW_NOTES, Tap, NOTES_OPEN),
                Step(CrawlTags.TEXT_LINK, TapLink("Episode page"), IntentSent(Intent.ACTION_VIEW, "https://example.invalid/night-shift")),
                Step(CrawlTags.TEXT_LINK, TapLink("old archive"), Visible(E2ETags.ShowNotes.INSECURE_CANCEL)),
                Step(E2ETags.ShowNotes.INSECURE_CANCEL, Tap, Gone(E2ETags.ShowNotes.INSECURE_CANCEL)),
                Step(CrawlTags.TEXT_LINK, TapLink("old archive"), Visible(E2ETags.ShowNotes.INSECURE_OPEN)),
                Step(
                    E2ETags.ShowNotes.INSECURE_OPEN,
                    Tap,
                    IntentSent(Intent.ACTION_VIEW, "http://example.invalid/night-shift-archive")
                )
            )
        }
    }
}
