package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A Media3 [BaseAudioProcessor] that adds a low-shelf bass lift at a fixed 80 Hz corner,
 * one biquad per channel. Sits after the EQ in the chain. Gain range is 0 to +9 dB
 * (phase 08 contract); 0 dB publishes a pass-through so bass boost off is byte-faithful.
 *
 * As with [EqProcessor], the shelf coefficient is computed off the audio thread and
 * published through a `@Volatile` swap; the audio thread only runs the per-channel
 * [BiquadState] memory.
 */
@UnstableApi
class BassBoostProcessor : BaseAudioProcessor() {
    @Volatile
    private var coeffs: Biquad = Biquad.PASSTHROUGH

    @Volatile
    private var boosting: Boolean = false

    @Volatile
    private var gainDb: Double = 0.0

    @Volatile
    private var sampleRate: Int = 0

    private var states: Array<BiquadState> = emptyArray()

    /** Set the bass boost in decibels, clamped to 0 to +9 dB; recomputes off the audio thread. */
    fun setGainDb(newGainDb: Double) {
        gainDb = newGainDb.coerceIn(EqState.BASS_MIN_DB, EqState.BASS_MAX_DB)
        recompute()
    }

    /** True when the shelf is lifting, for tests and diagnostics. */
    val isBoosting: Boolean get() = boosting

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        states = Array(inputAudioFormat.channelCount) { BiquadState() }
        recompute()
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        states.forEach(BiquadState::reset)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size == 0) return

        val output = replaceOutputBuffer(size).order(ByteOrder.nativeOrder())
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val currentCoeffs = coeffs

        if (!boosting || states.isEmpty()) {
            output.put(input)
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            filter16(input, output, position, limit, currentCoeffs)
        } else {
            filterFloat(input, output, position, limit, currentCoeffs)
        }

        inputBuffer.position(limit)
        output.flip()
    }

    private fun filter16(input: ByteBuffer, output: ByteBuffer, position: Int, limit: Int, currentCoeffs: Biquad) {
        val channelCount = states.size
        var channel = 0
        var i = position
        while (i + BYTES_PER_16BIT_SAMPLE <= limit) {
            val filtered = states[channel].process(currentCoeffs, input.getShort(i) / SHORT_SCALE)
            output.putShort(toShort(filtered))
            channel = (channel + 1) % channelCount
            i += BYTES_PER_16BIT_SAMPLE
        }
    }

    private fun filterFloat(input: ByteBuffer, output: ByteBuffer, position: Int, limit: Int, currentCoeffs: Biquad) {
        val channelCount = states.size
        var channel = 0
        var i = position
        while (i + BYTES_PER_FLOAT_SAMPLE <= limit) {
            val filtered = states[channel].process(currentCoeffs, input.getFloat(i).toDouble())
            output.putFloat(filtered.toFloat())
            channel = (channel + 1) % channelCount
            i += BYTES_PER_FLOAT_SAMPLE
        }
    }

    private fun recompute() {
        val rate = sampleRate
        if (rate <= 0) return
        val db = gainDb
        boosting = db > 0.0
        coeffs = if (boosting) Biquad.lowShelf(rate, SHELF_HZ, db) else Biquad.PASSTHROUGH
    }

    private fun toShort(value: Double): Short {
        val scaled = value * SHORT_SCALE
        val clamped = when {
            scaled > Short.MAX_VALUE.toDouble() -> Short.MAX_VALUE.toInt()
            scaled < Short.MIN_VALUE.toDouble() -> Short.MIN_VALUE.toInt()
            else -> Math.round(scaled).toInt()
        }
        return clamped.toShort()
    }

    private companion object {
        const val SHELF_HZ = 80.0
        const val BYTES_PER_16BIT_SAMPLE = 2
        const val BYTES_PER_FLOAT_SAMPLE = 4
        const val SHORT_SCALE = 32_768.0
    }
}
