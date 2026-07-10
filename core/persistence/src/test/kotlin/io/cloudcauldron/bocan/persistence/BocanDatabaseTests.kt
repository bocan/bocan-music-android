package io.cloudcauldron.bocan.persistence

import android.content.Context
import androidx.room3.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BocanDatabaseTests {
    @Test
    fun `production database opens at version 1 with WAL in effect`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = BocanDatabase.create(context, StandardTestDispatcher(testScheduler))
        try {
            val (journalMode, userVersion) = db.useReaderConnection { connection ->
                val mode = connection.usePrepared("PRAGMA journal_mode") { statement ->
                    statement.step()
                    statement.getText(0)
                }
                val version = connection.usePrepared("PRAGMA user_version") { statement ->
                    statement.step()
                    statement.getLong(0)
                }
                mode to version
            }
            assertEquals("wal", journalMode)
            assertEquals(1L, userVersion)
        } finally {
            db.close()
            context.deleteDatabase(BocanDatabase.FILE_NAME)
        }
    }

    @Test
    fun `in-memory database opens and answers queries`() = runDbTest { db ->
        assertEquals(0, db.syncDao().allTracks().size)
    }
}
