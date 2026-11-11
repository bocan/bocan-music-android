package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Back
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Flipped
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Gone
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Shows
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import io.cloudcauldron.bocan.sync.engine.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Sync status while paired to the (unreachable) fixture Mac: the three toggles, Sync now
 * reaching "unreachable" and stopping, Unpair and Remove media each cancelled and then
 * confirmed (last, since they empty the phone), and the pairing screen the unpaired state
 * offers.
 */
class SyncCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("sync", table(ctx))
        finishCrawl()
    }

    companion object {
        /** The coordinator's discovery window plus room for the sync attempt that follows it. */
        const val DISCOVERY_WAIT_MS = 30_000L

        fun unreachable(c: CrawlContext) =
            Holds("sync stopped at 'Mac unreachable'") { c.graph.syncCoordinator.syncState.value is SyncState.ServerUnreachable }

        fun table(c: CrawlContext): List<Step> {
            val pairingTitle = c.string(R.string.pairing_title)
            return listOf(
                Step(E2ETags.Nav.SETTINGS, Tap, Navigated("settings", E2ETags.Settings.SYNC)),
                Step(E2ETags.Settings.SYNC, Tap, Navigated("sync, paired", E2ETags.Sync.SYNC_NOW)),
                // Charging-only is enabled only while periodic sync is on, which is the default.
                Step(E2ETags.Sync.CHARGING_ONLY, Tap, Flipped),
                Step(E2ETags.Sync.PERIODIC, Tap, Flipped),
                Step(E2ETags.Sync.ON_DISCOVERY, Tap, Flipped),
                // Sync now first waits for discovery to find the Mac (SyncCoordinator.DISCOVERY_WINDOW_MS,
                // 20 s), showing Cancel; the fixture Mac never appears, so the sync then stops unreachable.
                Step(E2ETags.Sync.SYNC_NOW, Tap, Visible(E2ETags.Sync.CANCEL)),
                Step(E2ETags.Sync.CANCEL, Tap, Visible(E2ETags.Sync.SYNC_NOW)),
                Step(E2ETags.Sync.SYNC_NOW, Tap, unreachable(c), timeoutMs = DISCOVERY_WAIT_MS),
                Step(E2ETags.Sync.UNPAIR, Tap, Visible(E2ETags.Sync.UNPAIR_CONFIRM)),
                Step(E2ETags.Sync.UNPAIR_CANCEL, Tap, Gone(E2ETags.Sync.UNPAIR_CONFIRM)),
                Step(E2ETags.Sync.REMOVE_MEDIA, Tap, Visible(E2ETags.Sync.REMOVE_MEDIA_CONFIRM)),
                Step(E2ETags.Sync.REMOVE_MEDIA_CANCEL, Tap, Gone(E2ETags.Sync.REMOVE_MEDIA_CONFIRM)),
                Step(E2ETags.Sync.REMOVE_MEDIA, Tap, Visible(E2ETags.Sync.REMOVE_MEDIA_CONFIRM)),
                Step(
                    E2ETags.Sync.REMOVE_MEDIA_CONFIRM,
                    Tap,
                    Holds("no media left on the phone") { runBlocking { c.graph.syncCoordinator.storageBytes() } == 0L }
                ),
                Step(E2ETags.Sync.UNPAIR, Tap, Visible(E2ETags.Sync.UNPAIR_CONFIRM)),
                Step(E2ETags.Sync.UNPAIR_CONFIRM, Tap, Navigated("sync, unpaired", E2ETags.Sync.PAIR)),
                Step(E2ETags.Sync.PAIR, Tap, Shows(pairingTitle)),
                Step(CrawlTags.SYSTEM_BACK, Back, Visible(E2ETags.Sync.PAIR))
            )
        }
    }
}
