package io.cloudcauldron.bocan.playback.queue

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The persisted queue state that survives process death: the media ids, which one
 * is current, the position within it, and the repeat and shuffle flags. Restored
 * paused on service start, never auto-resumed.
 */
@Serializable
data class QueueSnapshot(
    val mediaIds: List<String>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: RepeatMode,
    val shuffleActive: Boolean,
    /** Duration of the current item, so restore can apply the long-item resume rule without preparing first. */
    val currentDurationMs: Long = 0,
    @SerialName("version") val schemaVersion: Int = CURRENT_VERSION
) {
    companion object {
        const val CURRENT_VERSION = 1

        /** An empty queue: nothing to restore. */
        val EMPTY = QueueSnapshot(emptyList(), -1, 0, RepeatMode.Off, shuffleActive = false)
    }
}

/**
 * Serializes a [QueueSnapshot] to and from JSON. Decoding is lenient and total: a
 * malformed or version-mismatched blob returns null rather than throwing, so a
 * corrupt file just means "start with an empty queue" instead of a crash loop.
 */
object QueueSnapshotCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: QueueSnapshot): String = json.encodeToString(QueueSnapshot.serializer(), snapshot)

    fun decode(text: String): QueueSnapshot? {
        // A corrupt or truncated file is expected after a hard kill mid-write; treat
        // it as no saved queue rather than propagating a crash.
        val decoded = try {
            json.decodeFromString(QueueSnapshot.serializer(), text)
        } catch (expected: SerializationException) {
            null
        } catch (expected: IllegalArgumentException) {
            null
        }
        return decoded?.takeIf { it.schemaVersion == QueueSnapshot.CURRENT_VERSION }
    }
}
