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
 * A Media3 [BaseAudioProcessor] that shapes the signal with ten cascaded biquad
 * peaking filters, one per [EqBands] band, applied per channel. It sits first in the
 * effects chain (decoder output, then EQ), so it works the same for platform-decoded
 * and FFmpeg-decoded sources.
 *
 * Coefficients are computed off the audio thread (in [setGains], and once per format in
 * [onConfigure]) and published to the audio thread through the `@Volatile` [chain] swap.
 * The audio thread only reads that array and runs the per-channel [BiquadState] memory
 * through it: no trigonometry, no allocation, no locking on the hot path (phase 08
 * gotcha). A flat curve publishes an empty chain, so the EQ is a byte-faithful
 * pass-through when disabled, which is what makes the master A/B honest.
 *
 * Supports 16-bit and float PCM, the two encodings ExoPlayer feeds a processor.
 */
@UnstableApi
class EqProcessor : BaseAudioProcessor() {
    // The published coefficient chain. Empty means "flat": pass samples through untouched.
    @Volatile
    private var chain: Array<Biquad> = EMPTY_CHAIN

    @Volatile
    private var gainsDb: DoubleArray = DoubleArray(EqBands.COUNT)

    @Volatile
    private var sampleRate: Int = 0

    // Owned by the audio thread only: [channel][band] delay memory. Rebuilt on configure.
    private var states: Array<Array<BiquadState>> = emptyArray()

    /** Update the ten band gains in decibels; recomputes coefficients off the audio thread. */
    fun setGains(newGains: List<Double>) {
        gainsDb = DoubleArray(EqBands.COUNT) { newGains.getOrElse(it) { 0.0 } }
        recompute()
    }

    /** The active coefficient chain length, for tests and diagnostics (0 when flat). */
    val activeBandCount: Int get() = chain.size

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        states = Array(inputAudioFormat.channelCount) { Array(EqBands.COUNT) { BiquadState() } }
        recompute()
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        // A seek must not smear stale samples across the cut: reset every band's memory.
        states.forEach { channel -> channel.forEach(BiquadState::reset) }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size == 0) return

        val output = replaceOutputBuffer(size).order(ByteOrder.nativeOrder())
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val currentChain = chain

        if (currentChain.isEmpty() || states.isEmpty()) {
            output.put(input)
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            filter16(input, output, position, limit, currentChain)
        } else {
            filterFloat(input, output, position, limit, currentChain)
        }

        inputBuffer.position(limit)
        output.flip()
    }

    private fun filter16(input: ByteBuffer, output: ByteBuffer, position: Int, limit: Int, currentChain: Array<Biquad>) {
        val channelCount = states.size
        var channel = 0
        var i = position
        while (i + BYTES_PER_16BIT_SAMPLE <= limit) {
            val sample = input.getShort(i) / SHORT_SCALE
            val filtered = runBands(currentChain, states[channel], sample)
            output.putShort(toShort(filtered))
            channel = (channel + 1) % channelCount
            i += BYTES_PER_16BIT_SAMPLE
        }
    }

    private fun filterFloat(input: ByteBuffer, output: ByteBuffer, position: Int, limit: Int, currentChain: Array<Biquad>) {
        val channelCount = states.size
        var channel = 0
        var i = position
        while (i + BYTES_PER_FLOAT_SAMPLE <= limit) {
            val filtered = runBands(currentChain, states[channel], input.getFloat(i).toDouble())
            output.putFloat(filtered.toFloat())
            channel = (channel + 1) % channelCount
            i += BYTES_PER_FLOAT_SAMPLE
        }
    }

    private fun runBands(currentChain: Array<Biquad>, channelStates: Array<BiquadState>, input: Double): Double {
        var value = input
        for (band in currentChain.indices) {
            value = channelStates[band].process(currentChain[band], value)
        }
        return value
    }

    private fun recompute() {
        val rate = sampleRate
        if (rate <= 0) return
        val gains = gainsDb
        if (gains.all { it == 0.0 }) {
            chain = EMPTY_CHAIN
            return
        }
        chain = Array(EqBands.COUNT) { band ->
            Biquad.peaking(rate, EqBands.centersHz[band], gains[band])
        }
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
        val EMPTY_CHAIN = emptyArray<Biquad>()
        const val BYTES_PER_16BIT_SAMPLE = 2
        const val BYTES_PER_FLOAT_SAMPLE = 4
        const val SHORT_SCALE = 32_768.0
    }
}
