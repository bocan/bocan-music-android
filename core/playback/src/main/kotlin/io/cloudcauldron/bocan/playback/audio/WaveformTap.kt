package io.cloudcauldron.bocan.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * A read-only view of the most recent output waveform, for the Now Playing oscilloscope.
 * The single producer is the audio thread; a single consumer (the GL render thread) copies
 * the latest frame out. Values are mono, normalised to -1..1.
 */
interface WaveformSource {
    /** The number of points in one published frame. */
    val pointCount: Int

    /**
     * Copy the latest waveform frame into [out] (chronological, oldest to newest). Returns
     * false when nothing has played yet, so the caller can draw a flat idle trace.
     */
    fun copyInto(out: FloatArray): Boolean
}

/**
 * A Media3 [BaseAudioProcessor] that taps the post-effects signal for the oscilloscope
 * without changing it: the input bytes pass straight through to the output. On the audio
 * thread it decimates the mono signal into a small ring, then publishes a snapshot into one
 * of a few rotating slots referenced by an [AtomicInteger], so the GL thread reads a
 * complete, consistent frame without a lock (single producer, single consumer). No
 * allocation and no trigonometry on the hot path, matching the rest of the effects chain.
 *
 * It sits last in the chain so the trace reflects what is actually heard (EQ, gain, fades).
 * Encodings other than 16-bit and float PCM pass through unsampled: the trace simply holds.
 */
@UnstableApi
class WaveformTap(override val pointCount: Int = DEFAULT_POINTS) :
    BaseAudioProcessor(),
    WaveformSource {
    // Audio-thread-owned decimated mono ring and its write cursor.
    private val ring = FloatArray(pointCount)
    private var writeIndex = 0
    private var decimationCounter = 0
    private var channelCount = 0
    private var sampling = false

    // Published snapshots: the writer rotates through slots so the reader's slot is never
    // the one being written, and the reader references the current one atomically.
    private val slots = Array(SLOT_COUNT) { FloatArray(pointCount) }
    private val published = AtomicInteger(NO_FRAME)
    private var nextSlot = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        channelCount = inputAudioFormat.channelCount
        sampling = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: StreamMetadata) {
        // A stop or seek resets the trace to flat rather than smearing stale audio.
        ring.fill(0f)
        writeIndex = 0
        decimationCounter = 0
        publish()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size == 0) return

        val output = replaceOutputBuffer(size).order(ByteOrder.nativeOrder())
        if (sampling && channelCount > 0) {
            sample(inputBuffer.order(ByteOrder.nativeOrder()), position, limit)
        }
        output.put(inputBuffer)
        output.flip()
        if (sampling && channelCount > 0) publish()
    }

    private fun sample(input: ByteBuffer, position: Int, limit: Int) {
        val is16 = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val bytesPerSample = if (is16) BYTES_PER_16BIT_SAMPLE else BYTES_PER_FLOAT_SAMPLE
        val frameBytes = bytesPerSample * channelCount
        var frame = position
        while (frame + frameBytes <= limit) {
            var sum = 0f
            var channel = 0
            var offset = frame
            while (channel < channelCount) {
                sum += if (is16) input.getShort(offset) / SHORT_SCALE else input.getFloat(offset)
                offset += bytesPerSample
                channel++
            }
            if (decimationCounter == 0) {
                ring[writeIndex] = sum / channelCount
                writeIndex = (writeIndex + 1) % pointCount
            }
            decimationCounter = (decimationCounter + 1) % DECIMATION
            frame += frameBytes
        }
    }

    private fun publish() {
        val slot = slots[nextSlot]
        var k = 0
        while (k < pointCount) {
            slot[k] = ring[(writeIndex + k) % pointCount]
            k++
        }
        published.set(nextSlot)
        nextSlot = (nextSlot + 1) % SLOT_COUNT
    }

    override fun copyInto(out: FloatArray): Boolean {
        val index = published.get()
        if (index == NO_FRAME) return false
        System.arraycopy(slots[index], 0, out, 0, minOf(out.size, pointCount))
        return true
    }

    private companion object {
        const val DEFAULT_POINTS = 256
        const val DECIMATION = 8
        const val SLOT_COUNT = 3
        const val NO_FRAME = -1
        const val SHORT_SCALE = 32_768f
        const val BYTES_PER_16BIT_SAMPLE = 2
        const val BYTES_PER_FLOAT_SAMPLE = 4
    }
}
