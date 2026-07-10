package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.cloudcauldron.bocan.persistence.FIXED_NOW
import io.cloudcauldron.bocan.persistence.entities.ScrobbleQueueEntity
import io.cloudcauldron.bocan.persistence.runDbTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ScrobbleDaoTests {
    private fun entry(payload: String) = ScrobbleQueueEntity(provider = "lastfm", payloadJson = payload)

    @Test
    fun `due returns queued entries oldest first and honours backoff`() = runDbTest { db ->
        val dao = db.scrobbleDao()
        val first = dao.enqueue(entry("{\"n\":1}"))
        dao.enqueue(entry("{\"n\":2}"))
        val deferred = dao.enqueue(entry("{\"n\":3}"))
        dao.recordAttempt(deferred, attempts = 1, nextAttemptAt = FIXED_NOW.plusSeconds(600), deadLettered = false)

        val due = dao.due(now = FIXED_NOW, limit = 10)
        assertEquals(2, due.size)
        assertEquals(first, due.first().id)
        assertTrue(due.none { it.id == deferred })
    }

    @Test
    fun `dead-lettered entries never come due and leave the queue size`() = runDbTest { db ->
        val dao = db.scrobbleDao()
        val id = dao.enqueue(entry("{}"))
        dao.observeQueueSize().test {
            assertEquals(1, awaitItem())

            dao.recordAttempt(id, attempts = 5, nextAttemptAt = null, deadLettered = true)

            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(dao.due(now = FIXED_NOW, limit = 10).isEmpty())
    }

    @Test
    fun `delete removes sent entries`() = runDbTest { db ->
        val dao = db.scrobbleDao()
        val a = dao.enqueue(entry("{}"))
        val b = dao.enqueue(entry("{}"))
        dao.delete(listOf(a, b))
        assertTrue(dao.due(now = FIXED_NOW, limit = 10).isEmpty())
    }
}
