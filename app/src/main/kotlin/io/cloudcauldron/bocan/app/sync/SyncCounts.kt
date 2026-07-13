package io.cloudcauldron.bocan.app.sync

import io.cloudcauldron.bocan.persistence.model.DownloadCounts
import io.cloudcauldron.bocan.sync.engine.SyncState

/**
 * The download counts to show under the sync status.
 *
 * The stored counts come from the database, but the engine applies the database only when a
 * sync pass completes (files are transferred first, so the library never lists audio that is
 * not yet on disk). That leaves a window during a transfer where files are visibly
 * downloading while the stored counts still read all zeros, which reads as "0 downloaded, 0
 * pending" next to a "Downloading 84 of 105" line and confuses the user.
 *
 * So while a transfer is in flight, derive the line from the live progress instead: files
 * done are downloaded, the remainder of the queue is pending, and failures are not known
 * until the run finishes. At rest (idle, applying, done, failed, unreachable) the stored
 * library counts stand, since those are the real per track states after a pass applied.
 */
fun displayedDownloadCounts(sync: SyncState, stored: DownloadCounts): DownloadCounts = when (sync) {
    is SyncState.Transferring -> DownloadCounts(
        pending = (sync.filesTotal - sync.filesDone).coerceAtLeast(0),
        downloaded = sync.filesDone,
        failed = 0
    )
    else -> stored
}
