package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import io.cloudcauldron.bocan.persistence.entities.PlayStatsEntity
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow

/** Writes and reads of the phone-owned play history. */
@Dao
interface PlayStatsDao {
    @Query("SELECT * FROM play_stats WHERE trackId = :trackId")
    fun observeStats(trackId: Long): Flow<PlayStatsEntity?>

    @Query("SELECT * FROM play_stats WHERE trackId = :trackId")
    suspend fun stats(trackId: Long): PlayStatsEntity?

    @Transaction
    suspend fun recordPlay(trackId: Long, playedSec: Long, at: Instant) {
        val current = stats(trackId) ?: PlayStatsEntity(trackId)
        upsert(
            current.copy(
                playCount = current.playCount + 1,
                lastPlayedAt = at,
                playDurationTotalSec = current.playDurationTotalSec + playedSec
            )
        )
    }

    @Transaction
    suspend fun recordSkip(trackId: Long, atSec: Int) {
        val current = stats(trackId) ?: PlayStatsEntity(trackId)
        upsert(
            current.copy(
                skipCount = current.skipCount + 1,
                skipAfterSeconds = atSec
            )
        )
    }

    /**
     * Maintenance only; nothing calls this yet. Removes stats for tracks that
     * left the sync set and have not been played since the cutoff.
     */
    suspend fun pruneOrphanedOlderThan(days: Long, now: Instant) {
        pruneOrphanedBefore(now.minus(days, ChronoUnit.DAYS))
    }

    @Query(
        """
        DELETE FROM play_stats
        WHERE trackId NOT IN (SELECT id FROM tracks)
        AND (lastPlayedAt IS NULL OR lastPlayedAt < :cutoff)
        """
    )
    suspend fun pruneOrphanedBefore(cutoff: Instant)

    @Upsert
    suspend fun upsert(stats: PlayStatsEntity)
}
