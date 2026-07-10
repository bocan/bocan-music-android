package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.time.Instant

/**
 * Phone-owned play history for a track. Rows survive every sync, including the
 * track leaving the sync set; rejoining keeps the history.
 */
@Entity(tableName = "play_stats")
data class PlayStatsEntity(
    @PrimaryKey val trackId: Long,
    val playCount: Long = 0,
    val skipCount: Long = 0,
    val lastPlayedAt: Instant? = null,
    val playDurationTotalSec: Long = 0,
    val skipAfterSeconds: Int? = null
)
