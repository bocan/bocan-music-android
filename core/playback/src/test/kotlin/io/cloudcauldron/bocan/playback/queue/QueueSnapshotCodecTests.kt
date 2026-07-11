package io.cloudcauldron.bocan.playback.queue

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class QueueSnapshotCodecTests {
    @Test
    fun `encode then decode round trips`() {
        val snapshot = QueueSnapshot(
            mediaIds = listOf("track:1", "track:2", "episode:ep9"),
            index = 1,
            positionMs = 12_345,
            repeatMode = RepeatMode.All,
            shuffleActive = true,
            currentDurationMs = 300_000
        )
        val decoded = QueueSnapshotCodec.decode(QueueSnapshotCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
    }

    @Test
    fun `a corrupt blob decodes to null`() {
        assertNull(QueueSnapshotCodec.decode("{not valid json"))
        assertNull(QueueSnapshotCodec.decode(""))
    }

    @Test
    fun `a future schema version decodes to null`() {
        val futureVersion = """
            {"mediaIds":[],"index":-1,"positionMs":0,"repeatMode":"Off","shuffleActive":false,"version":2}
        """.trimIndent()
        assertNull(QueueSnapshotCodec.decode(futureVersion))
    }

    @Test
    fun `unknown keys are ignored`() {
        val withExtra = """
            {"mediaIds":["track:1"],"index":0,"positionMs":0,"repeatMode":"Off","shuffleActive":false,"version":1,"future":"x"}
        """.trimIndent()
        val decoded = QueueSnapshotCodec.decode(withExtra)
        assertEquals(listOf("track:1"), decoded?.mediaIds)
    }
}
