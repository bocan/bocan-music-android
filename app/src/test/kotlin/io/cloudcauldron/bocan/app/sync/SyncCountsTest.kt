package io.cloudcauldron.bocan.app.sync

import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import io.cloudcauldron.bocan.sync.SyncError
import io.cloudcauldron.bocan.sync.engine.SyncState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncCountsTest {
    private val stored = DownloadCounts(pending = 3, downloaded = 40, failed = 1)

    @Test
    fun `during a transfer the counts come from live progress, not the empty database`() {
        // The regression: mid first sync the database is not applied yet, so stored reads
        // zeros while files download. The line must reflect the transfer, not the zeros.
        val counts = displayedDownloadCounts(
            SyncState.Transferring(filesDone = 84, filesTotal = 105, bytesDone = 0, bytesTotal = 0, currentItem = ""),
            DownloadCounts(pending = 0, downloaded = 0, failed = 0)
        )
        assertEquals(84, counts.downloaded)
        assertEquals(21, counts.pending)
        assertEquals(0, counts.failed)
    }

    @Test
    fun `a completed transfer reports no pending`() {
        val counts = displayedDownloadCounts(
            SyncState.Transferring(filesDone = 105, filesTotal = 105, bytesDone = 0, bytesTotal = 0, currentItem = ""),
            DownloadCounts(0, 0, 0)
        )
        assertEquals(105, counts.downloaded)
        assertEquals(0, counts.pending)
    }

    @Test
    fun `pending never goes negative if progress overshoots the total`() {
        val counts = displayedDownloadCounts(
            SyncState.Transferring(filesDone = 110, filesTotal = 105, bytesDone = 0, bytesTotal = 0, currentItem = ""),
            DownloadCounts(0, 0, 0)
        )
        assertEquals(0, counts.pending)
    }

    @Test
    fun `at rest the stored library counts stand`() {
        assertEquals(stored, displayedDownloadCounts(SyncState.Idle, stored))
        assertEquals(stored, displayedDownloadCounts(SyncState.Applying("database"), stored))
        assertEquals(stored, displayedDownloadCounts(SyncState.ServerUnreachable, stored))
        assertEquals(
            stored,
            displayedDownloadCounts(SyncState.Done(Instant.EPOCH, downloaded = 40, deleted = 0, failures = emptyList()), stored)
        )
        assertEquals(stored, displayedDownloadCounts(SyncState.Failed(SyncError.NotPaired), stored))
    }
}
