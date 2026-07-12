package io.cloudcauldron.bocan.playback.session

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AudioFormatResultTests {
    @Test
    fun `a full format survives a bundle round trip`() {
        val format = AudioPipelineFormat(sampleRateHz = 44_100, channelCount = 2, encoding = "audio/flac", bitDepth = 24)
        val decoded = AudioFormatResult.fromBundle(AudioFormatResult.toBundle(format))
        assertEquals(format, decoded)
    }

    @Test
    fun `absent fields stay absent, they are not zeroed`() {
        val format = AudioPipelineFormat(sampleRateHz = 48_000, channelCount = null, encoding = null, bitDepth = null)
        val bundle = AudioFormatResult.toBundle(format)
        assertTrue(bundle.containsKey(AudioFormatResult.KEY_SAMPLE_RATE))
        assertTrue(!bundle.containsKey(AudioFormatResult.KEY_CHANNEL_COUNT))
        assertEquals(format, AudioFormatResult.fromBundle(bundle))
    }

    @Test
    fun `an empty bundle decodes to null`() {
        assertNull(AudioFormatResult.fromBundle(AudioFormatResult.toBundle(AudioPipelineFormat())))
    }

    @Test
    fun `a null decoder format yields an empty value`() {
        assertTrue(AudioFormatResult.fromExoFormat(null).isEmpty)
    }

    @Test
    fun `a coded stream reports its codec but no bit depth`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_FLAC)
            .setSampleRate(44_100)
            .setChannelCount(2)
            .build()
        val pipeline = AudioFormatResult.fromExoFormat(format)
        assertEquals(44_100, pipeline.sampleRateHz)
        assertEquals(2, pipeline.channelCount)
        assertEquals(MimeTypes.AUDIO_FLAC, pipeline.encoding)
        assertNull(pipeline.bitDepth)
    }

    @Test
    fun `raw PCM output derives its bit depth`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setSampleRate(48_000)
            .setChannelCount(2)
            .build()
        assertEquals(16, AudioFormatResult.fromExoFormat(format).bitDepth)
    }

    @Test
    fun `unset sample rate and channel count are dropped`() {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()
        val pipeline = AudioFormatResult.fromExoFormat(format)
        assertNull(pipeline.sampleRateHz)
        assertNull(pipeline.channelCount)
        assertEquals(MimeTypes.AUDIO_AAC, pipeline.encoding)
    }
}
