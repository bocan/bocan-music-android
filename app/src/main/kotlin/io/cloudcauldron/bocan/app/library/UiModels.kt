package io.cloudcauldron.bocan.app.library

import android.text.format.DateUtils
import io.cloudcauldron.bocan.persistence.entities.AlbumEntity
import io.cloudcauldron.bocan.persistence.entities.TrackEntity
import io.cloudcauldron.bocan.persistence.model.DownloadState

/** A track as the list rows render it, decoupled from the Room entity and pre-formatted. */
data class TrackUi(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtistId: Long,
    val durationLabel: String,
    val durationMs: Long,
    val loved: Boolean,
    val rating: Int,
    val artworkHash: String?,
    val pending: Boolean
)

/** An album as the grid cells render it. */
data class AlbumUi(val id: Long, val name: String, val artist: String, val year: Int?, val artworkHash: String?, val trackCount: Int)

/** An artist as the list rows render it, with a derived album count. */
data class ArtistUi(val id: Long, val name: String, val albumCount: Int)

/** Format a millisecond duration as m:ss or h:mm:ss, never by string concatenation. */
fun formatDuration(durationMs: Long): String = DateUtils.formatElapsedTime(durationMs / MILLIS_PER_SECOND)

fun TrackEntity.toUi(): TrackUi = TrackUi(
    id = id,
    title = title,
    artist = artistName,
    album = albumName,
    albumId = albumId,
    albumArtistId = albumArtistId,
    durationLabel = formatDuration(durationMs),
    durationMs = durationMs,
    loved = loved,
    rating = rating,
    artworkHash = artworkHash,
    pending = downloadState != DownloadState.Downloaded
)

fun AlbumEntity.toUi(): AlbumUi = AlbumUi(
    id = id,
    name = name,
    artist = albumArtistName,
    year = year,
    artworkHash = artworkHash,
    trackCount = trackCount
)

private const val MILLIS_PER_SECOND = 1000L
