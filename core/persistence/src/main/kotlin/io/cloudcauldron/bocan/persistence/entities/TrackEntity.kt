package io.cloudcauldron.bocan.persistence.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.cloudcauldron.bocan.persistence.model.DownloadState
import java.time.Instant

/**
 * A synced track, mirroring the manifest's Track object. The id is the Mac's
 * stable track id; identity is id, change detection is sha256. Rows in this
 * table are replaceable at any time from a manifest.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index("albumId"),
        Index("artistId"),
        Index("genre"),
        Index("downloadState"),
        Index("sha256")
    ]
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artistId: Long,
    val artistName: String,
    val albumArtistId: Long,
    val albumArtistName: String,
    val albumId: Long,
    val albumName: String,
    val trackNumber: Int?,
    val trackTotal: Int?,
    val discNumber: Int?,
    val discTotal: Int?,
    val year: Int?,
    val genre: String?,
    val composer: String?,
    val bpm: Int?,
    val durationMs: Long,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val bitrate: Int?,
    val channelCount: Int?,
    val isLossless: Boolean,
    val format: String,
    val size: Long,
    val sha256: String,
    val relPath: String,
    val artworkHash: String?,
    val lyricsHash: String?,
    val rating: Int,
    val loved: Boolean,
    val rgTrackGain: Double?,
    val rgTrackPeak: Double?,
    val rgAlbumGain: Double?,
    val rgAlbumPeak: Double?,
    val clipSourceTrackId: Long?,
    val clipStartMs: Long?,
    val clipEndMs: Long?,
    val downloadState: DownloadState,
    val syncedAt: Instant
)
