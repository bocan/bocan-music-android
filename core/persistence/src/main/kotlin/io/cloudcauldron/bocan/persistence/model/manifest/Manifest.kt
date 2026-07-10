package io.cloudcauldron.bocan.persistence.model.manifest

import kotlinx.serialization.Serializable

/**
 * The full sync set as served by GET /v1/manifest. Field names match
 * docs/design-spec/sync-protocol.md section 7 exactly; both this client and
 * the Mac server ignore unknown fields (additive protocol changes need no
 * version bump).
 */
@Serializable
data class Manifest(
    val protocolVersion: Int,
    val serverId: String,
    val serverName: String,
    val generation: Long,
    val generatedAt: String,
    val tracks: List<ManifestTrack> = emptyList(),
    val playlists: List<ManifestPlaylist> = emptyList(),
    val podcasts: List<ManifestPodcast> = emptyList(),
    val episodes: List<ManifestEpisode> = emptyList()
)
