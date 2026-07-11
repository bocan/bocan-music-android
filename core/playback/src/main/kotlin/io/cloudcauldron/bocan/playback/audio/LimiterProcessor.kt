package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sign
import kotlin.math.tanh

/**
 * The last stage in the effects chain: a lookahead-free soft-knee peak limiter that
 * guards against clipping introduced by a hot EQ or bass boost. Below the knee it is a
 * pass-through (so it never colours normal-level audio and the master A/B stays honest);
 * above the knee it soft-clips with a tanh curve so the output magnitude never reaches
 * full scale however loud the input.
 *
 * The guard is only [enabled] when a positive gain is in play (any boosted band or bass
 * boost). With every band at or below unity nothing can exceed full scale, so the
 * limiter stays a pass-through and leaves the signal untouched.
 *
 * [LimiterMath.softClip] is the pure curve, unit tested directly.
 */
@UnstableApi
class LimiterProcessor : BaseAudioProcessor() {
    @Volatile
    private var enabled: Boolean = false

    /** Enable the guard (true when a positive EQ or bass gain is active). */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /** Whether the guard is currently limiting, for tests and diagnostics. */
    val isEnabled: Boolean get() = enabled

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

        if (!enabled) {
            output.put(input)
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            limit16(input, output, position, limit)
        } else {
            limitFloat(input, output, position, limit)
        }

        inputBuffer.position(limit)
        output.flip()
    }

    private fun limit16(input: ByteBuffer, output: ByteBuffer, position: Int, limit: Int) {
        var i = position
        while (i + BYTES_PER_16BIT_SAMPLE <= limit) {
            val limited = LimiterMath.softClip(input.getShort(i) / SHORT_SCALE)
            output.putShort(Math.round(limited * SHORT_SCALE).toInt().toShort())
            i += BYTES_PER_16BIT_SAMPLE
        }
    }

    private fun limitFloat(input: ByteBuffer, output: ByteBuffer, position: Int, limit: Int) {
        var i = position
        while (i + BYTES_PER_FLOAT_SAMPLE <= limit) {
            output.putFloat(LimiterMath.softClip(input.getFloat(i).toDouble()).toFloat())
            i += BYTES_PER_FLOAT_SAMPLE
        }
    }

    private companion object {
        const val BYTES_PER_16BIT_SAMPLE = 2
        const val BYTES_PER_FLOAT_SAMPLE = 4
        const val SHORT_SCALE = 32_768.0
    }
}

/** The pure soft-knee limiter curve, kept off any Android type so it is unit tested directly. */
object LimiterMath {
    /** Where the soft knee starts; below this the curve is the identity. */
    const val KNEE = 0.9

    /** The asymptotic ceiling the curve approaches but never reaches. */
    const val CEILING = 0.9999

    /**
     * Pass [sample] through unchanged when its magnitude is at or below the [KNEE];
     * above the knee, soft-clip with a tanh curve so the result stays below [CEILING]
     * (and therefore strictly below full scale) for any input, however large.
     */
    fun softClip(sample: Double): Double {
        val magnitude = if (sample < 0) -sample else sample
        if (magnitude <= KNEE) return sample
        val over = (magnitude - KNEE) / (1.0 - KNEE)
        return sign(sample) * (KNEE + (CEILING - KNEE) * tanh(over))
    }
}
