package io.cloudcauldron.bocan.app.library

/**
 * The six library browsing tabs, in display order. The persisted last-selected tab
 * is stored by [name], so the order here is stable but the ordinal is not relied on.
 */
enum class LibraryTab {
    Artists,
    Albums,
    Songs,
    Genres,
    Playlists,
    Folders
}
