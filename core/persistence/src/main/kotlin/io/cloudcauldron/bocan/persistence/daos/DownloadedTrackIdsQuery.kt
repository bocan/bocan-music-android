package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Query

/**
 * The one definition of "every downloaded track id", shared by the DAOs that need it (the
 * phone's Shuffle library shortcut and the car's voice fallback) so the query is not copied.
 */
interface DownloadedTrackIdsQuery {
    /** Ids of every downloaded track, in id order. */
    @Query("SELECT id FROM tracks WHERE downloadState = 'downloaded' ORDER BY id")
    suspend fun downloadedTrackIds(): List<Long>
}
