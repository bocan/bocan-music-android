package io.cloudcauldron.bocan.app.library

/**
 * The navigation and playback callbacks the library surfaces raise, gathered so
 * screens take one parameter instead of a dozen. Playback verbs delegate to the
 * player; navigation verbs push detail routes. There are no write verbs: the library
 * is read-only.
 */
@Suppress("LongParameterList")
class LibraryCallbacks(
    val openAlbum: (Long) -> Unit = {},
    val openArtist: (Long) -> Unit = {},
    val openPlaylist: (Long) -> Unit = {},
    val openGenre: (String) -> Unit = {},
    val playContext: (trackIds: List<Long>, index: Int) -> Unit = { _, _ -> },
    val shuffle: (List<Long>) -> Unit = {},
    val playNext: (List<Long>) -> Unit = {},
    val addToQueue: (List<Long>) -> Unit = {},
    val explainPending: () -> Unit = {}
)
