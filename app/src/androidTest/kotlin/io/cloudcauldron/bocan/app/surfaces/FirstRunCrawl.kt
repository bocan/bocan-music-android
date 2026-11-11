package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Back
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Shows
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * A fresh unpaired install: the welcome flow's Get started and pair-step skip, then the
 * phase 14 demo removed from sync status, the library's not-paired state with its Pair
 * button, and the demo loaded again from that state.
 */
class FirstRunCrawl : CrawlTest(FixtureProfile.UnpairedDemo, completeOnboarding = false) {
    @Test
    fun crawl() {
        crawl("first run", table(ctx))
        finishCrawl()
    }

    companion object {
        fun table(c: CrawlContext): List<Step> {
            fun libraryTracks() = runBlocking { c.graph.database.syncDao().allTracks().size }
            return listOf(
                Step(E2ETags.Onboarding.GET_STARTED, Tap, Visible(E2ETags.Onboarding.PAIR_SKIP)),
                Step(E2ETags.Onboarding.PAIR_SKIP, Tap, Navigated("library", E2ETags.Nav.SETTINGS)),
                Step(E2ETags.Nav.SETTINGS, Tap, Navigated("settings", E2ETags.Settings.SYNC)),
                Step(E2ETags.Settings.SYNC, Tap, Navigated("sync, unpaired with the demo", E2ETags.Sync.REMOVE_DEMO)),
                Step(E2ETags.Sync.REMOVE_DEMO, Tap, Holds("the demo library removed") { libraryTracks() == 0 }),
                Step(E2ETags.Common.BACK, Tap, Navigated("settings", E2ETags.Settings.SYNC)),
                Step(E2ETags.Nav.LIBRARY, Tap, Visible(E2ETags.EmptyState.ACTION)),
                Step(E2ETags.EmptyState.ACTION, Tap, Shows(c.string(R.string.pairing_title))),
                Step(CrawlTags.SYSTEM_BACK, Back, Visible(E2ETags.EmptyState.SECONDARY)),
                Step(E2ETags.EmptyState.SECONDARY, Tap, Holds("the demo library loaded again") { libraryTracks() > 0 })
            )
        }
    }
}
