package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.PlayerState
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import org.junit.Test

/** Now Playing for a podcast: skip back and forward by the configured seconds, the speed chip, and the chapters sheet. */
class PodcastPlayerCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("podcast player", table(ctx))
        finishCrawl()
    }

    companion object {
        private const val EPISODE = "Fixture Episode One"
        private const val MS_PER_SECOND = 1_000L
        private const val TOLERANCE_MS = 1_500L

        // From the fixture's chapters/fixture-episode-1.json.
        private const val MIDDLE_CHAPTER = "The middle"
        private const val MIDDLE_CHAPTER_MS = 40_000L

        fun table(c: CrawlContext): List<Step> {
            fun forwardMs() = c.now(c.graph.podcastPreferences.skipForwardSeconds) * MS_PER_SECOND
            fun backMs() = c.now(c.graph.podcastPreferences.skipBackSeconds) * MS_PER_SECOND
            return listOf(
                Step(E2ETags.Nav.PODCASTS, Tap, Navigated("podcasts", E2ETags.Podcasts.SHOWS_GRID)),
                Step(E2ETags.Rows.SHOW, Tap, Navigated("show", E2ETags.Show.DESCRIPTION_TOGGLE), row = "Tidewater Talks"),
                Step(E2ETags.Rows.EPISODE, Tap, PlayerState(playing = true, mediaIdPrefix = c.episode(EPISODE)), row = EPISODE),
                Step(E2ETags.MiniPlayer.OPEN, Tap, Navigated("podcast player", E2ETags.NowPlaying.SKIP_FORWARD)),
                Step(E2ETags.NowPlaying.PLAY_PAUSE, Tap, PlayerState(playing = false)),
                Step(
                    E2ETags.NowPlaying.SKIP_FORWARD,
                    Tap,
                    PlayerState(what = "moved forward by the skip-forward setting") { it.positionMs >= forwardMs() - TOLERANCE_MS }
                ),
                Step(
                    E2ETags.NowPlaying.SKIP_BACK,
                    Tap,
                    PlayerState(what = "moved back by the skip-back setting") {
                        it.positionMs <= forwardMs() - backMs() + TOLERANCE_MS
                    }
                ),
                Step(E2ETags.NowPlaying.SPEED, Tap, PlayerState(what = "speed changed from 1.0") { it.speed != 1.0f }),
                Step(E2ETags.NowPlaying.OVERFLOW, Tap, Visible(E2ETags.NowPlaying.MENU_CHAPTERS)),
                Step(E2ETags.NowPlaying.MENU_CHAPTERS, Tap, Visible(E2ETags.Rows.CHAPTER, MIDDLE_CHAPTER)),
                Step(
                    E2ETags.Rows.CHAPTER,
                    Tap,
                    PlayerState(what = "at the chapter '$MIDDLE_CHAPTER'") {
                        it.positionMs in MIDDLE_CHAPTER_MS..MIDDLE_CHAPTER_MS + TOLERANCE_MS * 2
                    },
                    row = MIDDLE_CHAPTER
                )
            )
        }
    }
}
