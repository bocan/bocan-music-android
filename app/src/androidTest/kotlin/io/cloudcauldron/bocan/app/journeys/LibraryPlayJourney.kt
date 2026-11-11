package io.cloudcauldron.bocan.app.journeys

import io.cloudcauldron.bocan.app.AlbumScreen
import io.cloudcauldron.bocan.app.E2ETest
import io.cloudcauldron.bocan.app.LibraryScreen
import io.cloudcauldron.bocan.app.MiniPlayer
import io.cloudcauldron.bocan.app.NowPlayingScreen
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import org.junit.Test

/**
 * The fixture library on a paired (unreachable) Mac: open an album, play a track, watch the
 * position advance, then next and previous from Now Playing. Playback is paused before the
 * skips so a 4 second fixture track cannot end on its own and move the queue under the test.
 */
class LibraryPlayJourney : E2ETest(FixtureProfile.PairedLibrary) {
    @Test
    fun playFromAnAlbumThenSkipForwardAndBack() {
        val first = trackMediaId(FIRST)
        val second = trackMediaId(SECOND)

        val library = LibraryScreen(ui)
        library.albumsTab()
        library.openAlbum(ALBUM)
        AlbumScreen(ui).playTrack(FIRST)

        awaitPlayer("'$FIRST' playing") { it.current?.mediaId == first && it.isPlaying }
        val started = player.positionMs
        awaitPlayer("the position advancing past $started ms") { it.positionMs > started }

        val miniPlayer = MiniPlayer(ui)
        miniPlayer.playPause()
        awaitPlayer("paused from the mini player") { !it.isPlaying }

        miniPlayer.open()
        val nowPlaying = NowPlayingScreen(ui)
        nowPlaying.next()
        awaitPlayer("next moved to '$SECOND'") { it.current?.mediaId == second }
        screenshot("now-playing-next")
        nowPlaying.previous()
        awaitPlayer("previous moved back to '$FIRST'") { it.current?.mediaId == first }
    }

    private companion object {
        // A two-track fixture album: the smallest queue where next and previous both have somewhere to go.
        const val ALBUM = "Breakwater"
        const val FIRST = "Breakwater 1"
        const val SECOND = "Breakwater 2"
    }
}
