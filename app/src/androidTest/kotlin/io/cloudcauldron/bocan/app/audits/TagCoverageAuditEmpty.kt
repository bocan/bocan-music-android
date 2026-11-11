package io.cloudcauldron.bocan.app.audits

import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.navigation.Destination
import org.junit.Test

/** The tag audit for a paired phone with nothing synced yet: the empty states and their buttons. */
class TagCoverageAuditEmpty : AuditTest(FixtureProfile.EmptyPaired) {
    @Test
    fun emptyLibraryPodcastsAndSearch() {
        ui.awaitTag(E2ETags.Nav.LIBRARY)
        check("library, paired and empty", Destination.Library::class)
        tap(E2ETags.Nav.PODCASTS)
        check("podcasts, empty", Destination.Podcasts::class)
        tap(E2ETags.Nav.SEARCH)
        check("search, empty library", Destination.Search::class)

        finish(ROUTES)
    }

    companion object {
        val ROUTES = setOf(Destination.Library::class, Destination.Podcasts::class, Destination.Search::class)
    }
}
