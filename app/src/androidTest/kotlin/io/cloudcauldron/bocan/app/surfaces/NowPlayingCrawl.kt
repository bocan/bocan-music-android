package io.cloudcauldron.bocan.app.surfaces

import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.surfaces.Action.Back
import io.cloudcauldron.bocan.app.surfaces.Action.Reorder
import io.cloudcauldron.bocan.app.surfaces.Action.Slide
import io.cloudcauldron.bocan.app.surfaces.Action.Swipe
import io.cloudcauldron.bocan.app.surfaces.Action.Tap
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Gone
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Holds
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Navigated
import io.cloudcauldron.bocan.app.surfaces.Postcondition.PlayerState
import io.cloudcauldron.bocan.app.surfaces.Postcondition.Visible
import io.cloudcauldron.bocan.playback.SleepTimerState
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import kotlin.math.abs
import org.junit.Test

/**
 * Now Playing for a track: every transport control (paused, so 4 second fixture tracks never
 * advance under a step), seek, shuffle, the repeat cycle, the overflow menu and the quick
 * actions, speed, the sleep timer, lyrics with a line tap, song details, artist and album
 * links, and the queue with reorder, swipe-to-remove, and a confirmed clear (last: it empties
 * the player).
 */
class NowPlayingCrawl : CrawlTest(FixtureProfile.PairedLibrary) {
    @Test
    fun crawl() {
        crawl("now playing", table(ctx))
        finishCrawl()
    }

    companion object {
        private val PLAYER = Navigated("now playing", E2ETags.NowPlaying.SEEK)
        private val SPEED_OPEN = Visible(E2ETags.Speed.FASTER)
        private const val SPEED_STEP = 0.1f
        private const val SPEED_EPSILON = 0.01f

        // From the fixture's lyrics for First Light 1 (lyrics/9001000001.lrc).
        private const val LYRIC_LINE = "Third line."
        private const val LYRIC_LINE_MS = 2_500L
        private const val SEEK_TOLERANCE_MS = 400L
        private const val FIFTEEN_MINUTES_MS = 15 * 60_000L

        fun table(c: CrawlContext): List<Step> = open(c) + transport(c) + gestures() + sheets(c) + lyricsAndLinks() + queue(c)

        private fun open(c: CrawlContext) = listOf(
            Step(E2ETags.Library.TAB_ALBUMS, Tap, Visible(E2ETags.Library.ALBUMS_GRID)),
            Step(E2ETags.Rows.ALBUM, Tap, Navigated("album", E2ETags.Detail.PLAY), row = "First Light"),
            Step(E2ETags.Rows.TRACK, Tap, PlayerState(playing = true, mediaIdPrefix = c.track("First Light 1")), row = "First Light 1"),
            Step(E2ETags.MiniPlayer.OPEN, Tap, PLAYER)
        )

        private fun transport(c: CrawlContext) = listOf(
            Step(E2ETags.NowPlaying.PLAY_PAUSE, Tap, PlayerState(playing = false)),
            Step(E2ETags.NowPlaying.NEXT, Tap, PlayerState(mediaIdPrefix = c.track("First Light 2"))),
            Step(E2ETags.NowPlaying.PREVIOUS, Tap, PlayerState(mediaIdPrefix = c.track("First Light 1"))),
            Step(E2ETags.NowPlaying.SHUFFLE, Tap, PlayerState(what = "shuffle on") { it.shuffleActive }),
            Step(E2ETags.NowPlaying.SHUFFLE, Tap, PlayerState(what = "shuffle off") { !it.shuffleActive }),
            Step(E2ETags.NowPlaying.REPEAT, Tap, PlayerState(what = "repeat all") { it.repeatMode == RepeatMode.All }),
            Step(E2ETags.NowPlaying.REPEAT, Tap, PlayerState(what = "repeat one") { it.repeatMode == RepeatMode.One }),
            Step(E2ETags.NowPlaying.REPEAT, Tap, PlayerState(what = "repeat off") { it.repeatMode == RepeatMode.Off }),
            Step(
                E2ETags.NowPlaying.SEEK,
                Slide(HALF),
                PlayerState(what = "halfway through the track") { abs(it.positionMs - it.durationMs / 2) <= SEEK_TOLERANCE_MS }
            )
        )

        // A plain full-length swipe each way; the thresholds, flings, snap-backs, ends, and reduced
        // motion are gestures/NowPlayingGestureTests. Positions, not titles: the shuffle steps above
        // leave the upcoming order random.
        private fun gestures() = listOf(
            Step(E2ETags.NowPlaying.GESTURES, Swipe(Direction.Left), PlayerState(what = "on the second item") { it.queueIndex == 1 }),
            Step(E2ETags.NowPlaying.GESTURES, Swipe(Direction.Right), PlayerState(what = "back on the first item") { it.queueIndex == 0 }),
            Step(E2ETags.NowPlaying.GESTURES, Swipe(Direction.Up), Visible(E2ETags.Common.SHEET_HANDLE)),
            Step(CrawlTags.SYSTEM_BACK, Back, Gone(E2ETags.Common.SHEET_HANDLE)),
            Step(E2ETags.NowPlaying.GESTURES, Swipe(Direction.Down), Visible(E2ETags.MiniPlayer.OPEN)),
            Step(E2ETags.MiniPlayer.OPEN, Tap, PLAYER)
        )

        private fun sheets(c: CrawlContext): List<Step> {
            fun sleep() = c.graph.sleepTimer.state.value
            val fifteen = c.plural(R.plurals.sleep_timer_minutes, FIFTEEN, FIFTEEN)
            return listOf(
                Step(E2ETags.NowPlaying.QUICK_SPEED, Tap, SPEED_OPEN),
                Step(E2ETags.Speed.FASTER, Tap, PlayerState(what = "speed 1.1") { abs(it.speed - (1f + SPEED_STEP)) < SPEED_EPSILON }),
                Step(E2ETags.Speed.SLOWER, Tap, PlayerState(what = "speed 1.0") { abs(it.speed - 1f) < SPEED_EPSILON }),
                Step(E2ETags.Speed.FASTER, Tap, PlayerState(what = "speed above 1.0") { it.speed > 1f + SPEED_EPSILON }),
                Step(E2ETags.Speed.RESET, Tap, PlayerState(what = "speed reset to 1.0") { abs(it.speed - 1f) < SPEED_EPSILON }),
                Step(CrawlTags.SYSTEM_BACK, Back, Gone(E2ETags.Speed.FASTER)),
                Step(E2ETags.NowPlaying.OVERFLOW, Tap, Visible(E2ETags.NowPlaying.MENU_SLEEP)),
                Step(E2ETags.NowPlaying.MENU_SLEEP, Tap, Visible(E2ETags.SleepTimer.END_OF_TRACK)),
                Step(
                    E2ETags.SleepTimer.PRESET,
                    Tap,
                    Holds("a 15 minute timer counting") { sleep() is SleepTimerState.Counting },
                    row = fifteen
                ),
                Step(
                    E2ETags.SleepTimer.EXTEND,
                    Tap,
                    Holds("the timer extended past 15 minutes") {
                        (sleep() as? SleepTimerState.Counting)?.let { it.remainingMs > FIFTEEN_MINUTES_MS } == true
                    }
                ),
                Step(E2ETags.SleepTimer.CANCEL, Tap, Holds("the timer cancelled") { sleep() == SleepTimerState.Idle }),
                Step(
                    E2ETags.SleepTimer.END_OF_TRACK,
                    Tap,
                    Holds("waiting for the track to end") { sleep() == SleepTimerState.WaitingForTrackEnd }
                ),
                Step(E2ETags.SleepTimer.CANCEL, Tap, Visible(E2ETags.SleepTimer.END_OF_TRACK)),
                Step(CrawlTags.SYSTEM_BACK, Back, Gone(E2ETags.SleepTimer.END_OF_TRACK)),
                Step(E2ETags.NowPlaying.OVERFLOW, Tap, Visible(E2ETags.NowPlaying.MENU_SPEED)),
                Step(E2ETags.NowPlaying.MENU_SPEED, Tap, SPEED_OPEN),
                Step(CrawlTags.SYSTEM_BACK, Back, Gone(E2ETags.Speed.FASTER)),
                Step(E2ETags.NowPlaying.OVERFLOW, Tap, Visible(E2ETags.NowPlaying.MENU_DETAILS)),
                Step(E2ETags.NowPlaying.MENU_DETAILS, Tap, Visible(E2ETags.Common.SHEET_HANDLE)),
                Step(CrawlTags.SYSTEM_BACK, Back, Gone(E2ETags.Common.SHEET_HANDLE)),
                Step(E2ETags.NowPlaying.QUICK_DETAILS, Tap, Visible(E2ETags.Common.SHEET_HANDLE)),
                Step(CrawlTags.SYSTEM_BACK, Back, Gone(E2ETags.Common.SHEET_HANDLE)),
                Step(E2ETags.NowPlaying.OVERFLOW, Tap, Visible(E2ETags.NowPlaying.MENU_QUEUE)),
                Step(E2ETags.NowPlaying.MENU_QUEUE, Tap, Visible(E2ETags.Queue.CLEAR)),
                Step(CrawlTags.SYSTEM_BACK, Back, Gone(E2ETags.Queue.CLEAR)),
                Step(E2ETags.NowPlaying.OVERFLOW, Tap, Visible(E2ETags.NowPlaying.MENU_EQUALIZER)),
                Step(E2ETags.NowPlaying.MENU_EQUALIZER, Tap, Navigated("equalizer", E2ETags.Equalizer.MASTER)),
                Step(E2ETags.Common.BACK, Tap, PLAYER),
                Step(E2ETags.NowPlaying.QUICK_EQUALIZER, Tap, Navigated("equalizer", E2ETags.Equalizer.MASTER)),
                Step(E2ETags.Common.BACK, Tap, PLAYER)
            )
        }

        private fun lyricsAndLinks() = listOf(
            Step(E2ETags.NowPlaying.OVERFLOW, Tap, Visible(E2ETags.NowPlaying.MENU_LYRICS)),
            Step(E2ETags.NowPlaying.MENU_LYRICS, Tap, Visible(E2ETags.Rows.LYRIC_LINE, LYRIC_LINE)),
            Step(E2ETags.NowPlaying.OVERFLOW, Tap, Visible(E2ETags.NowPlaying.MENU_LYRICS)),
            Step(E2ETags.NowPlaying.MENU_LYRICS, Tap, Gone(E2ETags.Rows.LYRIC_LINE)),
            Step(E2ETags.NowPlaying.QUICK_LYRICS, Tap, Visible(E2ETags.Rows.LYRIC_LINE, LYRIC_LINE)),
            Step(
                E2ETags.Rows.LYRIC_LINE,
                Tap,
                PlayerState(what = "at the line '$LYRIC_LINE'") { abs(it.positionMs - LYRIC_LINE_MS) <= SEEK_TOLERANCE_MS },
                row = LYRIC_LINE
            ),
            Step(E2ETags.NowPlaying.QUICK_LYRICS, Tap, Gone(E2ETags.Rows.LYRIC_LINE)),
            Step(E2ETags.NowPlaying.ARTIST, Tap, Navigated("artist", E2ETags.Detail.SHUFFLE_ALL)),
            Step(E2ETags.Common.BACK, Tap, PLAYER),
            Step(E2ETags.NowPlaying.ALBUM, Tap, Navigated("album", E2ETags.Detail.PLAY)),
            Step(E2ETags.Common.BACK, Tap, PLAYER),
            Step(E2ETags.NowPlaying.COLLAPSE, Tap, Visible(E2ETags.MiniPlayer.OPEN)),
            Step(E2ETags.MiniPlayer.OPEN, Tap, PLAYER)
        )

        private fun queue(c: CrawlContext): List<Step> {
            val (one, two, three) = listOf("First Light 1", "First Light 2", "First Light 3").map(c::track)
            return listOf(
                // The shuffle steps left 2 and 3 in a random order, and moving the last row down is a
                // no-op that the reorder check would pass. Replay the album so the queue is 1, 2, 3.
                Step(E2ETags.NowPlaying.ALBUM, Tap, Navigated("album", E2ETags.Detail.PLAY)),
                Step(
                    E2ETags.Detail.PLAY,
                    Tap,
                    PlayerState(what = "the album queued in order") { state -> state.queue.map { it.mediaId } == listOf(one, two, three) }
                ),
                Step(E2ETags.MiniPlayer.PLAY_PAUSE, Tap, PlayerState(playing = false)),
                Step(E2ETags.MiniPlayer.OPEN, Tap, PLAYER),
                Step(E2ETags.NowPlaying.QUICK_QUEUE, Tap, Visible(E2ETags.Rows.QUEUE, "First Light 3")),
                Step(
                    E2ETags.Rows.QUEUE_HANDLE,
                    Reorder(1),
                    PlayerState(what = "queue reordered to 1, 3, 2") { state -> state.queue.map { it.mediaId } == listOf(one, three, two) },
                    row = "First Light 2"
                ),
                Step(
                    E2ETags.Rows.QUEUE,
                    Swipe(Direction.Left),
                    PlayerState(what = "'First Light 3' removed") { state -> state.queue.map { it.mediaId } == listOf(one, two) },
                    row = "First Light 3"
                ),
                Step(E2ETags.Queue.CLEAR, Tap, Visible(E2ETags.Queue.CLEAR_CONFIRM)),
                Step(E2ETags.Queue.CLEAR_CANCEL, Tap, Gone(E2ETags.Queue.CLEAR_CONFIRM)),
                Step(E2ETags.Queue.CLEAR, Tap, Visible(E2ETags.Queue.CLEAR_CONFIRM)),
                Step(E2ETags.Queue.CLEAR_CONFIRM, Tap, PlayerState(what = "queue empty") { it.queue.isEmpty() })
            )
        }

        private const val HALF = 0.5f
        private const val FIFTEEN = 15
    }
}
