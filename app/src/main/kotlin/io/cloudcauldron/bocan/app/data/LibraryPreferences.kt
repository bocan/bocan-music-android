package io.cloudcauldron.bocan.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.cloudcauldron.bocan.app.library.LibraryTab
import io.cloudcauldron.bocan.persistence.model.AlbumSort
import io.cloudcauldron.bocan.persistence.model.TrackSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(name = "library_prefs")

/**
 * The preferences surface the view models depend on, so tests pass a fake instead of a
 * DataStore. Reads are reactive Flows; writes are suspend.
 */
interface LibraryPreferencesSource {
    val albumSort: Flow<AlbumSort>
    val trackSort: Flow<TrackSort>
    val lastTab: Flow<LibraryTab>
    val recentSearches: Flow<List<String>>

    suspend fun setAlbumSort(sort: AlbumSort)
    suspend fun setTrackSort(sort: TrackSort)
    suspend fun setLastTab(tab: LibraryTab)
    suspend fun addRecentSearch(query: String)
    suspend fun clearRecentSearches()
}

/**
 * DataStore-backed UI preferences: the selected library tab, the album and song sort
 * orders, and the last ten searches. The recent-searches list transform is pure
 * ([RecentSearches.updated]) so it is unit tested without a DataStore.
 */
class LibraryPreferences(private val context: Context) : LibraryPreferencesSource {
    override val albumSort: Flow<AlbumSort> = context.libraryDataStore.data.map { prefs ->
        AlbumSort.entries.firstOrNull { it.name == prefs[ALBUM_SORT] } ?: AlbumSort.Name
    }

    override val trackSort: Flow<TrackSort> = context.libraryDataStore.data.map { prefs ->
        TrackSort.entries.firstOrNull { it.name == prefs[TRACK_SORT] } ?: TrackSort.Title
    }

    override val lastTab: Flow<LibraryTab> = context.libraryDataStore.data.map { prefs ->
        LibraryTab.entries.firstOrNull { it.name == prefs[LAST_TAB] } ?: LibraryTab.Albums
    }

    override val recentSearches: Flow<List<String>> = context.libraryDataStore.data.map { prefs ->
        prefs[RECENT_SEARCHES]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
    }

    override suspend fun setAlbumSort(sort: AlbumSort) = edit { it[ALBUM_SORT] = sort.name }

    override suspend fun setTrackSort(sort: TrackSort) = edit { it[TRACK_SORT] = sort.name }

    override suspend fun setLastTab(tab: LibraryTab) = edit { it[LAST_TAB] = tab.name }

    override suspend fun addRecentSearch(query: String) = edit { prefs ->
        val current = prefs[RECENT_SEARCHES]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
        prefs[RECENT_SEARCHES] = RecentSearches.updated(current, query).joinToString(SEPARATOR)
    }

    override suspend fun clearRecentSearches() = edit { it.remove(RECENT_SEARCHES) }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.libraryDataStore.edit(block)
    }

    private companion object {
        val ALBUM_SORT = stringPreferencesKey("album_sort")
        val TRACK_SORT = stringPreferencesKey("track_sort")
        val LAST_TAB = stringPreferencesKey("last_tab")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        const val SEPARATOR = "\n"
    }
}

/** The pure recent-searches list policy: newest first, de-duplicated, capped. */
object RecentSearches {
    const val MAX = 10

    /** Prepend [query] (trimmed), remove any earlier duplicate (case-insensitive), cap at [MAX]. Blank queries are ignored. */
    fun updated(current: List<String>, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return current
        val withoutDup = current.filterNot { it.equals(trimmed, ignoreCase = true) }
        return (listOf(trimmed) + withoutDup).take(MAX)
    }
}
