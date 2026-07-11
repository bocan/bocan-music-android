package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ReplayGainProcessorTests {
    private fun configured(encoding: Int): ReplayGainProcessor {
        val processor = ReplayGainProcessor()
        processor.configure(AudioFormat(44_100, 2, encoding))
        processor.flush()
        return processor
    }

    private fun input16(vararg samples: Short): ByteBuffer {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach(buffer::putShort)
        buffer.flip()
        return buffer
    }

    private fun readShorts(buffer: ByteBuffer): List<Short> {
        val ordered = buffer.order(ByteOrder.nativeOrder())
        return buildList { while (ordered.remaining() >= 2) add(ordered.short) }
    }

    @Test
    fun `it halves samples at a factor of one half`() {
        val processor = configured(C.ENCODING_PCM_16BIT)
        processor.setFactor(0.5)
        processor.queueInput(input16(1000, -2000, 30000))
        assertEquals(listOf<Short>(500, -1000, 15000), readShorts(processor.output))
    }

    @Test
    fun `the fade factor multiplies the replay gain factor`() {
        val processor = configured(C.ENCODING_PCM_16BIT)
        processor.setFactor(1.0)
        processor.setFadeFactor(0.5f)
        processor.queueInput(input16(1000, -2000, 30000))
        assertEquals(listOf<Short>(500, -1000, 15000), readShorts(processor.output))
    }

    @Test
    fun `a full fade silences the output`() {
        val processor = configured(C.ENCODING_PCM_16BIT)
        processor.setFactor(1.0)
        processor.setFadeFactor(0f)
        processor.queueInput(input16(1000, -2000, 30000))
        assertEquals(listOf<Short>(0, 0, 0), readShorts(processor.output))
    }

    @Test
    fun `unity passes samples through unchanged`() {
        val processor = configured(C.ENCODING_PCM_16BIT)
        processor.setFactor(ReplayGainMath.UNITY)
        processor.queueInput(input16(123, -456, 789))
        assertEquals(listOf<Short>(123, -456, 789), readShorts(processor.output))
    }

    @Test
    fun `a boosting factor clamps to the sixteen bit range`() {
        val processor = configured(C.ENCODING_PCM_16BIT)
        processor.setFactor(2.0)
        processor.queueInput(input16(20000, -20000))
        assertEquals(listOf(Short.MAX_VALUE, Short.MIN_VALUE), readShorts(processor.output))
    }

    @Test
    fun `float samples are scaled and clamped to plus or minus one`() {
        val processor = configured(C.ENCODING_PCM_FLOAT)
        processor.setFactor(2.0)
        val buffer = ByteBuffer.allocate(3 * 4).order(ByteOrder.nativeOrder())
        buffer.putFloat(0.25f)
        buffer.putFloat(-0.25f)
        buffer.putFloat(0.75f)
        buffer.flip()
        processor.queueInput(buffer)
        val out = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(0.5f, out.float, 1e-6f)
        assertEquals(-0.5f, out.float, 1e-6f)
        assertEquals(1.0f, out.float, 1e-6f)
    }
}
