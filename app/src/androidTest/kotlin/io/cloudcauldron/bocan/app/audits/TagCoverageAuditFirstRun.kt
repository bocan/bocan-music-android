package io.cloudcauldron.bocan.app.audits

import androidx.compose.ui.test.performClick
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.navigation.Destination
import org.junit.Test

/**
 * The tag audit for a fresh unpaired install: the welcome flow, the unpaired sync screen with
 * the phase 14 demo, the pairing screen while it searches, and the demo episode's chapters
 * (the one episode whose chapters a debug build serves without a Mac).
 */
class TagCoverageAuditFirstRun : AuditTest(FixtureProfile.UnpairedDemo, completeOnboarding = false) {
    @Test
    fun onboardingUnpairedSyncPairingAndChapters() {
        check("onboarding welcome", Destination.Onboarding::class)
        tap(E2ETags.Onboarding.GET_STARTED)
        ui.awaitTag(E2ETags.Onboarding.PAIR_SKIP)
        check("onboarding pair step")
        tap(E2ETags.Onboarding.PAIR_SKIP)

        ui.awaitTag(E2ETags.Nav.SETTINGS)
        check("library with the demo")
        tap(E2ETags.Nav.SETTINGS)
        tap(E2ETags.Settings.SYNC)
        ui.awaitTag(E2ETags.Sync.REMOVE_DEMO)
        check("sync, unpaired with the demo", Destination.SyncStatus::class)
        tap(E2ETags.Sync.PAIR)
        check("pairing, searching", Destination.Pairing::class)
        back()
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Nav.PODCASTS)
        tapRow(E2ETags.Rows.SHOW, DEMO_SHOW)
        ui.awaitRow(DEMO_EPISODE).performClick()
        awaitPlayer("'$DEMO_EPISODE' playing") { it.current?.mediaId == DEMO_EPISODE_MEDIA_ID && it.isPlaying }
        tap(E2ETags.MiniPlayer.OPEN)
        tap(E2ETags.NowPlaying.OVERFLOW)
        tap(E2ETags.NowPlaying.MENU_CHAPTERS)
        ui.awaitAny(E2ETags.Rows.CHAPTER)
        check("chapters sheet")
        back()

        finish(ROUTES)
    }

    companion object {
        // The phase 14 demo, as shipped in app/src/main/assets/demo/manifest.json.
        private const val DEMO_SHOW = "Bòcan Demo Podcast"
        private const val DEMO_EPISODE = "Welcome to the demo"
        private const val DEMO_EPISODE_MEDIA_ID = "episode:demo-episode-1"

        val ROUTES = setOf(Destination.Onboarding::class, Destination.SyncStatus::class, Destination.Pairing::class)
    }
}
