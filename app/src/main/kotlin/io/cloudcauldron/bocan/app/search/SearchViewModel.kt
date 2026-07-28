package io.cloudcauldron.bocan.app.search

import io.cloudcauldron.bocan.app.data.LibraryPreferencesSource
import io.cloudcauldron.bocan.app.library.AlbumUi
import io.cloudcauldron.bocan.app.library.ArtistUi
import io.cloudcauldron.bocan.app.library.TrackUi
import io.cloudcauldron.bocan.app.library.toUi
import io.cloudcauldron.bocan.persistence.daos.SearchDao
import io.cloudcauldron.bocan.persistence.model.SearchResults
import io.cloudcauldron.bocan.sync.CoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Sectioned search results plus the current query and the recent-search history. */
data class SearchUiState(
    val query: String = "",
    /** The query the result sections were computed for; lags [query] while a search is in flight. */
    val resultsFor: String = "",
    val tracks: List<TrackUi> = emptyList(),
    val albums: List<AlbumUi> = emptyList(),
    val artists: List<ArtistUi> = emptyList(),
    val recent: List<String> = emptyList()
) {
    val hasQuery: Boolean get() = query.isNotBlank()

    /**
     * True only when the settled results for the current query came back empty.
     * While a search is still debouncing or querying, the sections are empty but
     * stale, and claiming "no results" then would flash on every keystroke.
     */
    val noResults: Boolean get() = hasQuery && resultsFor == query && tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

/**
 * FTS-backed search. The query debounces before hitting the DAO (which sanitises the
 * input against FTS operator injection), results are sectioned into tracks, albums,
 * and artists, and submitted queries are remembered as recent searches.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    searchDao: SearchDao,
    private val prefs: LibraryPreferencesSource,
    dispatchers: CoroutineDispatchers,
    debounceMs: Long = DEBOUNCE_MS
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val query = MutableStateFlow("")

    // Each result set is tagged with the query it answers, so the UI can tell
    // "settled and empty" apart from "still searching".
    private val results = query
        .debounce(debounceMs)
        .flatMapLatest { q ->
            val source = if (q.isBlank()) flowOf(SearchResults.EMPTY) else searchDao.search(q)
            source.map { q to it }
        }

    val state: StateFlow<SearchUiState> =
        combine(query, results, prefs.recentSearches) { q, (resultsFor, r), recent ->
            SearchUiState(
                query = q,
                resultsFor = resultsFor,
                tracks = r.tracks.map { it.toUi() },
                albums = r.albums.map { it.toUi() },
                artists = r.artists.map { ArtistUi(it.id, it.name, albumCount = 0, songCount = 0) },
                recent = recent
            )
        }.stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), SearchUiState())

    fun onQueryChange(next: String) {
        query.value = next
    }

    fun onSubmit() {
        val submitted = query.value
        scope.launch { prefs.addRecentSearch(submitted) }
    }

    fun clearRecent() = scope.launch { prefs.clearRecentSearches() }.let {}

    fun dispose() = scope.cancel()

    private companion object {
        const val DEBOUNCE_MS = 200L
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
    }
}
