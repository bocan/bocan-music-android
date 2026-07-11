package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A Media3 [BaseAudioProcessor] that multiplies every PCM sample by a linear
 * ReplayGain factor. It is inserted into the [androidx.media3.exoplayer.audio.DefaultAudioSink]
 * chain by the RenderersFactory.
 *
 * This is the chain's single gain stage. Two contributions multiply here:
 *
 *  - the ReplayGain [factor], computed off-thread by [ReplayGainMath] whenever the
 *    current item changes; and
 *  - a [fadeFactor] (0..1) the [Crossfader] ramps for "fade between tracks" and the
 *    short fade before a manual skip.
 *
 * Both live in the gain stage on purpose (phase 08 gotcha): the sleep timer owns
 * `player.volume`, while ReplayGain and fades compose here instead of fighting it. The
 * audio thread never allocates, locks, or computes gain: it reads two volatile doubles,
 * multiplies once, and scales. An effective gain of exactly [ReplayGainMath.UNITY]
 * copies samples unchanged as a fast path, so the chain stays byte-faithful when idle.
 *
 * Supports 16-bit and float PCM, the two encodings ExoPlayer feeds an audio
 * processor; any other encoding is rejected so the sink can convert upstream.
 */
@UnstableApi
class ReplayGainProcessor : BaseAudioProcessor() {
    @Volatile
    private var factor: Double = ReplayGainMath.UNITY

    @Volatile
    private var fadeFactor: Double = ReplayGainMath.UNITY

    /** Update the ReplayGain factor applied from the next processed buffer onward. */
    fun setFactor(newFactor: Double) {
        factor = newFactor
    }

    /** Update the fade multiplier (0..1) applied from the next processed buffer onward. */
    fun setFadeFactor(newFadeFactor: Float) {
        fadeFactor = newFadeFactor.toDouble().coerceIn(0.0, 1.0)
    }

    /** The ReplayGain factor currently applied, for tests and diagnostics. */
    val currentFactor: Double get() = factor

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size == 0) return

        val output = replaceOutputBuffer(size).order(ByteOrder.nativeOrder())
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val gain = factor * fadeFactor

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            var i = position
            while (i + BYTES_PER_16BIT_SAMPLE <= limit) {
                output.putShort(scaleShort(input.getShort(i), gain))
                i += BYTES_PER_16BIT_SAMPLE
            }
        } else {
            var i = position
            while (i + BYTES_PER_FLOAT_SAMPLE <= limit) {
                output.putFloat(scaleFloat(input.getFloat(i), gain))
                i += BYTES_PER_FLOAT_SAMPLE
            }
        }

        inputBuffer.position(limit)
        output.flip()
    }

    private fun scaleShort(sample: Short, gain: Double): Short {
        if (gain == ReplayGainMath.UNITY) return sample
        val scaled = sample * gain
        val clamped = when {
            scaled > Short.MAX_VALUE.toDouble() -> Short.MAX_VALUE.toInt()
            scaled < Short.MIN_VALUE.toDouble() -> Short.MIN_VALUE.toInt()
            else -> Math.round(scaled).toInt()
        }
        return clamped.toShort()
    }

    private fun scaleFloat(sample: Float, gain: Double): Float {
        if (gain == ReplayGainMath.UNITY) return sample
        val scaled = sample * gain
        return when {
            scaled > 1.0 -> 1.0f
            scaled < -1.0 -> -1.0f
            else -> scaled.toFloat()
        }
    }

    private companion object {
        const val BYTES_PER_16BIT_SAMPLE = 2
        const val BYTES_PER_FLOAT_SAMPLE = 4
    }
}
