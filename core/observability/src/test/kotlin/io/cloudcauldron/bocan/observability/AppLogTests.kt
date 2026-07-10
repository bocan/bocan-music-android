package io.cloudcauldron.bocan.observability

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class AppLogTests {
    private lateinit var recorder: RecordingTree

    @Before
    fun plantRecorder() {
        recorder = RecordingTree()
        Timber.plant(recorder)
    }

    @After
    fun uprootAll() {
        Timber.uprootAll()
    }

    @Test
    fun `every sensitive key is redacted`() {
        val log = AppLog.forCategory(LogCategory.Sync)
        AppLog.sensitiveKeys.forEach { key ->
            recorder.clear()
            log.info("op.test", mapOf(key to "supersecret"))
            val message = recorder.single().message
            assertTrue("expected $key to be redacted in: $message", message.contains("$key=<redacted>"))
            assertFalse("secret value leaked for $key in: $message", message.contains("supersecret"))
        }
    }

    @Test
    fun `sensitive keys are redacted regardless of case`() {
        val log = AppLog.forCategory(LogCategory.Network)
        log.info("op.test", mapOf("Authorization" to "Bearer abc123"))
        val message = recorder.single().message
        assertTrue(message.contains("Authorization=<redacted>"))
        assertFalse(message.contains("abc123"))
    }

    @Test
    fun `non-sensitive fields pass through verbatim`() {
        val log = AppLog.forCategory(LogCategory.Playback)
        log.debug("track.play", mapOf("title" to "Thunderstruck", "ms" to 4200))
        val message = recorder.single().message
        assertEquals("track.play title=Thunderstruck ms=4200", message)
    }

    @Test
    fun `event strings are emitted verbatim without fields`() {
        val log = AppLog.forCategory(LogCategory.App)
        log.info("app.start")
        assertEquals("app.start", recorder.single().message)
    }

    @Test
    fun `category renders as the lowercased tag`() {
        LogCategory.entries.forEach { category ->
            recorder.clear()
            AppLog.forCategory(category).warning("op.test")
            assertEquals(category.name.lowercase(), recorder.single().tag)
        }
    }

    @Test
    fun `null field values render as null`() {
        val log = AppLog.forCategory(LogCategory.Persistence)
        log.error("db.open.failed", mapOf("cause" to null))
        assertEquals("db.open.failed cause=null", recorder.single().message)
    }

    @Test
    fun `all levels reach the tree`() {
        val log = AppLog.forCategory(LogCategory.Ui)
        log.debug("ui.debug")
        log.info("ui.info")
        log.warning("ui.warning")
        log.error("ui.error")
        assertEquals(listOf("ui.debug", "ui.info", "ui.warning", "ui.error"), recorder.entries.map { it.message })
    }

    private class RecordingTree : Timber.Tree() {
        data class Entry(val priority: Int, val tag: String?, val message: String)

        val entries = mutableListOf<Entry>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            entries += Entry(priority, tag, message)
        }

        fun single(): Entry {
            assertEquals("expected exactly one log entry", 1, entries.size)
            return entries.first()
        }

        fun clear() = entries.clear()
    }
}
