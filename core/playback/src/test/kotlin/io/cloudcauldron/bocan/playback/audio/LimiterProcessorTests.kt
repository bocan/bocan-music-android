package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LimiterProcessorTests {
    @Test
    fun `softClip leaves quiet samples untouched`() {
        assertEquals(0.5, LimiterMath.softClip(0.5), 0.0)
        assertEquals(-0.5, LimiterMath.softClip(-0.5), 0.0)
        assertEquals(LimiterMath.KNEE, LimiterMath.softClip(LimiterMath.KNEE), 0.0)
    }

    @Test
    fun `softClip never reaches full scale however loud`() {
        listOf(1.0, 2.0, 4.0, 100.0).forEach { assertTrue(LimiterMath.softClip(it) < 1.0) }
        listOf(-1.0, -2.0, -4.0, -100.0).forEach { assertTrue(LimiterMath.softClip(it) > -1.0) }
    }

    @Test
    fun `a hot square wave never exceeds full scale when enabled`() {
        val processor = configured()
        processor.setEnabled(true)
        // A +12 dB square wave sits near +/-2.0, well past full scale.
        val hot = FloatArray(SAMPLES) { if (it % 2 == 0) HOT else -HOT }
        feed(processor, hot).forEach { assertTrue(abs(it) <= 1.0f, "sample $it exceeded full scale") }
    }

    @Test
    fun `disabled is a byte-faithful pass-through`() {
        val processor = configured()
        val hot = FloatArray(SAMPLES) { if (it % 2 == 0) HOT else -HOT }
        hot.zip(feed(processor, hot)).forEach { (i, o) -> assertEquals(i, o, 0f) }
    }

    private fun configured(): LimiterProcessor {
        val processor = LimiterProcessor()
        processor.configure(AudioFormat(44_100, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        return processor
    }

    private fun feed(processor: LimiterProcessor, samples: FloatArray): FloatArray {
        val input = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat)
        input.flip()
        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        return FloatArray(out.remaining() / 4) { out.float }
    }

    private companion object {
        const val SAMPLES = 512
        const val HOT = 1.995f
    }
}
