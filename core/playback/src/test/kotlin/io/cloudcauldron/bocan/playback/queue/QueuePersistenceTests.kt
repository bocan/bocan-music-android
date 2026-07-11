package io.cloudcauldron.bocan.playback.queue

import io.cloudcauldron.bocan.playback.CoroutineDispatchers
import io.cloudcauldron.bocan.playback.NoopLog
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueuePersistenceTests {
    private fun tempDir(): File = Files.createTempDirectory("queue-test").toFile()

    private fun dispatchers() = UnconfinedTestDispatcher().let { CoroutineDispatchers(io = it, default = it, main = it) }

    private val sample = QueueSnapshot(
        mediaIds = listOf("track:1", "track:2"),
        index = 1,
        positionMs = 4_321,
        repeatMode = RepeatMode.One,
        shuffleActive = true,
        currentDurationMs = 250_000
    )

    @Test
    fun `save then load round trips`() = runTest {
        val dir = tempDir()
        val persistence = QueuePersistence(dir, dispatchers(), NoopLog)
        persistence.save(sample)
        assertEquals(sample, persistence.load())
    }

    @Test
    fun `a fresh instance restores after a simulated process death`() = runTest {
        val dir = tempDir()
        QueuePersistence(dir, dispatchers(), NoopLog).save(sample)
        // A new instance over the same directory models the process being killed and relaunched.
        val restored = QueuePersistence(dir, dispatchers(), NoopLog).load()
        assertEquals(sample, restored)
    }

    @Test
    fun `load with no saved file returns null`() = runTest {
        val restored = QueuePersistence(tempDir(), dispatchers(), NoopLog).load()
        assertNull(restored)
    }

    @Test
    fun `a torn file restores as no queue rather than crashing`() = runTest {
        val dir = tempDir()
        File(dir, "queue.json").writeText("{ truncated mid-write")
        assertNull(QueuePersistence(dir, dispatchers(), NoopLog).load())
    }

    @Test
    fun `a later save replaces the earlier snapshot`() = runTest {
        val dir = tempDir()
        val persistence = QueuePersistence(dir, dispatchers(), NoopLog)
        persistence.save(sample)
        val next = sample.copy(index = 0, positionMs = 10)
        persistence.save(next)
        assertEquals(next, persistence.load())
    }
}
