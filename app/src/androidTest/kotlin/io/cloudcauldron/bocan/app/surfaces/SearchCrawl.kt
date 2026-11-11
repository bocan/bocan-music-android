package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Clear
import io.cloudcauldron.bocan.app.surfaces.Action.Submit
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Action.Type
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Gone
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.PlayerState
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import org.junit.Test

/** Search: typing, each result kind, submitting to the history, a recent search, and clearing the history. */
class SearchCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("search", table(ctx))
        finishCrawl()
    }

    companion object {
        private const val QUERY = "Aurora"
        private const val ALBUM_QUERY = "Light"

        fun table(c: CrawlContext) = listOf(
            Step(E2ETags.Nav.SEARCH, Tap, Navigated("search", E2ETags.Search.FIELD)),
            Step(E2ETags.Search.FIELD, Type(QUERY), Visible(E2ETags.Search.ARTIST_ROW, "Aurora Lane")),
            Step(E2ETags.Search.ARTIST_ROW, Tap, Navigated("artist", E2ETags.Detail.SHUFFLE_ALL), row = "Aurora Lane"),
            Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Rows.TRACK, "First Light 1")),
            Step(E2ETags.Rows.TRACK, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("First Light 1")), row = "First Light 1"),
            Step(
                E2ETags.Search.FIELD,
                Submit,
                Holds("'$QUERY' saved as a recent search") { QUERY in c.now(c.graph.libraryPreferences.recentSearches) }
            ),
            Step(E2ETags.Search.FIELD, Clear, Visible(E2ETags.Search.RECENT_ROW, QUERY)),
            // An artist's name matches no album, so albums are searched by their own name.
            Step(E2ETags.Search.FIELD, Type(ALBUM_QUERY), Visible(E2ETags.Search.ALBUM_ROW, "First Light")),
            Step(E2ETags.Search.ALBUM_ROW, Tap, Navigated("album", E2ETags.Detail.PLAY), row = "First Light"),
            Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Rows.TRACK, "First Light 1")),
            Step(E2ETags.Search.FIELD, Clear, Visible(E2ETags.Search.RECENT_ROW, QUERY)),
            Step(E2ETags.Search.RECENT_ROW, Tap, Visible(E2ETags.Search.ARTIST_ROW, "Aurora Lane"), row = QUERY),
            Step(E2ETags.Search.FIELD, Clear, Visible(E2ETags.Search.CLEAR_RECENT)),
            Step(E2ETags.Search.CLEAR_RECENT, Tap, Gone(E2ETags.Search.RECENT_ROW, QUERY))
        )
    }
}
