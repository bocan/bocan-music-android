package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.time.Instant

/** The single paired Mac. At most one row exists at a time. */
@Entity(tableName = "sync_server")
data class SyncServerEntity(
    @PrimaryKey val serverId: String,
    val serverName: String,
    val certFingerprint: String,
    val certDer: ByteArray,
    val lastAppliedGeneration: Long,
    val lastSyncAt: Instant?,
    val pairedAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncServerEntity) return false
        return serverId == other.serverId &&
            serverName == other.serverName &&
            certFingerprint == other.certFingerprint &&
            certDer.contentEquals(other.certDer) &&
            lastAppliedGeneration == other.lastAppliedGeneration &&
            lastSyncAt == other.lastSyncAt &&
            pairedAt == other.pairedAt
    }

    override fun hashCode(): Int {
        var result = serverId.hashCode()
        result = 31 * result + serverName.hashCode()
        result = 31 * result + certFingerprint.hashCode()
        result = 31 * result + certDer.contentHashCode()
        result = 31 * result + lastAppliedGeneration.hashCode()
        result = 31 * result + (lastSyncAt?.hashCode() ?: 0)
        result = 31 * result + pairedAt.hashCode()
        return result
    }
}
