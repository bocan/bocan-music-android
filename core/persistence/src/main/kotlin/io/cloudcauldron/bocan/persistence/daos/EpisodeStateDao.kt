package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import io.cloudcauldron.bocan.persistence.entities.EpisodeStateEntity
import io.cloudcauldron.bocan.persistence.model.PlayState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Writes and reads of the phone-owned episode listening progress. */
@Dao
interface EpisodeStateDao {
    @Query("SELECT * FROM episode_state WHERE episodeId = :episodeId")
    fun observeState(episodeId: String): Flow<EpisodeStateEntity?>

    @Query("SELECT * FROM episode_state WHERE episodeId = :episodeId")
    suspend fun state(episodeId: String): EpisodeStateEntity?

    @Transaction
    suspend fun updatePosition(episodeId: String, ms: Long, at: Instant) {
        val current = state(episodeId) ?: EpisodeStateEntity(episodeId)
        upsert(
            current.copy(
                playPositionMs = ms,
                playState = PlayState.InProgress,
                lastPlayedAt = at
            )
        )
    }

    @Transaction
    suspend fun markPlayed(episodeId: String, at: Instant) {
        val current = state(episodeId) ?: EpisodeStateEntity(episodeId)
        upsert(
            current.copy(
                playState = PlayState.Played,
                lastPlayedAt = at,
                completedAt = at
            )
        )
    }

    @Query("UPDATE episode_state SET speedOverride = :speed WHERE episodeId = :episodeId")
    suspend fun setSpeedOverride(episodeId: String, speed: Double?)

    @Upsert
    suspend fun upsert(state: EpisodeStateEntity)
}
