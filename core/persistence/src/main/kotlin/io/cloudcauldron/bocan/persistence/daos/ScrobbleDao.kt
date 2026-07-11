package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import io.cloudcauldron.bocan.persistence.entities.ScrobbleQueueEntity
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The scrobble outbox. Phase 09 drives it; the schema and basic ops land here. */
@Dao
interface ScrobbleDao {
    @Insert
    suspend fun enqueue(entry: ScrobbleQueueEntity): Long

    @Query(
        """
        SELECT * FROM scrobble_queue
        WHERE deadLettered = 0 AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now)
        ORDER BY id
        LIMIT :limit
        """
    )
    suspend fun due(now: Instant, limit: Int): List<ScrobbleQueueEntity>

    /** Live, non-dead rows for one provider; phase 09 reads these to dedup before enqueue. */
    @Query("SELECT * FROM scrobble_queue WHERE provider = :provider AND deadLettered = 0 ORDER BY id")
    suspend fun activeForProvider(provider: String): List<ScrobbleQueueEntity>

    /** The dead-letter list surfaced in settings with retry and discard actions. */
    @Query("SELECT * FROM scrobble_queue WHERE deadLettered = 1 ORDER BY id")
    fun observeDeadLettered(): Flow<List<ScrobbleQueueEntity>>

    @Query("UPDATE scrobble_queue SET attempts = :attempts, nextAttemptAt = :nextAttemptAt, deadLettered = :deadLettered WHERE id = :id")
    suspend fun recordAttempt(id: Long, attempts: Int, nextAttemptAt: Instant?, deadLettered: Boolean)

    @Query("DELETE FROM scrobble_queue WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM scrobble_queue WHERE deadLettered = 0")
    fun observeQueueSize(): Flow<Int>
}
