package io.cloudcauldron.bocan.persistence.model.manifest

import kotlinx.serialization.Serializable

/**
 * One playlist. Smart playlists arrive pre-evaluated as plain ordered id
 * lists; folders have empty trackIds and exist for hierarchy.
 */
@Serializable
data class ManifestPlaylist(
    val id: Long,
    val name: String,
    val kind: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
    val accentColor: String? = null,
    val artworkHash: String? = null,
    val trackIds: List<Long> = emptyList()
)
