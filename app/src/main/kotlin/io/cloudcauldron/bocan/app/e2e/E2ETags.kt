package io.cloudcauldron.bocan.app.e2e

/**
 * Stable test tags for the end-to-end suite (phase 17), in a `surface.control`
 * namespace. A tag is a fixed string, never derived from a label, a title, or a list
 * position: renaming a control's label must not break a test, and removing the control
 * must. Rows are not tagged per item; the suite finds them by their merged content
 * description (the TalkBack sentence), so accessibility and testability are one thing.
 *
 * Compose test tags live only in the semantics tree; they are not exposed as Android
 * resource ids, so release builds leak nothing to the accessibility tree.
 */
object E2ETags {
    object Onboarding {
        const val GET_STARTED = "onboarding.getStarted"
        const val SKIP = "onboarding.skip"
        const val PAIR_SKIP = "onboarding.pairSkip"
        const val DONE = "onboarding.done"
        const val SYNC_LATER = "onboarding.syncLater"
    }

    object Nav {
        const val LIBRARY = "nav.library"
        const val PODCASTS = "nav.podcasts"
        const val SEARCH = "nav.search"
        const val SETTINGS = "nav.settings"
    }

    object Library {
        const val TAB_ARTISTS = "library.tab.artists"
        const val TAB_ALBUMS = "library.tab.albums"
        const val TAB_SONGS = "library.tab.songs"
        const val TAB_GENRES = "library.tab.genres"
        const val TAB_PLAYLISTS = "library.tab.playlists"
        const val TAB_FOLDERS = "library.tab.folders"
        const val ALBUMS_GRID = "library.albumsGrid"
    }

    object Podcasts {
        const val SHOWS_GRID = "podcasts.showsGrid"
    }

    object MiniPlayer {
        const val OPEN = "miniPlayer.open"
        const val ARTWORK = "miniPlayer.artwork"
        const val PLAY_PAUSE = "miniPlayer.playPause"
    }

    object NowPlaying {
        const val SHUFFLE = "nowPlaying.shuffle"
        const val PREVIOUS = "nowPlaying.previous"
        const val PLAY_PAUSE = "nowPlaying.playPause"
        const val NEXT = "nowPlaying.next"
        const val REPEAT = "nowPlaying.repeat"
        const val SPEED = "nowPlaying.speed"
        const val SKIP_BACK = "nowPlaying.skipBack"
        const val SKIP_FORWARD = "nowPlaying.skipForward"
    }

    /** A control inside a row, for example `album.row.overflow`; the row itself is found by content description. */
    fun row(surface: String, control: String) = "$surface.row.$control"
}
