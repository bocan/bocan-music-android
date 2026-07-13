package io.cloudcauldron.bocan.app.player

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** The pure anchor-resolution logic behind the Now Playing gestures. */
class PlayerGesturesTests {
    private val width = 1000f
    private val slowFling = 0f

    private val commitDistance = 300f

    @Test
    fun `a short slow horizontal drag settles back`() {
        val target = resolveHorizontalTarget(offsetPx = 100f, widthPx = width, velocityPx = slowFling, hasPrevious = true, hasNext = true)
        assertEquals(HorizontalAnchor.Settled, target)
    }

    @Test
    fun `dragging left (right to left) past the threshold advances to next`() {
        val target = resolveHorizontalTarget(offsetPx = -450f, widthPx = width, velocityPx = slowFling, hasPrevious = true, hasNext = true)
        assertEquals(HorizontalAnchor.Next, target)
    }

    @Test
    fun `dragging right past the threshold goes to previous`() {
        val target = resolveHorizontalTarget(offsetPx = 450f, widthPx = width, velocityPx = slowFling, hasPrevious = true, hasNext = true)
        assertEquals(HorizontalAnchor.Previous, target)
    }

    @Test
    fun `a fast leftward fling advances even with a short drag`() {
        val target = resolveHorizontalTarget(offsetPx = -60f, widthPx = width, velocityPx = -2_000f, hasPrevious = true, hasNext = true)
        assertEquals(HorizontalAnchor.Next, target)
    }

    @Test
    fun `at the end of the queue a leftward drag clamps to settled`() {
        val target = resolveHorizontalTarget(offsetPx = -700f, widthPx = width, velocityPx = -3_000f, hasPrevious = true, hasNext = false)
        assertEquals(HorizontalAnchor.Settled, target)
    }

    @Test
    fun `at the start of the queue a rightward drag clamps to settled`() {
        val target = resolveHorizontalTarget(offsetPx = 700f, widthPx = width, velocityPx = slowFling, hasPrevious = false, hasNext = true)
        assertEquals(HorizontalAnchor.Settled, target)
    }

    @Test
    fun `dragging down past the threshold dismisses`() {
        assertEquals(
            VerticalAnchor.Dismiss,
            resolveVerticalTarget(offsetPx = 400f, commitDistancePx = commitDistance, velocityPx = slowFling)
        )
    }

    @Test
    fun `dragging up past the threshold opens details`() {
        assertEquals(
            VerticalAnchor.Details,
            resolveVerticalTarget(offsetPx = -400f, commitDistancePx = commitDistance, velocityPx = slowFling)
        )
    }

    @Test
    fun `a short vertical drag settles back`() {
        assertEquals(
            VerticalAnchor.Settled,
            resolveVerticalTarget(offsetPx = 120f, commitDistancePx = commitDistance, velocityPx = slowFling)
        )
    }

    @Test
    fun `a fast downward fling dismisses`() {
        assertEquals(VerticalAnchor.Dismiss, resolveVerticalTarget(offsetPx = 50f, commitDistancePx = commitDistance, velocityPx = 2_500f))
    }

    @Test
    fun `a fast downward flick dismisses even when the velocity sign is spuriously up`() {
        // VelocityTracker can report the wrong sign on a fast, short flick as the finger lifts.
        // The finger's net travel (a positive, downward offset) is the source of truth, so the
        // player must still dismiss rather than bounce back and open details.
        assertEquals(
            VerticalAnchor.Dismiss,
            resolveVerticalTarget(offsetPx = 60f, commitDistancePx = commitDistance, velocityPx = -2_500f)
        )
    }

    @Test
    fun `axis lock prefers horizontal on a tie and picks the dominant otherwise`() {
        assertEquals(GestureAxis.Horizontal, dominantAxis(dx = 10f, dy = 10f))
        assertEquals(GestureAxis.Horizontal, dominantAxis(dx = 12f, dy = 3f))
        assertEquals(GestureAxis.Vertical, dominantAxis(dx = 3f, dy = 12f))
    }

    @Test
    fun `a drag toward an existing neighbor is allowed up to one card`() {
        assertEquals(width, clampWithRubberBand(rawOffsetPx = 5_000f, extentPx = width, allowPositive = true, allowNegative = true))
    }

    @Test
    fun `a drag toward a missing neighbor rubber-bands short of a commit`() {
        val displayed = clampWithRubberBand(rawOffsetPx = 5_000f, extentPx = width, allowPositive = false, allowNegative = true)
        assertTrue(displayed > 0f, "the drag still gives visible feedback")
        assertTrue(displayed < width * PlayerGestureThresholds.COMMIT_FRACTION, "but never reaches the commit threshold")
    }

    @Test
    fun `rubber-band resistance is monotonic and symmetric`() {
        val near = clampWithRubberBand(rawOffsetPx = 200f, extentPx = width, allowPositive = false, allowNegative = false)
        val far = clampWithRubberBand(rawOffsetPx = 800f, extentPx = width, allowPositive = false, allowNegative = false)
        assertTrue(far > near)
        val negative = clampWithRubberBand(rawOffsetPx = -800f, extentPx = width, allowPositive = false, allowNegative = false)
        assertEquals(-far, negative)
    }

    @Test
    fun `a zero-size surface yields no offset`() {
        assertEquals(0f, clampWithRubberBand(rawOffsetPx = 500f, extentPx = 0f, allowPositive = true, allowNegative = true))
    }
}
