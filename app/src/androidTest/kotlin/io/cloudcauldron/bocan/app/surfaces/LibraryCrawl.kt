package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Swipe
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.PlayerState
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Shows
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.TrackSort
import org.junit.Test

/** The library root: the bottom bar, the six tabs, the folder browser, every sort entry, the A to Z rail, shuffle all, and the mini player. */
class LibraryCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("library", table(ctx))
        finishCrawl()
    }

    companion object {
        fun table(c: CrawlContext): List<Step> = tabs() + folders(c) + sorts(c) + rail(c) + shuffleAndChrome() + unsyncedLyrics(c)

        // Synced lyrics are crawled in NowPlayingCrawl; the fixture's unsynced kind is checked here.
        // From the fixture's lyrics/9001000025.txt.
        private fun unsyncedLyrics(c: CrawlContext) = listOf(
            Step(E2ETags.Library.TAB_ALBUMS, Tap, Visible(E2ETags.Library.ALBUMS_GRID)),
            Step(E2ETags.Rows.ALBUM, Tap, Navigated("album", E2ETags.Detail.PLAY), row = "Heather"),
            Step(E2ETags.Rows.TRACK, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("Heather 1")), row = "Heather 1"),
            Step(E2ETags.MiniPlayer.OPEN, Tap, Navigated("now playing", E2ETags.NowPlaying.SEEK)),
            Step(E2ETags.NowPlaying.QUICK_LYRICS, Tap, Shows("Unsynced fixture lyrics."))
        )

        private fun tabs() = listOf(
            Step(E2ETags.Nav.LIBRARY, Tap, Navigated("library", E2ETags.Library.TAB_ALBUMS)),
            Step(E2ETags.Library.TAB_ARTISTS, Tap, Visible(E2ETags.Rows.ARTIST, "Aurora Lane")),
            Step(E2ETags.Library.TAB_ALBUMS, Tap, Visible(E2ETags.Library.ALBUMS_GRID)),
            Step(E2ETags.Library.TAB_SONGS, Tap, Visible(E2ETags.Rows.TRACK, "Breakwater 1")),
            Step(E2ETags.Library.TAB_GENRES, Tap, Visible(E2ETags.Rows.GENRE, "Folk")),
            Step(E2ETags.Library.TAB_PLAYLISTS, Tap, Visible(E2ETags.Rows.PLAYLIST, "Fixture Mix")),
            Step(E2ETags.Library.TAB_FOLDERS, Tap, Visible(E2ETags.Rows.FOLDER, "Fixture"))
        )

        private fun folders(c: CrawlContext) = listOf(
            Step(E2ETags.Rows.FOLDER, Tap, Visible(E2ETags.Rows.FOLDER, "aurora-lane"), row = "Fixture"),
            Step(E2ETags.Rows.FOLDER, Tap, Visible(E2ETags.Rows.FOLDER, "first-light"), row = "aurora-lane"),
            Step(E2ETags.Rows.FOLDER, Tap, Visible(E2ETags.Rows.FOLDER_TRACK, "1-02 first-light-2.mp3"), row = "first-light"),
            Step(
                E2ETags.Rows.FOLDER_TRACK,
                Tap,
                PlayerState(playing = true, mediaIdPrefix = c.track("First Light 2")),
                row = "1-02 first-light-2.mp3"
            )
        )

        private fun sorts(c: CrawlContext): List<Step> {
            val albums = listOf(
                AlbumSort.Year to R.string.sort_album_year,
                AlbumSort.Artist to R.string.sort_album_artist,
                AlbumSort.Name to R.string.sort_album_name
            ).flatMap { (sort, label) ->
                sortSteps(c.string(label), Holds("the album sort is $sort") { c.now(c.graph.libraryPreferences.albumSort) == sort })
            }
            val songs = listOf(
                TrackSort.Artist to R.string.sort_song_artist,
                TrackSort.Album to R.string.sort_song_album,
                TrackSort.Title to R.string.sort_song_title
            ).flatMap { (sort, label) ->
                sortSteps(c.string(label), Holds("the song sort is $sort") { c.now(c.graph.libraryPreferences.trackSort) == sort })
            }
            return listOf(Step(E2ETags.Library.TAB_ALBUMS, Tap, Visible(E2ETags.Library.ALBUMS_GRID))) + albums +
                listOf(Step(E2ETags.Library.TAB_SONGS, Tap, Visible(E2ETags.Rows.TRACK, "Breakwater 1"))) + songs
        }

        private fun sortSteps(label: String, stored: Postcondition) = listOf(
            Step(E2ETags.Sort.OPEN, Tap, Visible(E2ETags.Sort.OPTION, label)),
            Step(E2ETags.Sort.OPTION, Tap, stored, row = label)
        )

        // Songs are back in title order here. A drag down the whole rail ends past the last letter
        // with songs, so the list lands on the first W title. "A" is the one letter every height
        // shows, and has no songs, so a tap on it goes to the first letter that has some.
        private fun rail(c: CrawlContext) = listOf(
            Step(E2ETags.Library.ALPHABET_RAIL, Swipe(Direction.Down), Visible(E2ETags.Rows.TRACK, "Wading 1")),
            Step(E2ETags.Library.ALPHABET_LETTER, Tap, Visible(E2ETags.Rows.TRACK, "Breakwater 1"), row = "A"),
            Step(E2ETags.Rows.TRACK, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("Breakwater 1")), row = "Breakwater 1")
        )

        private fun shuffleAndChrome() = listOf(
            Step(E2ETags.Library.SHUFFLE_ALL, Tap, PlayerState(playing = true, mediaIdPrefix = "track:")),
            Step(E2ETags.MiniPlayer.PLAY_PAUSE, Tap, PlayerState(playing = false)),
            Step(E2ETags.MiniPlayer.OPEN, Tap, Navigated("now playing", E2ETags.NowPlaying.SEEK)),
            Step(E2ETags.NowPlaying.COLLAPSE, Tap, Visible(E2ETags.MiniPlayer.OPEN)),
            Step(E2ETags.Nav.PODCASTS, Tap, Navigated("podcasts", E2ETags.Podcasts.SHOWS_GRID)),
            Step(E2ETags.Nav.SEARCH, Tap, Navigated("search", E2ETags.Search.FIELD)),
            Step(E2ETags.Nav.SETTINGS, Tap, Navigated("settings", E2ETags.Settings.SYNC)),
            Step(E2ETags.Nav.LIBRARY, Tap, Navigated("library", E2ETags.Library.TAB_SONGS))
        )
    }
}
