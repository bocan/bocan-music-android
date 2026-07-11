package io.cloudcauldron.bocan.persistence.daos

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import io.cloudcauldron.bocan.persistence.entities.LyricsCacheEntity

/** Reads and writes of the phone-owned lyrics cache, keyed by trackId and lyricsHash. */
@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId")
    suspend fun get(trackId: Long): LyricsCacheEntity?

    @Upsert
    suspend fun upsert(entity: LyricsCacheEntity)
}
