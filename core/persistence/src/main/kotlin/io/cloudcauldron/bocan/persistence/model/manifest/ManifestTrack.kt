package io.cloudcauldron.bocan.persistence.model.manifest

import kotlinx.serialization.Serializable

/**
 * One track in the manifest. Identity is id, change detection is sha256.
 * Only the fields the contract lists as always present are non-null here:
 * the Mac omits every other key when it has no value (an untagged file), and
 * a missing key must never fail the sync.
 */
@Serializable
data class ManifestTrack(
    val id: Long,
    val relPath: String,
    val size: Long,
    val sha256: String,
    val format: String,
    val durationMs: Long,
    val title: String? = null,
    val artist: String? = null,
    val artistId: Long? = null,
    val albumArtist: String? = null,
    val albumArtistId: Long? = null,
    val album: String? = null,
    val albumId: Long? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val composer: String? = null,
    val bpm: Double? = null,
    val rating: Int = 0,
    val loved: Boolean = false,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val bitrate: Int? = null,
    val channelCount: Int? = null,
    val isLossless: Boolean = false,
    val replayGain: ManifestReplayGain? = null,
    val artworkHash: String? = null,
    val lyricsHash: String? = null,
    val clip: ManifestClip? = null
)

/** ReplayGain values as scanned by the Mac. */
@Serializable
data class ManifestReplayGain(
    val trackGain: Double? = null,
    val trackPeak: Double? = null,
    val albumGain: Double? = null,
    val albumPeak: Double? = null
)

/**
 * A CUE virtual track: the audio bytes belong to the source track's file, and
 * a clipped track is never downloaded on its own.
 */
@Serializable
data class ManifestClip(val sourceTrackId: Long, val startMs: Long, val endMs: Long)
