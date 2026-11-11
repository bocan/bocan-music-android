package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import org.junit.Test

/** A paired phone with nothing synced: the library's empty state offers Sync now, which opens sync status. */
class EmptyLibraryCrawl : CrawlTest(FixtureProfile.EmptyPaired) {
    @Test
    fun crawl() {
        crawl("empty library", table(ctx))
        finishCrawl()
    }

    companion object {
        fun table(c: CrawlContext) = listOf(
            Step(E2ETags.Nav.LIBRARY, Tap, Visible(E2ETags.EmptyState.ACTION)),
            // The empty state's Sync now opens sync status, where the sync itself starts (SyncCrawl).
            Step(E2ETags.EmptyState.ACTION, Tap, Navigated("sync, paired", E2ETags.Sync.SYNC_NOW))
        )
    }
}
