package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Look
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Action.TypeFrom
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Empty
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Shows
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import io.cloudcauldron.bocan.app.sync.FakeMac
import io.cloudcauldron.bocan.app.sync.FakeMacRule
import io.cloudcauldron.bocan.persistence.model.DownloadState
import io.cloudcauldron.bocan.sync.engine.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * The welcome flow against the loopback fake Mac (phase 17 slice 6): the Mac row, the code
 * field with wrong codes and Start over, then the right code and the first-sync page. One run
 * can leave that page by only one of its two buttons, so there are two tables: Sync later,
 * with the Mac trickling every file so the first sync is still running when it is pressed, and
 * Done, after the first sync has brought the whole fixture library over the wire.
 */
class PairingCrawl : CrawlTest(FixtureProfile.Unpaired, completeOnboarding = false) {
    @get:Rule
    val fakeMac = FakeMacRule()

    @Test
    fun crawlToSyncLater() {
        fakeMac.mac.slowFiles()
        crawl("pairing, then sync later", syncLaterTable(ctx))
        finishCrawl()
    }

    @Test
    fun crawlToDone() {
        crawl("pairing, then done", doneTable(ctx))
        finishCrawl()
    }

    companion object {
        private const val SYNC_TIMEOUT_MS = 60_000L

        private fun mac() = FakeMac.current()

        private fun confirms() = mac().requests.count { it == "/v1/pair/confirm" }

        /** The pairing steps both tables share, ending on the first-sync page with exactly one confirm sent. */
        private fun pairing() = listOf(
            Step(E2ETags.Onboarding.GET_STARTED, Tap, Visible(E2ETags.Pairing.MAC_ROW, FakeMac.SERVICE_NAME)),
            Step(E2ETags.Pairing.MAC_ROW, Tap, Visible(E2ETags.Pairing.CODE), row = FakeMac.SERVICE_NAME)
        )

        fun syncLaterTable(c: CrawlContext): List<Step> {
            val mismatch = Shows(c.string(R.string.pairing_code_mismatch))
            return pairing() + listOf(
                Step(E2ETags.Pairing.CODE, TypeFrom("a wrong code") { mac().wrongCode() }, mismatch),
                Step(E2ETags.Pairing.CODE, Look, Empty(E2ETags.Pairing.CODE)),
                Step(E2ETags.Pairing.CODE, TypeFrom("a second wrong code") { mac().wrongCode() }, mismatch),
                Step(E2ETags.Pairing.CODE, Look, Empty(E2ETags.Pairing.CODE)),
                Step(E2ETags.Pairing.CODE, TypeFrom("a third wrong code") { mac().wrongCode() }, Visible(E2ETags.Pairing.START_OVER)),
                // The code check is the phone's own: a wrong code must never reach the Mac.
                Step(E2ETags.Pairing.START_OVER, Look, Holds("no pair/confirm sent for a wrong code") { confirms() == 0 }),
                Step(E2ETags.Pairing.START_OVER, Tap, Visible(E2ETags.Pairing.MAC_ROW, FakeMac.SERVICE_NAME)),
                Step(E2ETags.Pairing.MAC_ROW, Tap, Visible(E2ETags.Pairing.CODE), row = FakeMac.SERVICE_NAME),
                Step(E2ETags.Pairing.CODE, typeTheShownCode(), Visible(E2ETags.Onboarding.SYNC_LATER)),
                Step(
                    E2ETags.Onboarding.SYNC_LATER,
                    Look,
                    Holds("the first sync transferring, slowed by the Mac") { syncState(c) is SyncState.Transferring },
                    timeoutMs = SYNC_TIMEOUT_MS
                ),
                Step(E2ETags.Onboarding.SYNC_LATER, Tap, Navigated("library", E2ETags.Nav.SETTINGS)),
                Step(
                    E2ETags.Nav.SETTINGS,
                    Look,
                    Holds("the first sync stopped with nothing downloaded") {
                        val state = syncState(c)
                        state !is SyncState.Transferring && state !is SyncState.CheckingManifest && downloadedTracks(c) == 0
                    }
                )
            )
        }

        fun doneTable(c: CrawlContext): List<Step> = pairing() + listOf(
            Step(E2ETags.Pairing.CODE, typeTheShownCode(), Visible(E2ETags.Onboarding.DONE)),
            Step(E2ETags.Onboarding.DONE, Look, Holds("one pair/confirm sent") { confirms() == 1 }),
            Step(
                E2ETags.Onboarding.DONE,
                Look,
                Holds("every fixture track downloaded over the wire") { downloadedTracks(c) == mac().manifest.tracks.size },
                timeoutMs = SYNC_TIMEOUT_MS
            ),
            Step(E2ETags.Onboarding.DONE, Tap, Navigated("library", E2ETags.Nav.SETTINGS))
        )

        private fun typeTheShownCode() = TypeFrom("the code the Mac shows") { mac().shownCode.orEmpty() }

        private fun syncState(c: CrawlContext): SyncState = c.graph.syncCoordinator.syncState.value

        private fun downloadedTracks(c: CrawlContext): Int =
            runBlocking { c.graph.database.syncDao().allTracks().count { it.downloadState == DownloadState.Downloaded } }
    }
}
