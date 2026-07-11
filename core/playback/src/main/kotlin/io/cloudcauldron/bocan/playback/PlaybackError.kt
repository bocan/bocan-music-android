package io.cloudcauldron.bocan.playback

/**
 * The one error hierarchy for the playback module. Every failure that crosses a
 * module boundary is one of these cases, each carrying enough context to log and
 * to map to a user-facing string resource in :app. The message text here is for
 * logs and developers, never shown directly: UI copy lives in strings.xml.
 */
sealed class PlaybackError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The player could not be constructed (renderer or audio sink init failed). */
    data class PlayerInitFailed(val reason: Throwable) : PlaybackError("player init failed", reason)

    /** A media id in a queue snapshot or transport call was not track:<id> or episode:<id>. */
    data class UnknownMediaId(val mediaId: String) : PlaybackError("unrecognised media id: $mediaId")

    /** The item could not be prepared or decoded (no renderer, missing file, corrupt data). */
    data class ItemUnplayable(val mediaId: String, val reason: Throwable? = null) :
        PlaybackError("item unplayable: $mediaId", reason)

    /** External storage is unmounted, so the media file could not be resolved. */
    data object MediaUnavailable : PlaybackError("external media storage is unavailable")

    /** Reading or writing the persisted queue snapshot failed. */
    data class QueuePersistenceFailed(val reason: Throwable) : PlaybackError("queue persistence failed", reason)
}
