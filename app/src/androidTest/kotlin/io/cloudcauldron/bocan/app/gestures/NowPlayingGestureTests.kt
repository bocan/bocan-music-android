package io.cloudcauldron.bocan.app.gestures

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import io.cloudcauldron.bocan.app.E2ETest
import io.cloudcauldron.bocan.app.LibraryScreen
import io.cloudcauldron.bocan.app.MiniPlayer
import io.cloudcauldron.bocan.app.R
import io.cloudcauldron.bocan.app.e2e.E2ETags
import io.cloudcauldron.bocan.app.fixtures.FixtureProfile
import io.cloudcauldron.bocan.app.player.PlayerGestureThresholds
import io.cloudcauldron.bocan.playback.queue.RepeatMode
import kotlin.math.abs
import kotlin.math.roundToLong
import org.junit.After
import org.junit.Test

/**
 * The Now Playing gesture surface (phase 13) under a real finger. Every distance and speed is
 * derived from [PlayerGestureThresholds] and the surface's measured size, never typed in: a
 * commit drag travels past the commit distance slower than the fling velocity (so only the
 * distance can commit it), a fling travels short of the distance faster than the velocity (so
 * only the speed can), and a snap-back stays under both.
 *
 * Playback is paused before each gesture, so a 4 second fixture track never ends under a test.
 * The motion setting ([MotionSetting]) is set explicitly in every test, because the nightly's
 * emulator runs with animations off and a developer's emulator usually has them on.
 */
class NowPlayingGestureTests : E2ETest(FixtureProfile.PairedLibrary) {
    private val motion = MotionSetting()

    @After
    fun restoreMotion() = motion.restore()

    @Test
    fun swipeLeftPastTheCommitDistanceAdvances() {
        open(at = 2)
        drag(dx = -horizontalCommitPx() * PAST)
        awaitCurrent(3, "a slow left swipe past the commit distance")
    }

    @Test
    fun swipeRightPastTheCommitDistanceGoesBack() {
        open(at = 2)
        drag(dx = horizontalCommitPx() * PAST)
        awaitCurrent(1, "a slow right swipe past the commit distance")
    }

    @Test
    fun aShortFastFlingCommits() {
        open(at = 2)
        fling(dx = -horizontalCommitPx() * SHORT)
        awaitCurrent(3, "a left fling short of the commit distance")
        fling(dx = horizontalCommitPx() * SHORT)
        awaitCurrent(2, "a right fling short of the commit distance")
    }

    @Test
    fun aDragShortOfBothThresholdsSnapsBack() {
        open(at = 2)
        val rest = titleLeft(2)
        drag(dx = -horizontalCommitPx() * SHORT)
        drag(dx = horizontalCommitPx() * SHORT)
        drag(dy = -verticalCommitPx() * SHORT)
        drag(dy = verticalCommitPx() * SHORT)
        composeRule.waitForIdle()
        assertCurrent(2, "four short slow drags")
        check(abs(titleLeft(2) - rest) < 1f) { "the card did not settle back: left ${titleLeft(2)}, at rest $rest" }
        check(sheetHandles() == 0) { "a short upward drag opened a sheet" }
        ui.awaitTag(E2ETags.NowPlaying.SEEK)
        // And the surface still answers: the next real swipe commits.
        drag(dx = -horizontalCommitPx() * PAST)
        awaitCurrent(3, "a full swipe after the snap-backs")
    }

    @Test
    fun swipeUpOpensSongDetails() {
        open(at = 2)
        drag(dy = -verticalCommitPx() * PAST)
        awaitState("the song details sheet open") { sheetHandles() == 1 }
        assertCurrent(2, "the upward swipe")
    }

    @Test
    fun swipeDownDismissesThePlayer() {
        open(at = 2)
        drag(dy = verticalCommitPx() * PAST)
        ui.awaitTag(E2ETags.MiniPlayer.OPEN)
        awaitState("Now Playing gone") { tagged(E2ETags.NowPlaying.SEEK) == 0 }
        assertCurrent(2, "the dismiss")
        check(!player.isPlaying) { "the dismiss started playback" }
    }

    @Test
    fun atTheEndsOfTheQueueASwipeSettlesBack() {
        open(at = TRACKS)
        drag(dx = -horizontalCommitPx() * PAST)
        composeRule.waitForIdle()
        assertCurrent(TRACKS, "a left swipe on the last track")
        drag(dx = horizontalCommitPx() * PAST)
        awaitCurrent(TRACKS - 1, "a right swipe back from the last track")
        repeat(TRACKS - 2) { drag(dx = horizontalCommitPx() * PAST) }
        awaitCurrent(1, "right swipes back to the first track")
        drag(dx = horizontalCommitPx() * PAST)
        composeRule.waitForIdle()
        assertCurrent(1, "a right swipe on the first track")
    }

    /** Under repeat all the Next and Previous buttons wrap at the ends, so the swipes must too. */
    @Test
    fun underRepeatAllTheEndsWrapLikeTheButtons() {
        open(at = TRACKS)
        repeat(2) { ui.tap(E2ETags.NowPlaying.REPEAT) }
        awaitPlayer("repeat one") { it.repeatMode == RepeatMode.One }
        drag(dx = -horizontalCommitPx() * PAST)
        composeRule.waitForIdle()
        assertCurrent(TRACKS, "a left swipe on the last track under repeat one")
        repeat(2) { ui.tap(E2ETags.NowPlaying.REPEAT) }
        awaitPlayer("repeat all") { it.repeatMode == RepeatMode.All }
        drag(dx = -horizontalCommitPx() * PAST)
        awaitCurrent(1, "a left swipe on the last track under repeat all")
        drag(dx = horizontalCommitPx() * PAST)
        awaitCurrent(TRACKS, "a right swipe on the first track under repeat all")
    }

    /** Two flings in quick succession, the second while the first is still settling, skip two tracks. */
    @Test
    fun twoQuickFlingsSkipTwoTracks() {
        open(at = 2)
        val surface = surface()
        val commit = horizontalCommitPx()
        surface.performTouchInput {
            repeat(2) {
                // A person's second flick lands while the first card is still sliding home.
                if (it > 0) advanceEventTime(BETWEEN_FLINGS_MS)
                swipeWithVelocity(
                    start = Offset(centerX + commit * SHORT / 2, centerY),
                    end = Offset(centerX - commit * SHORT / 2, centerY),
                    endVelocity = PlayerGestureThresholds.FLING_VELOCITY * FAST
                )
            }
        }
        awaitCurrent(4, "two quick left flings from track 2")
    }

    @Test
    fun theFourTalkBackActionsMatchTheGestures() {
        open(at = 2)
        accessibilityAction(R.string.gesture_next_track)
        awaitCurrent(3, "the next track action")
        accessibilityAction(R.string.gesture_previous_track)
        awaitCurrent(2, "the previous track action")
        accessibilityAction(R.string.gesture_song_details)
        awaitState("the song details sheet open") { sheetHandles() == 1 }
        ui.awaitTag(E2ETags.Common.SHEET_HANDLE)
        androidx.test.espresso.Espresso.pressBack()
        awaitState("the song details sheet closed") { sheetHandles() == 0 }
        accessibilityAction(R.string.gesture_close_player)
        ui.awaitTag(E2ETags.MiniPlayer.OPEN)
    }

    /** With animations on, the card follows the finger and the whole screen follows a downward drag. */
    @Test
    fun withMotionTheBlockFollowsTheFinger() {
        open(at = 2, reducedMotion = false)
        val dx = -horizontalCommitPx() * SHORT
        val cardRest = titleLeft(2)
        hold(dx = dx) {
            val moved = titleLeft(2) - cardRest
            check(moved < dx / 2) { "the card moved $moved px under a $dx px drag" }
        }
        val dy = verticalCommitPx() * SHORT
        val screenRest = seekTop()
        hold(dy = dy) {
            val moved = seekTop() - screenRest
            check(moved > dy / 2) { "the screen moved $moved px under a $dy px drag" }
        }
        composeRule.waitForIdle()
        check(abs(titleLeft(2) - cardRest) < 1f && abs(seekTop() - screenRest) < 1f) { "the block did not settle back" }
    }

    /** Reduced motion (phase 13, Motion.kt): nothing slides, the content swaps, the dismiss pops. */
    @Test
    fun underReducedMotionNothingSlidesAndTheContentSwaps() {
        open(at = 2, reducedMotion = true)
        val cardRest = titleLeft(2)
        hold(dx = -horizontalCommitPx() * SHORT) {
            check(abs(titleLeft(2) - cardRest) < 1f) { "the card slid under reduced motion: ${titleLeft(2)} from $cardRest" }
        }
        val screenRest = seekTop()
        hold(dy = verticalCommitPx() * SHORT) {
            check(abs(seekTop() - screenRest) < 1f) { "the screen slid under reduced motion: ${seekTop()} from $screenRest" }
        }
        drag(dx = -horizontalCommitPx() * PAST)
        awaitCurrent(3, "a left swipe under reduced motion")
        drag(dy = verticalCommitPx() * PAST)
        ui.awaitTag(E2ETags.MiniPlayer.OPEN)
    }

    // --- Setup -------------------------------------------------------------------------------

    /** Play [ALBUM] from track [at], pause, set the motion, and open Now Playing. */
    private fun open(at: Int, reducedMotion: Boolean = false) {
        motion.set(reduced = reducedMotion)
        val library = LibraryScreen(ui)
        library.albumsTab()
        library.openAlbum(ALBUM)
        // The later tracks of a six-track album sit below the fold: bring the row on screen first.
        ui.awaitRow(E2ETags.Rows.TRACK, title(at)).performScrollTo().performClick()
        awaitPlayer("'${title(at)}' playing") { it.current?.mediaId == mediaId(at) && it.isPlaying }
        val miniPlayer = MiniPlayer(ui)
        miniPlayer.playPause()
        awaitPlayer("paused") { !it.isPlaying }
        miniPlayer.open()
        surface()
    }

    private fun title(index: Int) = "$ALBUM $index"

    private fun mediaId(index: Int) = trackMediaId(title(index))

    // --- Gestures ------------------------------------------------------------------------------

    private fun surface(): SemanticsNodeInteraction = ui.awaitTag(E2ETags.NowPlaying.GESTURES)

    /** The horizontal commit distance: a fraction of the surface's width, as the gesture code measures it. */
    private fun horizontalCommitPx(): Float = surface().fetchSemanticsNode().size.width * PlayerGestureThresholds.COMMIT_FRACTION

    /** The vertical commit distance: a fixed size in dp, converted with the device's density. */
    private fun verticalCommitPx(): Float = with(composeRule.density) { PlayerGestureThresholds.VERTICAL_COMMIT_DP.dp.toPx() }

    /** A drag at a third of the fling velocity, centred on the surface, released where it ends. */
    private fun drag(dx: Float = 0f, dy: Float = 0f) {
        val distance = kotlin.math.hypot(dx, dy)
        val millis = (distance / (PlayerGestureThresholds.FLING_VELOCITY * SLOW) * MILLIS_PER_SECOND).roundToLong()
        surface().performTouchInput {
            val half = Offset(dx / 2, dy / 2)
            swipe(start = center - half, end = center + half, durationMillis = millis)
        }
    }

    /** A swipe that ends at twice the fling velocity. */
    private fun fling(dx: Float) {
        surface().performTouchInput {
            swipeWithVelocity(
                start = Offset(centerX - dx / 2, centerY),
                end = Offset(centerX + dx / 2, centerY),
                endVelocity = PlayerGestureThresholds.FLING_VELOCITY * FAST
            )
        }
    }

    /** Press, drag slowly by ([dx], [dy]), run [whileHeld] with the finger still down, then lift. */
    private fun hold(dx: Float = 0f, dy: Float = 0f, whileHeld: () -> Unit) {
        surface().performTouchInput {
            down(center)
            repeat(HOLD_STEPS) { moveBy(Offset(dx / HOLD_STEPS, dy / HOLD_STEPS), delayMillis = HOLD_STEP_MS) }
        }
        composeRule.waitForIdle()
        whileHeld()
        surface().performTouchInput { up() }
    }

    private fun accessibilityAction(@StringRes label: Int) {
        val name = app.getString(label)
        val action = surface().fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == name }
        composeRule.runOnUiThread { action.action() }
    }

    // --- Reading the screen ----------------------------------------------------------------------

    private fun titleLeft(index: Int): Float = composeRule
        .onNode(hasText(title(index)) and hasAnyAncestor(hasTestTag(E2ETags.NowPlaying.GESTURES)), useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot.left

    private fun seekTop(): Float = ui.awaitTag(E2ETags.NowPlaying.SEEK).fetchSemanticsNode().boundsInRoot.top

    private fun tagged(tag: String): Int =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun sheetHandles(): Int = tagged(E2ETags.Common.SHEET_HANDLE)

    private fun awaitCurrent(index: Int, after: String) =
        awaitPlayer("'${title(index)}' current after $after") { it.current?.mediaId == mediaId(index) }

    private fun assertCurrent(index: Int, after: String) =
        check(player.current?.mediaId == mediaId(index)) { "expected '${title(index)}' after $after, the player is on ${player.current}" }

    private companion object {
        // Six tracks over two discs: room for two skips from track 2 and for both ends.
        const val ALBUM = "Long Field"
        const val TRACKS = 6

        /** Multiples of a threshold: past it, and well short of it. */
        const val PAST = 1.5f
        const val SHORT = 0.5f

        /** Multiples of the fling velocity: a slow drag, and a fling. */
        const val SLOW = 1f / 3f
        const val FAST = 2f

        const val MILLIS_PER_SECOND = 1_000f
        const val HOLD_STEPS = 20
        const val HOLD_STEP_MS = 40L
        const val BETWEEN_FLINGS_MS = 120L
    }
}
