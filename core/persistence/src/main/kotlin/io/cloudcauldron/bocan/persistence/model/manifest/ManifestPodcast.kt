package io.cloudcauldron.bocan.persistence.model.manifest

import kotlinx.serialization.Serializable

/** One podcast show. playbackSpeed is the Mac's per-show override. */
@Serializable
data class ManifestPodcast(
    val id: Long,
    val title: String,
    val author: String? = null,
    val descriptionHtml: String? = null,
    val artworkHash: String? = null,
    val playbackSpeed: Double? = null
)
