package io.cloudcauldron.bocan.persistence.daos

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.cloudcauldron.bocan.persistence.FIXED_NOW
import io.cloudcauldron.bocan.persistence.entities.LyricsCacheEntity
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import io.cloudcauldron.bocan.persistence.runDbTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LyricsDaoTests {
    private fun entity(trackId: Long, hash: String = "h$trackId") =
        LyricsCacheEntity(trackId, hash, LyricsKind.Synced, "[00:01.00]line", FIXED_NOW)

    @Test
    fun `upsert then get round-trips and a second upsert replaces`() = runDbTest { db ->
        val dao = db.lyricsDao()
        dao.upsert(entity(1))
        assertEquals("h1", dao.get(1)?.lyricsHash)

        dao.upsert(entity(1, hash = "h1-new"))
        assertEquals("h1-new", dao.get(1)?.lyricsHash)
    }

    @Test
    fun `delete removes only the named track`() = runDbTest { db ->
        val dao = db.lyricsDao()
        dao.upsert(entity(1))
        dao.upsert(entity(2))

        dao.delete(1)

        assertNull(dao.get(1))
        assertEquals("h2", dao.get(2)?.lyricsHash)
    }

    @Test
    fun `delete of a missing row is a no-op`() = runDbTest { db ->
        db.lyricsDao().delete(99)
        assertNull(db.lyricsDao().get(99))
    }
}
