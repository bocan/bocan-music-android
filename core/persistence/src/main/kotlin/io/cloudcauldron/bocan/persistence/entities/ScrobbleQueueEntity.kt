package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.time.Instant

/** Phone-owned scrobble outbox. Schema only in phase 01; phase 09 drives it. */
@Entity(tableName = "scrobble_queue")
data class ScrobbleQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val payloadJson: String,
    val attempts: Int = 0,
    val nextAttemptAt: Instant? = null,
    val deadLettered: Boolean = false
)
