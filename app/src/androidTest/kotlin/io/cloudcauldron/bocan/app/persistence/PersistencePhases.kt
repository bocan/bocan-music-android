package io.cloudcauldron.bocan.app.persistence

import androidx.test.platform.app.InstrumentationRegistry
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.library.LibraryTab
import io.cloudcauldron.bocan.app.settings.PodcastSettingsViewModel
import io.cloudcauldron.bocan.app.surfaces.Action.Look
import io.cloudcauldron.bocan.app.surfaces.Action.Slide
import io.cloudcauldron.bocan.app.surfaces.Action.Submit
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Action.Type
import io.cloudcauldron.bocan.app.surfaces.CrawlContext
import io.cloudcauldron.bocan.app.surfaces.CrawlTest
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Flipped
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.PlayerState
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Selected
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Shows
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Text
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Toggled
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import io.cloudcauldron.bocan.app.surfaces.Step
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.TrackSort
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/*
 * Persistence across a process kill (phase 17, slice 4). A test runs inside the app's own
 * process, so no test can kill the app and see it start again; and the Orchestrator wipes the
 * app's data between tests. So this is two instrumentation runs that run.sh drives with plain
 * `am instrument`, outside Gradle and the Orchestrator:
 *
 *   1. PersistenceWritePhase, on a cleared install: change every setting through the screens,
 *      leave a queue paused on its second track, and record what it left.
 *   2. `am force-stop`: the process dies, the playback service with it.
 *   3. PersistenceReadPhase, in a new process on the same data: every setting reads back the
 *      same, the screens show it, and the queue is restored paused at the same index.
 *
 * Both skip themselves unless run.sh passes `-e e2ePersistence true`, so a stray run (Android
 * Studio, a suite) neither seeds over the read phase's data nor fails the read phase for want of it.
 */

private const val ALBUM = "Long Field"
private const val SECOND = "Long Field 2"
private const val USER_PRESET = "Kept curve"
private const val QUERY = "Light"
private const val RAISED = 0.75f
private const val TOP = 1f

private fun requireRunScript() = assumeTrue(
    "the persistence phases run only from scripts/e2e/run.sh (make end-to-end)",
    InstrumentationRegistry.getArguments().getString("e2ePersistence") == "true"
)

/** Phase 1: on a clean install, change every setting a person can change and leave a paused queue. */
class PersistenceWritePhase : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun changeEverythingThenRecordIt() {
        val before = StoredState.read(graph)
        crawl("persistence, write", writeTable(ctx))
        val after = StoredState.read(graph)
        val unchanged = after.keys.filter { before[it] == after[it] }
        check(unchanged.isEmpty()) { "the write phase left these at their defaults, so their persistence is untested: $unchanged" }
        StoredState.save(app, after)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun onlyFromTheRunScript() = requireRunScript()

        private fun writeTable(c: CrawlContext): List<Step> = library(c) + search(c) + settings(c) + equalizer(c) + queue(c)

        private fun library(c: CrawlContext): List<Step> {
            val year = c.string(R.string.sort_album_year)
            val byArtist = c.string(R.string.sort_song_artist)
            return listOf(
                Step(E2ETags.Library.TAB_ALBUMS, Tap, Visible(E2ETags.Library.ALBUMS_GRID)),
                Step(E2ETags.Sort.OPEN, Tap, Visible(E2ETags.Sort.OPTION, year)),
                Step(
                    E2ETags.Sort.OPTION,
                    Tap,
                    Holds("albums by year") { c.now(c.graph.libraryPreferences.albumSort) == AlbumSort.Year },
                    row = year
                ),
                Step(E2ETags.Library.TAB_SONGS, Tap, Visible(E2ETags.Library.ALPHABET_RAIL)),
                Step(E2ETags.Sort.OPEN, Tap, Visible(E2ETags.Sort.OPTION, byArtist)),
                Step(
                    E2ETags.Sort.OPTION,
                    Tap,
                    Holds("songs by artist") { c.now(c.graph.libraryPreferences.trackSort) == TrackSort.Artist },
                    row = byArtist
                )
            )
        }

        private fun search(c: CrawlContext) = listOf(
            Step(E2ETags.Nav.SEARCH, Tap, Navigated("search", E2ETags.Search.FIELD)),
            Step(E2ETags.Search.FIELD, Type(QUERY), Visible(E2ETags.Search.ALBUM_ROW, "First Light")),
            Step(E2ETags.Search.FIELD, Submit, Holds("'$QUERY' saved") { QUERY in c.now(c.graph.libraryPreferences.recentSearches) })
        )

        private fun settings(c: CrawlContext): List<Step> {
            val settings = Navigated("settings", E2ETags.Settings.SYNC)
            val fastest = c.string(R.string.speed_value, PodcastSettingsViewModel.SPEED_PRESETS.last().toFloat())
            val longest = c.string(R.string.podcast_settings_seconds, PodcastSettingsViewModel.SKIP_PRESETS.last())
            return listOf(
                Step(E2ETags.Nav.SETTINGS, Tap, settings),
                Step(E2ETags.Settings.PLAYBACK, Tap, Navigated("playback settings", E2ETags.PlaybackSettings.RESUME_ON_RECONNECT)),
                Step(E2ETags.PlaybackSettings.RESUME_ON_RECONNECT, Tap, Flipped),
                Step(E2ETags.Common.BACK, Tap, settings),
                Step(E2ETags.Settings.PODCASTS, Tap, Visible(E2ETags.PodcastSettings.SPEED_OPTION, fastest)),
                Step(E2ETags.PodcastSettings.SPEED_OPTION, Tap, Selected(E2ETags.PodcastSettings.SPEED_OPTION, fastest), row = fastest),
                Step(
                    E2ETags.PodcastSettings.SKIP_BACK_OPTION,
                    Tap,
                    Selected(E2ETags.PodcastSettings.SKIP_BACK_OPTION, longest),
                    row = longest
                ),
                Step(
                    E2ETags.PodcastSettings.SKIP_FORWARD_OPTION,
                    Tap,
                    Selected(E2ETags.PodcastSettings.SKIP_FORWARD_OPTION, longest),
                    row = longest
                ),
                Step(E2ETags.Common.BACK, Tap, settings),
                Step(E2ETags.Settings.SCROBBLING, Tap, Navigated("scrobbling", E2ETags.Scrobble.MASTER)),
                Step(E2ETags.Scrobble.MASTER, Tap, Flipped),
                Step(E2ETags.Common.BACK, Tap, settings),
                Step(E2ETags.Settings.APPEARANCE, Tap, Navigated("appearance", E2ETags.Appearance.THEME_SYSTEM)),
                Step(E2ETags.Appearance.THEME_DARK, Tap, Selected(E2ETags.Appearance.THEME_DARK)),
                Step(E2ETags.Appearance.DYNAMIC_COLOR, Tap, Flipped),
                Step(E2ETags.Appearance.PURE_BLACK, Tap, Flipped),
                Step(E2ETags.Common.BACK, Tap, settings)
            )
        }

        private fun equalizer(c: CrawlContext): List<Step> {
            fun eq() = c.now(c.graph.eqPreferences.state)
            val rock = c.string(R.string.eq_preset_rock)
            val firstBand = c.string(R.string.eq_hertz_spoken, FIRST_BAND_HZ)
            return listOf(
                Step(E2ETags.Settings.PLAYBACK, Tap, Navigated("playback settings", E2ETags.PlaybackSettings.EQUALIZER)),
                Step(E2ETags.PlaybackSettings.EQUALIZER, Tap, Navigated("equalizer", E2ETags.Equalizer.MASTER)),
                Step(E2ETags.Equalizer.MASTER, Tap, Flipped),
                Step(E2ETags.Equalizer.PRESET_OPTION, Tap, Selected(E2ETags.Equalizer.PRESET_OPTION, rock), row = rock),
                Step(
                    E2ETags.Equalizer.BAND,
                    Slide(RAISED),
                    Holds("the first band raised") { eq().bandGainsDb.first() > 0.0 },
                    row = firstBand
                ),
                Step(E2ETags.Equalizer.BASS_BOOST, Slide(TOP), Holds("bass boost raised") { eq().bassBoostDb > 0.0 }),
                Step(E2ETags.Equalizer.REPLAYGAIN_TRACK, Tap, Selected(E2ETags.Equalizer.REPLAYGAIN_TRACK)),
                Step(E2ETags.Equalizer.PREAMP, Slide(TOP), Holds("preamp raised") { eq().preampDb > 0.0 }),
                Step(E2ETags.Equalizer.SKIP_SILENCE, Tap, Flipped),
                Step(E2ETags.Equalizer.FADE, Slide(TOP), Holds("a fade set") { eq().fadeSeconds > 0 }),
                Step(E2ETags.Equalizer.SAVE_PRESET, Tap, Visible(E2ETags.Equalizer.SAVE_NAME)),
                Step(E2ETags.Equalizer.SAVE_NAME, Type(USER_PRESET), Text(E2ETags.Equalizer.SAVE_NAME, USER_PRESET)),
                Step(E2ETags.Equalizer.SAVE_CONFIRM, Tap, Visible(E2ETags.Equalizer.PRESET_OPTION, USER_PRESET)),
                Step(E2ETags.Common.BACK, Tap, Navigated("playback settings", E2ETags.PlaybackSettings.EQUALIZER))
            )
        }

        private fun queue(c: CrawlContext) = listOf(
            Step(E2ETags.Nav.LIBRARY, Tap, Navigated("library", E2ETags.Library.TAB_ALBUMS)),
            Step(E2ETags.Library.TAB_ALBUMS, Tap, Visible(E2ETags.Library.ALBUMS_GRID)),
            Step(E2ETags.Rows.ALBUM, Tap, Navigated("album", E2ETags.Detail.PLAY), row = ALBUM),
            Step(E2ETags.Detail.PLAY, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("$ALBUM 1"))),
            Step(E2ETags.MiniPlayer.PLAY_PAUSE, Tap, PlayerState(playing = false)),
            Step(E2ETags.MiniPlayer.OPEN, Tap, Navigated("now playing", E2ETags.NowPlaying.SEEK)),
            Step(E2ETags.NowPlaying.NEXT, Tap, PlayerState(playing = false, mediaIdPrefix = c.track(SECOND))),
            Step(
                E2ETags.NowPlaying.NEXT,
                Look,
                Holds("the queue written to disk at index 1") {
                    val saved = runBlocking { c.graph.queuePersistence.load() }
                    saved != null && saved.index == 1 && saved.mediaIds.getOrNull(1) == c.track(SECOND)
                }
            ),
            // Last, so nothing after it moves the library's remembered tab back to Albums.
            Step(E2ETags.NowPlaying.COLLAPSE, Tap, Navigated("album", E2ETags.Detail.PLAY)),
            Step(E2ETags.Common.BACK, Tap, Visible(E2ETags.Library.ALBUMS_GRID)),
            Step(
                E2ETags.Library.TAB_GENRES,
                Tap,
                Holds("the library remembers Genres") { c.now(c.graph.libraryPreferences.lastTab) == LibraryTab.Genres }
            )
        )

        private const val FIRST_BAND_HZ = 31
    }
}

/** Phase 3, after the kill: a new process on phase 1's data. Nothing is seeded; everything must come back. */
class PersistenceReadPhase : CrawlTest(FixtureProfile.PairedLibrary, keepInstall = true) {
    @Test
    fun everythingComesBackAfterTheKill() {
        val expected = checkNotNull(StoredState.load(app)) {
            "no record from the write phase: run both phases through scripts/e2e/run.sh (make end-to-end)"
        }
        val actual = StoredState.read(graph)
        val lost = expected.keys.filter { expected[it] != actual[it] }.map { "$it: wrote ${expected[it]}, read ${actual[it]}" }
        check(lost.isEmpty()) { "settings lost across the kill: $lost" }

        awaitPlayer("the queue restored paused on '$SECOND' at index 1") {
            it.current?.mediaId == trackMediaId(SECOND) && it.queueIndex == 1 && !it.isPlaying
        }
        crawl("persistence, read", readTable(ctx, expected))
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun onlyFromTheRunScript() = requireRunScript()

        /** The screens show what storage holds: the restored track, and a setting on each settings screen. */
        private fun readTable(c: CrawlContext, expected: Map<String, String>): List<Step> {
            val resume = expected.getValue("playback.resumeOnReconnect").toBooleanStrict()
            return listOf(
                Step(E2ETags.MiniPlayer.OPEN, Look, Shows(SECOND)),
                Step(E2ETags.Nav.SETTINGS, Tap, Navigated("settings", E2ETags.Settings.SYNC)),
                Step(E2ETags.Settings.APPEARANCE, Tap, Selected(E2ETags.Appearance.THEME_DARK)),
                Step(E2ETags.Appearance.PURE_BLACK, Look, Toggled(E2ETags.Appearance.PURE_BLACK, on = true)),
                Step(E2ETags.Common.BACK, Tap, Navigated("settings", E2ETags.Settings.SYNC)),
                Step(E2ETags.Settings.PLAYBACK, Tap, Toggled(E2ETags.PlaybackSettings.RESUME_ON_RECONNECT, on = resume)),
                Step(E2ETags.PlaybackSettings.EQUALIZER, Tap, Toggled(E2ETags.Equalizer.MASTER, on = true)),
                Step(E2ETags.Equalizer.PRESET_OPTION, Look, Visible(E2ETags.Equalizer.PRESET_OPTION, USER_PRESET)),
                Step(E2ETags.Equalizer.REPLAYGAIN_TRACK, Look, Selected(E2ETags.Equalizer.REPLAYGAIN_TRACK)),
                Step(E2ETags.Equalizer.SKIP_SILENCE, Look, Toggled(E2ETags.Equalizer.SKIP_SILENCE, on = true))
            )
        }
    }
}
