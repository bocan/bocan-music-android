package io.cloudcauldron.bocan.playback.podcast

/**
 * One Podcasting 2.0 chapter. [startTimeMs] is inclusive; [endTimeMs] is exclusive when
 * present (otherwise the chapter runs until the next chapter's start). [imageUrl] and
 * [url] are optional per the spec.
 */
data class Chapter(
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val imageUrl: String? = null,
    val url: String? = null
)
