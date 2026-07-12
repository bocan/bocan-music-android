package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WaveformTapTests {
    @Test
    fun `nothing published before any audio flows`() {
        val tap = WaveformTap()
        assertFalse(tap.copyInto(FloatArray(tap.pointCount)))
    }

    @Test
    fun `float audio passes through byte-faithfully`() {
        val tap = configured(C.ENCODING_PCM_FLOAT)
        val input = FloatArray(FRAMES) { (it / FRAMES.toFloat()) - 0.5f }
        val output = feedFloat(tap, input)
        assertEquals(input.size, output.size)
        input.zip(output).forEach { (i, o) -> assertEquals(i, o, EPSILON) }
    }

    @Test
    fun `the published frame is the decimated newest window`() {
        val tap = configured(C.ENCODING_PCM_FLOAT)
        // Exactly enough frames to fill the ring once: pointCount * decimation.
        val total = tap.pointCount * DECIMATION
        val input = FloatArray(total) { it / total.toFloat() }
        feedFloat(tap, input)
        val frame = FloatArray(tap.pointCount)
        assertTrue(tap.copyInto(frame))
        // Chronological oldest to newest: point k is input sample 8k (every 8th, offset 0).
        assertEquals(input[0], frame[0], EPSILON)
        assertEquals(input[DECIMATION], frame[1], EPSILON)
        assertEquals(input[(tap.pointCount - 1) * DECIMATION], frame[tap.pointCount - 1], EPSILON)
    }

    @Test
    fun `sixteen bit audio is normalised to plus or minus one`() {
        val tap = configured(C.ENCODING_PCM_16BIT)
        val input = ShortArray(FRAMES) { Short.MAX_VALUE }
        feed16(tap, input)
        val frame = FloatArray(tap.pointCount)
        assertTrue(tap.copyInto(frame))
        assertEquals(1f, frame.last(), 1e-3f)
    }

    @Test
    fun `a flush resets the trace to flat`() {
        val tap = configured(C.ENCODING_PCM_FLOAT)
        feedFloat(tap, FloatArray(FRAMES) { 0.9f })
        tap.flush()
        val frame = FloatArray(tap.pointCount)
        assertTrue(tap.copyInto(frame))
        frame.forEach { assertEquals(0f, it, EPSILON) }
    }

    private fun configured(encoding: Int): WaveformTap {
        val tap = WaveformTap()
        tap.configure(AudioFormat(SAMPLE_RATE, 1, encoding))
        tap.flush()
        return tap
    }

    private fun feedFloat(tap: WaveformTap, samples: FloatArray): FloatArray {
        val input = ByteBuffer.allocate(samples.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat)
        input.flip()
        tap.queueInput(input)
        val out = tap.output.order(ByteOrder.nativeOrder())
        return FloatArray(out.remaining() / BYTES_PER_FLOAT) { out.float }
    }

    private fun feed16(tap: WaveformTap, samples: ShortArray) {
        val input = ByteBuffer.allocate(samples.size * BYTES_PER_SHORT).order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        tap.queueInput(input)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAMES = 4_096
        const val DECIMATION = 8
        const val BYTES_PER_FLOAT = 4
        const val BYTES_PER_SHORT = 2
        const val EPSILON = 1e-6f
    }
}
