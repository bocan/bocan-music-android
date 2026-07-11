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
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class EqProcessorTests {
    @Test
    fun `a flat processor passes float samples through unchanged`() {
        val processor = configured()
        val input = sine(CENTER_HZ, SAMPLES)
        val output = feed(processor, input)
        input.zip(output).forEach { (i, o) -> assertEquals(i, o, FLAT_EPSILON) }
    }

    @Test
    fun `a boosted band lifts its centre frequency`() {
        val processor = configured()
        val gains = EqBands.flatGains.toMutableList().also { it[CENTER_BAND] = 6.0 }
        processor.setGains(gains)
        assertEquals(EqBands.COUNT, processor.activeBandCount)
        val measured = gainDb(processor, EqBands.centersHz[CENTER_BAND])
        assertEquals(6.0, measured, TOLERANCE_DB)
    }

    @Test
    fun `a boosted band barely touches a distant frequency`() {
        val processor = configured()
        val gains = EqBands.flatGains.toMutableList().also { it[CENTER_BAND] = 6.0 }
        processor.setGains(gains)
        // 8 kHz is three bands above the 1 kHz band; it should stay close to unity.
        assertTrue(gainDb(processor, EqBands.centersHz[8]) < 1.5)
    }

    @Test
    fun `swapping coefficients on silence injects no click`() {
        val processor = configured()
        feed(processor, FloatArray(SAMPLES)) // prime with silence
        val gains = EqBands.flatGains.toMutableList().also { it[CENTER_BAND] = 12.0 }
        processor.setGains(gains)
        val afterSwap = feed(processor, FloatArray(SAMPLES))
        afterSwap.forEach { assertEquals(0f, it, FLAT_EPSILON) }
    }

    private fun configured(): EqProcessor {
        val processor = EqProcessor()
        processor.configure(AudioFormat(SAMPLE_RATE, 1, C.ENCODING_PCM_FLOAT))
        processor.flush()
        return processor
    }

    private fun gainDb(processor: EqProcessor, frequencyHz: Double): Double {
        val output = feed(processor, sine(frequencyHz, SAMPLES))
        val rmsIn = sqrt(AMPLITUDE * AMPLITUDE / 2)
        val rmsOut = sqrt(output.drop(WARMUP).sumOf { (it * it).toDouble() } / (output.size - WARMUP))
        return 20.0 * log10(rmsOut / rmsIn)
    }

    private fun sine(frequencyHz: Double, count: Int): FloatArray {
        val step = 2.0 * PI * frequencyHz / SAMPLE_RATE
        return FloatArray(count) { (AMPLITUDE * sin(step * it)).toFloat() }
    }

    private fun feed(processor: EqProcessor, samples: FloatArray): FloatArray {
        val input = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat)
        input.flip()
        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        return FloatArray(out.remaining() / 4) { out.float }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CENTER_BAND = 5 // 1 kHz
        const val CENTER_HZ = 1_000.0
        const val AMPLITUDE = 0.5
        const val SAMPLES = 20_000
        const val WARMUP = 4_096
        const val TOLERANCE_DB = 0.7
        const val FLAT_EPSILON = 1e-6f
    }
}
