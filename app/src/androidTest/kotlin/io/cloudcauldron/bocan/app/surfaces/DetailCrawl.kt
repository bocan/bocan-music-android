package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.LongPress
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.PlayerState
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import org.junit.Test

/** Album (and the track actions sheet), artist, genre, and playlist detail: manual, smart, and a folder's child. */
class DetailCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("detail screens", table(ctx))
        finishCrawl()
    }

    companion object {
        fun table(c: CrawlContext): List<Step> = album(c) + artistAndGenre(c) + playlists(c)

        private val ALBUM_SCREEN = Navigated("album", E2ETags.Detail.PLAY)
        private val ARTIST_SCREEN = Navigated("artist", E2ETags.Detail.SHUFFLE_ALL)

        private fun album(c: CrawlContext): List<Step> {
            val first = c.track("Breakwater 1")
            val second = c.track("Breakwater 2")
            return listOf(
                Step(E2ETags.Library.TAB_ALBUMS, Tap, Visible(E2ETags.Library.ALBUMS_GRID)),
                Step(E2ETags.Rows.ALBUM, Tap, ALBUM_SCREEN, row = "Breakwater"),
                Step(E2ETags.Detail.PLAY, Tap, PlayerState(playing = true, mediaIdPrefix = first)),
                Step(E2ETags.Rows.TRACK, LongPress, Visible(E2ETags.TrackActions.PLAY_NEXT), row = "Breakwater 2"),
                Step(
                    E2ETags.TrackActions.PLAY_NEXT,
                    Tap,
                    PlayerState(what = "'Breakwater 2' queued twice, next after the current item") { state ->
                        state.queue.count { it.mediaId == second } == 2 && state.queue.getOrNull(state.queueIndex + 1)?.mediaId == second
                    }
                ),
                Step(E2ETags.Rows.TRACK, LongPress, Visible(E2ETags.TrackActions.ADD_TO_QUEUE), row = "Breakwater 1"),
                Step(
                    E2ETags.TrackActions.ADD_TO_QUEUE,
                    Tap,
                    PlayerState(what = "'Breakwater 1' last in the queue") { it.queue.lastOrNull()?.mediaId == first }
                ),
                Step(E2ETags.Rows.TRACK, LongPress, Visible(E2ETags.TrackActions.GO_TO_ARTIST), row = "Breakwater 1"),
                Step(E2ETags.TrackActions.GO_TO_ARTIST, Tap, ARTIST_SCREEN),
                Step(E2ETags.Common.BACK, Tap, ALBUM_SCREEN),
                Step(E2ETags.Rows.TRACK, LongPress, Visible(E2ETags.TrackActions.GO_TO_ALBUM), row = "Breakwater 1"),
                Step(E2ETags.TrackActions.GO_TO_ALBUM, Tap, ALBUM_SCREEN),
                Step(E2ETags.Common.BACK, Tap, ALBUM_SCREEN),
                Step(
                    E2ETags.Detail.SHUFFLE,
                    Tap,
                    PlayerState(playing = true, what = "playing a Breakwater track") { it.current?.mediaId in setOf(first, second) }
                ),
                Step(E2ETags.Rows.TRACK, Tap, PlayerState(playing = true, mediaIdPrefix = second), row = "Breakwater 2"),
                Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Library.ALBUMS_GRID))
            )
        }

        private fun artistAndGenre(c: CrawlContext): List<Step> {
            val auroraLane = c.tracksBy("Aurora Lane")
            return listOf(
                Step(E2ETags.Library.TAB_ARTISTS, Tap, Visible(E2ETags.Rows.ARTIST, "Aurora Lane")),
                Step(E2ETags.Rows.ARTIST, Tap, ARTIST_SCREEN, row = "Aurora Lane"),
                Step(E2ETags.Rows.ALBUM, Tap, ALBUM_SCREEN, row = "Low Sun"),
                Step(E2ETags.Common.BACK, Tap, ARTIST_SCREEN),
                Step(
                    E2ETags.Detail.SHUFFLE_ALL,
                    Tap,
                    PlayerState(playing = true, what = "playing an Aurora Lane track") { it.current?.mediaId in auroraLane }
                ),
                Step(E2ETags.Rows.TRACK, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("Low Sun 1")), row = "Low Sun 1"),
                Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Rows.ARTIST, "Aurora Lane")),
                Step(E2ETags.Library.TAB_GENRES, Tap, Visible(E2ETags.Rows.GENRE, "Folk")),
                Step(E2ETags.Rows.GENRE, Tap, Navigated("genre", E2ETags.Detail.SHUFFLE_ALL), row = "Folk"),
                Step(E2ETags.Detail.SHUFFLE_ALL, Tap, PlayerState(playing = true, mediaIdPrefix = "track:")),
                Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Rows.GENRE, "Folk"))
            )
        }

        private fun playlists(c: CrawlContext) = listOf(
            Step(E2ETags.Library.TAB_PLAYLISTS, Tap, Visible(E2ETags.Rows.PLAYLIST, "Fixture Mix")),
            Step(E2ETags.Rows.PLAYLIST, Tap, Navigated("manual playlist", E2ETags.Detail.PLAY), row = "Fixture Mix"),
            Step(E2ETags.Detail.PLAY, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("First Light 1"))),
            Step(E2ETags.Detail.SHUFFLE, Tap, PlayerState(playing = true, mediaIdPrefix = "track:")),
            Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Rows.PLAYLIST, "Loved Fixture Tracks")),
            Step(E2ETags.Rows.PLAYLIST, Tap, Navigated("smart playlist", E2ETags.Detail.PLAY), row = "Loved Fixture Tracks"),
            Step(E2ETags.Detail.PLAY, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("Crossings 1"))),
            Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Rows.PLAYLIST, "Folder Child")),
            Step(E2ETags.Rows.PLAYLIST, Tap, Navigated("a folder's child playlist", E2ETags.Detail.PLAY), row = "Folder Child"),
            Step(E2ETags.Detail.PLAY, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("Rue des Lilas 1"))),
            Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Rows.PLAYLIST, "Fixture Mix"))
        )
    }
}
