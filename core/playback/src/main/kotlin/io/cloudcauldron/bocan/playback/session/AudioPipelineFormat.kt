package io.cloudcauldron.bocan.playback.session

/**
 * The live audio pipeline format read from the decoder for the song details sheet:
 * the sample rate, channel count, container or codec (as a MIME string), and the PCM
 * bit depth when the output is raw PCM. Every field is optional; a field the decoder
 * does not report is null and its row is omitted. An all-null value means the pipeline
 * is unknown (nothing playing, or a non-audio track) and no pipeline line is shown.
 */
data class AudioPipelineFormat(
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val encoding: String? = null,
    val bitDepth: Int? = null
) {
    /** True when the decoder reported nothing usable, so callers render no pipeline line. */
    val isEmpty: Boolean
        get() = sampleRateHz == null && channelCount == null && encoding == null && bitDepth == null
}
