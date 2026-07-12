package io.cloudcauldron.bocan.playback.session

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

/**
 * The Bundle codec for the GET_AUDIO_FORMAT custom session command, shared by the
 * service (which reads the decoder Format and writes the result) and the controller
 * (which reads it back). A [MediaController] cannot see the decoder Format directly,
 * which is the whole reason this command exists; the service reads it from ExoPlayer
 * and answers with these keys.
 *
 * Every field is written only when present, so an absent value is a missing key, never
 * a sentinel. An entirely empty Bundle decodes to null.
 */
object AudioFormatResult {
    const val KEY_SAMPLE_RATE = "bocan.audio.sampleRateHz"
    const val KEY_CHANNEL_COUNT = "bocan.audio.channelCount"
    const val KEY_ENCODING = "bocan.audio.encoding"
    const val KEY_BIT_DEPTH = "bocan.audio.bitDepth"

    /** Encode a pipeline format into the result Bundle, omitting every absent field. */
    fun toBundle(format: AudioPipelineFormat): Bundle {
        val bundle = Bundle()
        format.sampleRateHz?.let { bundle.putInt(KEY_SAMPLE_RATE, it) }
        format.channelCount?.let { bundle.putInt(KEY_CHANNEL_COUNT, it) }
        format.encoding?.let { bundle.putString(KEY_ENCODING, it) }
        format.bitDepth?.let { bundle.putInt(KEY_BIT_DEPTH, it) }
        return bundle
    }

    /** Decode a result Bundle, or null when it carries no usable field. */
    fun fromBundle(bundle: Bundle): AudioPipelineFormat? {
        val format = AudioPipelineFormat(
            sampleRateHz = bundle.optInt(KEY_SAMPLE_RATE),
            channelCount = bundle.optInt(KEY_CHANNEL_COUNT),
            encoding = if (bundle.containsKey(KEY_ENCODING)) bundle.getString(KEY_ENCODING) else null,
            bitDepth = bundle.optInt(KEY_BIT_DEPTH)
        )
        return if (format.isEmpty) null else format
    }

    /**
     * Map an ExoPlayer decoder [Format] to a pipeline format. A null format (nothing
     * playing, or no audio track) yields an empty value. The bit depth is derivable only
     * for raw PCM output; for a coded stream (FLAC, MP3) it is absent and the codec MIME
     * carries the detail instead.
     */
    @UnstableApi
    fun fromExoFormat(format: Format?): AudioPipelineFormat {
        if (format == null) return AudioPipelineFormat()
        return AudioPipelineFormat(
            sampleRateHz = format.sampleRate.takeIf { it != Format.NO_VALUE && it > 0 },
            channelCount = format.channelCount.takeIf { it != Format.NO_VALUE && it > 0 },
            encoding = format.sampleMimeType,
            bitDepth = bitDepthOf(format.pcmEncoding)
        )
    }

    @UnstableApi
    private fun bitDepthOf(pcmEncoding: Int): Int? = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> BITS_8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> BITS_16
        C.ENCODING_PCM_24BIT -> BITS_24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> BITS_32
        else -> null
    }

    private fun Bundle.optInt(key: String): Int? = if (containsKey(key)) getInt(key) else null

    private const val BITS_8 = 8
    private const val BITS_16 = 16
    private const val BITS_24 = 24
    private const val BITS_32 = 32
}
