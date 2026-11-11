package io.cloudcauldron.bocan.app.e2e

/**
 * Stable test tags for the end-to-end suite (phase 17), in a `surface.control`
 * namespace. A tag is a fixed string, never derived from a label, a title, or a list
 * position: renaming a control's label must not break a test, and removing the control
 * must.
 *
 * Repeated controls (every track row, every sort option, every preset chip) share one
 * `surface.row.control` tag, kept in [Rows] or beside their surface. A test picks the one
 * it wants by the row's merged content description (the TalkBack sentence) or, where a row
 * has none, its label; so accessibility and testability are one thing.
 *
 * Every actionable control in the app carries a tag from here; `TagCoverageAudit` (androidTest)
 * fails on any that does not, and `scripts/e2e/audit-test-tags.py` is the cheap source check.
 *
 * Compose test tags live only in the semantics tree; they are not exposed as Android
 * resource ids, so release builds leak nothing to the accessibility tree.
 */
object E2ETags {
    /** Controls shared by many screens. */
    object Common {
        const val BACK = "common.back"
        const val LOCAL_NETWORK_ALLOW = "common.localNetworkAllow"

        /** The drag handle on every bottom sheet (components/SheetDragHandle). */
        const val SHEET_HANDLE = "common.sheetHandle"
    }

    object EmptyState {
        const val ACTION = "emptyState.action"
        const val SECONDARY = "emptyState.secondary"
    }

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
        const val SHUFFLE_ALL = "library.shuffleAll"

        /** The songs tab's A to Z rail; every letter shares this tag and is picked by its text. */
        const val ALPHABET_LETTER = "library.alphabet.row.jump"
    }

    /** The sort affordance on the library tabs; every option shares [OPTION] and is picked by its label. */
    object Sort {
        const val OPEN = "sort.open"
        const val OPTION = "sort.row.option"
    }

    /** Album, artist, genre, and playlist detail headers. */
    object Detail {
        const val PLAY = "detail.play"
        const val SHUFFLE = "detail.shuffle"
        const val SHUFFLE_ALL = "detail.shuffleAll"
    }

    /** The long-press sheet on a track row. */
    object TrackActions {
        const val PLAY_NEXT = "trackActions.playNext"
        const val ADD_TO_QUEUE = "trackActions.addToQueue"
        const val GO_TO_ALBUM = "trackActions.goToAlbum"
        const val GO_TO_ARTIST = "trackActions.goToArtist"
    }

    /** Repeated rows and cells. The row itself is found by its content description (or label), never by index. */
    object Rows {
        const val TRACK = "track.row.play"
        const val ALBUM = "album.row.open"
        const val ARTIST = "artist.row.open"
        const val GENRE = "genre.row.open"
        const val PLAYLIST = "playlist.row.open"
        const val FOLDER = "folder.row.open"
        const val FOLDER_TRACK = "folder.row.play"
        const val SHOW = "show.row.open"
        const val CONTINUE = "continue.row.resume"
        const val EPISODE = "episode.row.play"
        const val EPISODE_MENU = "episode.row.menu"
        const val CHAPTER = "chapter.row.seek"
        const val QUEUE = "queue.row.item"
        const val QUEUE_HANDLE = "queue.row.handle"
        const val LYRIC_LINE = "lyrics.row.seek"
    }

    object Search {
        const val FIELD = "search.field"
        const val RECENT_ROW = "search.recent.row.open"
        const val CLEAR_RECENT = "search.clearRecent"
        const val ALBUM_ROW = "search.album.row.open"
        const val ARTIST_ROW = "search.artist.row.open"
    }

    object Podcasts {
        const val SHOWS_GRID = "podcasts.showsGrid"
    }

    object Show {
        const val DESCRIPTION_TOGGLE = "show.descriptionToggle"
    }

    /** The overflow menu on an episode row. */
    object EpisodeMenu {
        const val MARK_PLAYED = "episodeMenu.markPlayed"
        const val MARK_UNPLAYED = "episodeMenu.markUnplayed"
        const val SHOW_NOTES = "episodeMenu.showNotes"
    }

    object ShowNotes {
        const val INSECURE_OPEN = "showNotes.insecureLink.open"
        const val INSECURE_CANCEL = "showNotes.insecureLink.cancel"
    }

    object MiniPlayer {
        const val OPEN = "miniPlayer.open"
        const val ARTWORK = "miniPlayer.artwork"
        const val PLAY_PAUSE = "miniPlayer.playPause"
    }

    object NowPlaying {
        const val COLLAPSE = "nowPlaying.collapse"
        const val OVERFLOW = "nowPlaying.overflow"
        const val MENU_SLEEP = "nowPlaying.menu.sleep"
        const val MENU_SPEED = "nowPlaying.menu.speed"
        const val MENU_EQUALIZER = "nowPlaying.menu.equalizer"
        const val MENU_LYRICS = "nowPlaying.menu.lyrics"
        const val MENU_DETAILS = "nowPlaying.menu.details"
        const val MENU_CHAPTERS = "nowPlaying.menu.chapters"
        const val MENU_QUEUE = "nowPlaying.menu.queue"
        const val QUICK_SPEED = "nowPlaying.quick.speed"
        const val QUICK_EQUALIZER = "nowPlaying.quick.equalizer"
        const val QUICK_LYRICS = "nowPlaying.quick.lyrics"
        const val QUICK_DETAILS = "nowPlaying.quick.details"
        const val QUICK_QUEUE = "nowPlaying.quick.queue"
        const val GESTURES = "nowPlaying.gestures"
        const val ARTIST = "nowPlaying.artist"
        const val ALBUM = "nowPlaying.album"
        const val SEEK = "nowPlaying.seek"
        const val SHUFFLE = "nowPlaying.shuffle"
        const val PREVIOUS = "nowPlaying.previous"
        const val PLAY_PAUSE = "nowPlaying.playPause"
        const val NEXT = "nowPlaying.next"
        const val REPEAT = "nowPlaying.repeat"
        const val SPEED = "nowPlaying.speed"
        const val SKIP_BACK = "nowPlaying.skipBack"
        const val SKIP_FORWARD = "nowPlaying.skipForward"
    }

    object SleepTimer {
        const val PRESET = "sleepTimer.row.preset"
        const val END_OF_TRACK = "sleepTimer.endOfTrack"
        const val EXTEND = "sleepTimer.extend"
        const val CANCEL = "sleepTimer.cancel"
    }

    object Speed {
        const val SLOWER = "speed.slower"
        const val FASTER = "speed.faster"
        const val RESET = "speed.reset"
    }

    object Queue {
        const val CLEAR = "queue.clear"
        const val CLEAR_CONFIRM = "queue.clear.confirm"
        const val CLEAR_CANCEL = "queue.clear.cancel"
    }

    object Settings {
        const val SYNC = "settings.sync"
        const val PLAYBACK = "settings.playback"
        const val PODCASTS = "settings.podcasts"
        const val SCROBBLING = "settings.scrobbling"
        const val APPEARANCE = "settings.appearance"
        const val ABOUT = "settings.about"
    }

    /** The sync status and settings screen, paired and unpaired. */
    object Sync {
        const val PAIR = "sync.pair"
        const val REMOVE_DEMO = "sync.removeDemo"
        const val SYNC_NOW = "sync.syncNow"
        const val CANCEL = "sync.cancel"
        const val ON_DISCOVERY = "sync.onDiscovery"
        const val PERIODIC = "sync.periodic"
        const val CHARGING_ONLY = "sync.chargingOnly"
        const val UNPAIR = "sync.unpair"
        const val UNPAIR_CONFIRM = "sync.unpair.confirm"
        const val UNPAIR_CANCEL = "sync.unpair.cancel"
        const val REMOVE_MEDIA = "sync.removeMedia"
        const val REMOVE_MEDIA_CONFIRM = "sync.removeMedia.confirm"
        const val REMOVE_MEDIA_CANCEL = "sync.removeMedia.cancel"
    }

    object Pairing {
        const val MAC_ROW = "pairing.mac.row.pair"
        const val CODE = "pairing.code"
        const val DONE = "pairing.done"
        const val START_OVER = "pairing.startOver"
    }

    object PlaybackSettings {
        const val EQUALIZER = "playbackSettings.equalizer"
        const val RESUME_ON_RECONNECT = "playbackSettings.resumeOnReconnect"
    }

    /** Podcast defaults; each chip row shares one tag and a test picks the chip by its label. */
    object PodcastSettings {
        const val SPEED_OPTION = "podcastSettings.speed.row.option"
        const val SKIP_BACK_OPTION = "podcastSettings.skipBack.row.option"
        const val SKIP_FORWARD_OPTION = "podcastSettings.skipForward.row.option"
    }

    object Appearance {
        const val THEME_SYSTEM = "appearance.theme.system"
        const val THEME_LIGHT = "appearance.theme.light"
        const val THEME_DARK = "appearance.theme.dark"
        const val DYNAMIC_COLOR = "appearance.dynamicColor"
        const val PURE_BLACK = "appearance.pureBlack"
    }

    object About {
        const val SITE = "about.site"
        const val SITE_ANDROID = "about.siteAndroid"
        const val TOUR = "about.tour"
    }

    /** Scrobbling. Per-provider controls carry the provider's stable id: see [provider]. */
    object Scrobble {
        const val MASTER = "scrobble.master"
        const val CONNECT = "connect"
        const val DISCONNECT = "disconnect"
        const val FINISH_LAST_FM = "finishLastFm"
        const val ENABLED = "enabled"
        const val DEAD_LETTER_RETRY = "scrobble.deadLetter.row.retry"
        const val DEAD_LETTER_DISCARD = "scrobble.deadLetter.row.discard"
        const val TOKEN_FIELD = "scrobble.token.field"
        const val TOKEN_SAVE = "scrobble.token.save"
        const val TOKEN_CANCEL = "scrobble.token.cancel"

        /** `scrobble.provider.<id>.<control>`, for example `scrobble.provider.lastfm.connect`; ids are core's `ProviderId` constants. */
        fun provider(providerId: String, control: String) = "scrobble.provider.$providerId.$control"
    }

    object Equalizer {
        const val MASTER = "equalizer.master"
        const val PRESET_OPTION = "equalizer.preset.row.select"
        const val PRESET_DELETE = "equalizer.preset.row.delete"
        const val SAVE_PRESET = "equalizer.savePreset"
        const val SAVE_NAME = "equalizer.savePreset.name"
        const val SAVE_CONFIRM = "equalizer.savePreset.confirm"
        const val SAVE_CANCEL = "equalizer.savePreset.cancel"
        const val BAND = "equalizer.band.row.gain"
        const val BASS_BOOST = "equalizer.bassBoost"
        const val REPLAYGAIN_OFF = "equalizer.replayGain.off"
        const val REPLAYGAIN_TRACK = "equalizer.replayGain.track"
        const val REPLAYGAIN_ALBUM = "equalizer.replayGain.album"
        const val PREAMP = "equalizer.preamp"
        const val SKIP_SILENCE = "equalizer.skipSilence"
        const val FADE = "equalizer.fade"
    }

    /** A control inside a row, for example `album.row.overflow`; the row itself is found by content description. */
    fun row(surface: String, control: String) = "$surface.row.$control"
}
