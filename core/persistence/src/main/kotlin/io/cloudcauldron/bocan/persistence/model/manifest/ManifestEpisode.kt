package io.cloudcauldron.bocan.persistence.model.manifest

import kotlinx.serialization.Serializable

/**
 * One downloaded episode. playPositionMs and playState are the Mac's values
 * at manifest time and only ever seed episodes this phone has never played.
 */
@Serializable
data class ManifestEpisode(
    val id: String,
    val podcastId: Long,
    val guid: String,
    val title: String,
    val publishedAt: String,
    val durationMs: Long,
    val descriptionHtml: String? = null,
    val relPath: String,
    val size: Long,
    val sha256: String,
    val hasChapters: Boolean = false,
    val playPositionMs: Long = 0,
    val playState: String = "unplayed"
)
