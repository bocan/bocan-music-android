package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import io.cloudcauldron.bocan.persistence.model.LyricsKind
import java.time.Instant

/** Phone-owned cache of fetched lyrics, keyed by the manifest's lyricsHash. */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackId: Long,
    val lyricsHash: String,
    val kind: LyricsKind,
    val text: String,
    val fetchedAt: Instant
)
