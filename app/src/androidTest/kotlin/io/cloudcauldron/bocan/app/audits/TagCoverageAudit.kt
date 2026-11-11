package io.cloudcauldron.bocan.app.audits

import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.cloudcauldron.bocan.app.Ui
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.navigation.Destination
import io.cloudcauldron.bocan.scrobble.providers.ProviderId
import org.junit.Test

/**
 * Every actionable node on every screen and sheet reachable with the paired fixture library
 * carries a stable tag (phase 17, slice 2). One test per area, so a failure names the area;
 * each test lists every gap it found, not only the first.
 */
class TagCoverageAudit : AuditTest(FixtureProfile.PairedLibrary) {
    @Test
    fun libraryTabsAndDetailScreens() {
        tap(E2ETags.Nav.LIBRARY)
        check("library", Destination.Library::class)
        LIBRARY_TABS.forEach { tab ->
            tap(tab)
            check("library tab $tab")
        }
        listOf(E2ETags.Library.TAB_ALBUMS, E2ETags.Library.TAB_SONGS).forEach { tab ->
            tap(tab)
            tap(E2ETags.Sort.OPEN)
            check("sort menu on $tab")
            back()
        }

        tap(E2ETags.Library.TAB_ALBUMS)
        ui.scrollToRow(E2ETags.Library.ALBUMS_GRID, ALBUM).performClick()
        check("album", Destination.AlbumDetail::class)
        ui.awaitRow(ALBUM_TRACK).performTouchInput { longClick() }
        check("track actions sheet")
        back()
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Library.TAB_ARTISTS)
        tapRow(E2ETags.Rows.ARTIST, ARTIST)
        check("artist", Destination.ArtistDetail::class)
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Library.TAB_GENRES)
        tapRow(E2ETags.Rows.GENRE, GENRE)
        check("genre", Destination.GenreDetail::class)
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Library.TAB_PLAYLISTS)
        listOf(MANUAL_PLAYLIST, SMART_PLAYLIST).forEach { playlist ->
            tapRow(E2ETags.Rows.PLAYLIST, playlist)
            check("playlist $playlist", Destination.PlaylistDetail::class)
            tap(E2ETags.Common.BACK)
        }

        tap(E2ETags.Library.TAB_FOLDERS)
        FOLDER_PATH.forEach { folder -> tapRow(E2ETags.Rows.FOLDER, folder) }
        check("folder of tracks")

        finish(LIBRARY_ROUTES)
    }

    @Test
    fun nowPlayingItsSheetsAndTheEqualizer() {
        tap(E2ETags.Library.TAB_ALBUMS)
        ui.scrollToRow(E2ETags.Library.ALBUMS_GRID, LYRICS_ALBUM).performClick()
        ui.awaitRow(LYRICS_TRACK).performClick()
        awaitPlayer("'$LYRICS_TRACK' playing") { it.current?.mediaId == trackMediaId(LYRICS_TRACK) && it.isPlaying }
        check("library with the mini player")

        tap(E2ETags.MiniPlayer.OPEN)
        check("now playing", Destination.NowPlaying::class)
        tap(E2ETags.NowPlaying.OVERFLOW)
        check("now playing overflow menu")
        back()
        tap(E2ETags.NowPlaying.QUICK_LYRICS)
        ui.awaitAny(E2ETags.Rows.LYRIC_LINE)
        check("synced lyrics")
        tap(E2ETags.NowPlaying.QUICK_LYRICS)
        tap(E2ETags.NowPlaying.QUICK_SPEED)
        check("speed sheet")
        back()
        tap(E2ETags.NowPlaying.QUICK_DETAILS)
        check("song details sheet")
        back()
        tap(E2ETags.NowPlaying.QUICK_QUEUE)
        check("queue sheet")
        tap(E2ETags.Queue.CLEAR)
        check("clear queue dialog")
        tap(E2ETags.Queue.CLEAR_CANCEL)
        back()
        tap(E2ETags.NowPlaying.OVERFLOW)
        tap(E2ETags.NowPlaying.MENU_SLEEP)
        check("sleep timer, idle")
        tap(E2ETags.SleepTimer.END_OF_TRACK)
        ui.awaitTag(E2ETags.SleepTimer.CANCEL)
        check("sleep timer, armed")
        tap(E2ETags.SleepTimer.CANCEL)
        back()

        tap(E2ETags.NowPlaying.QUICK_EQUALIZER)
        check("equalizer", Destination.Equalizer::class)
        tapScrolled(E2ETags.Equalizer.REPLAYGAIN_TRACK)
        ui.awaitTag(E2ETags.Equalizer.PREAMP)
        check("equalizer with the preamp")
        tapScrolled(E2ETags.Equalizer.SAVE_PRESET)
        check("save preset dialog")
        tap(E2ETags.Equalizer.SAVE_CANCEL)
        tap(E2ETags.Common.BACK)
        tap(E2ETags.NowPlaying.COLLAPSE)
        ui.awaitTag(E2ETags.MiniPlayer.OPEN)

        finish(PLAYER_ROUTES)
    }

    @Test
    fun podcastsShowsAndThePodcastPlayer() {
        tap(E2ETags.Nav.PODCASTS)
        check("podcasts home", Destination.Podcasts::class)
        tapRow(E2ETags.Rows.SHOW, SHOW)
        ui.awaitRow(EPISODE)
        check("show", Destination.ShowDetail::class)
        composeRule.onNode(hasTestTag(E2ETags.Rows.EPISODE_MENU) and hasAnySibling(Ui.rowTitled(EPISODE)), useUnmergedTree = true)
            .performClick()
        check("episode menu")
        tap(E2ETags.EpisodeMenu.SHOW_NOTES)
        check("show notes sheet")
        back()

        ui.awaitRow(EPISODE).performClick()
        awaitPlayer("'$EPISODE' playing") { it.current?.mediaId == episodeMediaId(EPISODE) && it.isPlaying }
        tap(E2ETags.MiniPlayer.OPEN)
        ui.awaitTag(E2ETags.NowPlaying.SKIP_FORWARD)
        check("podcast now playing", Destination.NowPlaying::class)

        finish(PODCAST_ROUTES)
    }

    @Test
    fun searchResultsAndRecentSearches() {
        tap(E2ETags.Nav.SEARCH)
        check("search, empty", Destination.Search::class)
        ui.awaitTag(E2ETags.Search.FIELD).performTextInput(QUERY)
        ui.awaitRow(E2ETags.Search.ARTIST_ROW, ARTIST)
        check("search results")
        ui.awaitTag(E2ETags.Search.FIELD).performImeAction()
        ui.awaitTag(E2ETags.Search.FIELD).performTextClearance()
        ui.awaitRow(E2ETags.Search.RECENT_ROW, QUERY)
        check("recent searches")

        finish(setOf(Destination.Search::class))
    }

    @Test
    fun settingsAndEverySection() {
        tap(E2ETags.Nav.SETTINGS)
        check("settings", Destination.Settings::class)

        tap(E2ETags.Settings.SYNC)
        check("sync, paired", Destination.SyncStatus::class)
        tapScrolled(E2ETags.Sync.UNPAIR)
        check("unpair dialog")
        tap(E2ETags.Sync.UNPAIR_CANCEL)
        tapScrolled(E2ETags.Sync.REMOVE_MEDIA)
        check("remove media dialog")
        tap(E2ETags.Sync.REMOVE_MEDIA_CANCEL)
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Settings.PLAYBACK)
        check("playback settings", Destination.PlaybackSettings::class)
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Settings.PODCASTS)
        check("podcast settings", Destination.PodcastSettings::class)
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Settings.SCROBBLING)
        check("scrobbling", Destination.ScrobbleSettings::class)
        tapScrolled(E2ETags.Scrobble.provider(ProviderId.LISTENBRAINZ, E2ETags.Scrobble.CONNECT))
        check("scrobble token dialog")
        tap(E2ETags.Scrobble.TOKEN_CANCEL)
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Settings.APPEARANCE)
        check("appearance", Destination.AppearanceSettings::class)
        tap(E2ETags.Common.BACK)

        tap(E2ETags.Settings.ABOUT)
        check("about", Destination.About::class)
        tap(E2ETags.Common.BACK)

        finish(SETTINGS_ROUTES)
    }

    companion object {
        // Names from the fixture manifest (scripts/demo-media/fixture.py), never positions.
        private const val ALBUM = "Breakwater"
        private const val ALBUM_TRACK = "Breakwater 1"
        private const val LYRICS_ALBUM = "First Light"
        private const val LYRICS_TRACK = "First Light 1"
        private const val ARTIST = "Aurora Lane"
        private const val GENRE = "Folk"
        private const val MANUAL_PLAYLIST = "Fixture Mix"
        private const val SMART_PLAYLIST = "Loved Fixture Tracks"
        private val FOLDER_PATH = listOf("Fixture", "aurora-lane", "first-light")
        private const val SHOW = "Tidewater Talks"
        private const val EPISODE = "Fixture Episode One"
        private const val QUERY = "Aurora"

        private val LIBRARY_TABS = listOf(
            E2ETags.Library.TAB_ARTISTS,
            E2ETags.Library.TAB_ALBUMS,
            E2ETags.Library.TAB_SONGS,
            E2ETags.Library.TAB_GENRES,
            E2ETags.Library.TAB_PLAYLISTS,
            E2ETags.Library.TAB_FOLDERS
        )

        private val LIBRARY_ROUTES = setOf(
            Destination.Library::class,
            Destination.AlbumDetail::class,
            Destination.ArtistDetail::class,
            Destination.GenreDetail::class,
            Destination.PlaylistDetail::class
        )
        private val PLAYER_ROUTES = setOf(Destination.NowPlaying::class, Destination.Equalizer::class)
        private val PODCAST_ROUTES = setOf(Destination.Podcasts::class, Destination.ShowDetail::class, Destination.NowPlaying::class)
        private val SETTINGS_ROUTES = setOf(
            Destination.Settings::class,
            Destination.SyncStatus::class,
            Destination.PlaybackSettings::class,
            Destination.PodcastSettings::class,
            Destination.ScrobbleSettings::class,
            Destination.AppearanceSettings::class,
            Destination.About::class
        )

        /** Every route this class reaches; `DestinationCoverageTest` adds up the audits. */
        val ROUTES = LIBRARY_ROUTES + PLAYER_ROUTES + PODCAST_ROUTES + setOf(Destination.Search::class) + SETTINGS_ROUTES
    }
}
