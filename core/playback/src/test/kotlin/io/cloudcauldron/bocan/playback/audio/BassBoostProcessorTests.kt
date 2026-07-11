package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BassBoostProcessorTests {
    @Test
    fun `off is a pass-through`() {
        val processor = configured()
        assertFalse(processor.isBoosting)
        val input = sine(60.0, SAMPLES)
        input.zip(feed(processor, input)).forEach { (i, o) -> assertEquals(i, o, EPSILON) }
    }

    @Test
    fun `boost raises 60 Hz and leaves 2 kHz`() {
        val processor = configured()
        processor.setGainDb(6.0)
        assertTrue(processor.isBoosting)
        // 60 Hz sits just below the 80 Hz corner: clearly lifted, though not yet the full shelf.
        assertTrue(gainDb(processor, 60.0) > 2.0, "60 Hz should be audibly raised")
        // Deep bass, well below the corner, reaches the design gain.
        assertEquals(6.0, gainDb(processor, DEEP_BASS_HZ), DESIGN_TOLERANCE_DB)
        assertTrue(gainDb(processor, 2_000.0) < 1.0, "the shelf should leave 2 kHz essentially untouched")
    }

    @Test
    fun `gain is clamped to the nine decibel ceiling`() {
        val processor = configured()
        processor.setGainDb(20.0)
        // A 9 dB shelf, not 20: the deep-bass lift should sit near +9 dB.
        assertEquals(9.0, gainDb(processor, DEEP_BASS_HZ), DESIGN_TOLERANCE_DB)
    }

    private fun configured(): BassBoostProcessor {
        val processor = BassBoostProcessor()
        processor.configure(AudioFormat(SAMPLE_RATE, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        return processor
    }

    private fun gainDb(processor: BassBoostProcessor, frequencyHz: Double): Double {
        val output = feed(processor, sine(frequencyHz, SAMPLES))
        val rmsIn = sqrt(AMPLITUDE * AMPLITUDE / 2)
        val rmsOut = sqrt(output.drop(WARMUP).sumOf { (it * it).toDouble() } / (output.size - WARMUP))
        return 20.0 * log10(rmsOut / rmsIn)
    }

    private fun sine(frequencyHz: Double, count: Int): FloatArray {
        val step = 2.0 * PI * frequencyHz / SAMPLE_RATE
        return FloatArray(count) { (AMPLITUDE * sin(step * it)).toFloat() }
    }

    private fun feed(processor: BassBoostProcessor, samples: FloatArray): FloatArray {
        val input = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat)
        input.flip()
        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        return FloatArray(out.remaining() / 4) { out.float }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val AMPLITUDE = 0.5
        const val SAMPLES = 40_000
        const val WARMUP = 8_192
        const val DEEP_BASS_HZ = 25.0
        const val DESIGN_TOLERANCE_DB = 1.3
        const val EPSILON = 1e-6f
    }
}
