package io.cloudcauldron.bocan.app.journeys

import androidx.lifecycle.Lifecycle
import io.cloudcauldron.bocan.app.AlbumScreen
import io.cloudcauldron.bocan.app.E2ETest
import io.cloudcauldron.bocan.app.LibraryScreen
import io.cloudcauldron.bocan.app.MiniPlayer
import io.cloudcauldron.bocan.app.OnboardingScreen
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test

/**
 * A fresh unpaired install: skip the welcome flow, the phase 14 demo album seeds itself,
 * play it, the mini player shows the cover, and the bar is still there after the app goes
 * to the background and comes back.
 */
class FirstRunJourney : E2ETest(FixtureProfile.UnpairedDemo, completeOnboarding = false) {
    @Test
    fun skipOnboardingThenPlayTheDemoAlbum() {
        OnboardingScreen(ui).skip()

        val library = LibraryScreen(ui)
        library.albumsTab()
        library.openAlbum(DEMO_ALBUM)
        screenshot("demo-album")
        AlbumScreen(ui).playTrack(DEMO_TRACK)

        awaitPlayer("the first demo track playing with its position advancing") {
            it.current?.mediaId == DEMO_TRACK_MEDIA_ID && it.isPlaying && it.positionMs > 0
        }
        val miniPlayer = MiniPlayer(ui)
        miniPlayer.awaitArtwork()
        assertNotNull(player.current?.artworkUri, "the playing demo track carries no cover for the mini player")
        screenshot("playing")

        val scenario = composeRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        miniPlayer.awaitArtwork()
        assertEquals(DEMO_TRACK_MEDIA_ID, player.current?.mediaId, "reopening the app lost the playing track")
        screenshot("reopened")
    }

    private companion object {
        // The phase 14 demo, as shipped in app/src/main/assets/demo/manifest.json.
        const val DEMO_ALBUM = "Bòcan Demo"
        const val DEMO_TRACK = "Demo Audio 1"
        const val DEMO_TRACK_MEDIA_ID = "track:9000000001"
    }
}
