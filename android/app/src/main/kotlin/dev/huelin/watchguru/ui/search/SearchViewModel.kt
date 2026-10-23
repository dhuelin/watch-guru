package dev.huelin.watchguru.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.huelin.watchguru.api.apis.TitleControllerApi
import dev.huelin.watchguru.api.models.AddToWatchlist
import dev.huelin.watchguru.api.models.SearchHit
import dev.huelin.watchguru.api.models.WatchlistItemResponse
import dev.huelin.watchguru.data.ApiResult
import dev.huelin.watchguru.data.WatchGuruRepository
import dev.huelin.watchguru.ui.components.UiState
import dev.huelin.watchguru.ui.navigation.TitleId
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The minimum the backend will act on; shorter queries come back empty. */
private const val MIN_QUERY_LENGTH = 2
private const val DEBOUNCE_MS = 300L

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<SearchHit>>>(UiState.Empty)
    val results: StateFlow<UiState<List<SearchHit>>> = _results.asStateFlow()

    /**
     * What people are watching this week, shown while the box is empty.
     *
     * Held apart from [results] rather than loaded into it: coming back to an
     * empty box should not cost a request, and a search that found nothing must
     * still read as "nothing found" rather than silently becoming a chart.
     */
    private val _trending = MutableStateFlow<UiState<List<SearchHit>>>(UiState.Loading)
    val trending: StateFlow<UiState<List<SearchHit>>> = _trending.asStateFlow()

    /** Whether the box holds enough to have searched for anything. */
    val isSearching: StateFlow<Boolean> = _query
        .map { it.trim().length >= MIN_QUERY_LENGTH }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Provider ids added during this session, with the status the server gave
     * them, so a row can say "Watchlist" rather than just looking pressed.
     */
    private val _added = MutableStateFlow<Map<Long, WatchlistItemResponse.Status>>(emptyMap())
    val added: StateFlow<Map<Long, WatchlistItemResponse.Status>> = _added.asStateFlow()

    /** Provider ids with an add in flight, so a double tap cannot add twice. */
    private val _adding = MutableStateFlow<Set<Long>>(emptySet())
    val adding: StateFlow<Set<Long>> = _adding.asStateFlow()

    /** The provider id of the result being opened, while its import runs. */
    private val _opening = MutableStateFlow<Long?>(null)
    val opening: StateFlow<Long?> = _opening.asStateFlow()

    /**
     * A failure to tell the user about once, then forget: an add or an open
     * that did not work. The list stays on screen, so it is a snackbar rather
     * than an error state.
     */
    private val _actionFailure = MutableStateFlow<ApiResult.Failure?>(null)
    val actionFailure: StateFlow<ApiResult.Failure?> = _actionFailure.asStateFlow()

    /**
     * Titles ready to open, by their internal id.
     *
     * A channel rather than state: navigating is something that happens once,
     * and a state would navigate again every time the screen came back.
     */
    private val _openTitle = Channel<TitleId>(Channel.BUFFERED)
    val openTitle: Flow<TitleId> = _openTitle.receiveAsFlow()

    init {
        loadTrending()

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _query
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                // collectLatest, so a slow response for "brea" is cancelled the
                // moment "break" arrives and cannot overwrite newer results.
                .collectLatest { text -> runSearch(text.trim()) }
        }
    }

    fun onQueryChanged(text: String) {
        _query.value = text
    }

    fun clearQuery() {
        _query.value = ""
    }

    fun dismissActionFailure() {
        _actionFailure.value = null
    }

    /**
     * Opens a result, which first means making sure the backend has it.
     *
     * A search hit carries the provider's id, not the backend's, and the two
     * are not interchangeable: passing one as the other opened a 404, or a
     * different title once the numbers collide. Importing is idempotent, so
     * this works whether or not the title is already in the catalogue, and the
     * id that comes back is the only one handed to navigation.
     */
    fun open(hit: SearchHit) {
        if (_opening.value != null) return

        viewModelScope.launch {
            _opening.value = hit.providerId
            val type = TitleControllerApi.TitleTypeImportTitle.valueOf(hit.titleType.value)
            when (val result = repository.importTitle(type, hit.providerId)) {
                is ApiResult.Success -> _openTitle.send(TitleId(result.value.id))
                is ApiResult.Failure -> _actionFailure.value = result
            }
            _opening.value = null
        }
    }

    fun retry() {
        viewModelScope.launch {
            if (_query.value.trim().length >= MIN_QUERY_LENGTH) runSearch(_query.value.trim()) else loadTrending()
        }
    }

    private fun loadTrending() {
        viewModelScope.launch {
            _trending.value = UiState.Loading
            _trending.value = when (val result = repository.trending()) {
                is ApiResult.Success ->
                    result.value.results.let { if (it.isEmpty()) UiState.Empty else UiState.Content(it) }
                // Shown as an error rather than as an empty shelf: the screen
                // has nothing else on it, and "nothing is trending this week"
                // is a claim about the world rather than about the network.
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    /**
     * Adds a hit to the library straight from the results list.
     *
     * The backend imports the title from the provider as part of this call, so
     * there is nothing to do first.
     */
    fun addToLibrary(hit: SearchHit) {
        if (hit.providerId in _adding.value || hit.providerId in _added.value) return

        viewModelScope.launch {
            _adding.value = _adding.value + hit.providerId
            val request = AddToWatchlist(
                providerId = hit.providerId,
                titleType = AddToWatchlist.TitleType.valueOf(hit.titleType.value),
            )
            when (val result = repository.addToLibrary(request)) {
                is ApiResult.Success ->
                    _added.value = _added.value + (hit.providerId to result.value.status)
                // Said out loud: before, a failed add just left the + button
                // there, which reads as "nothing happened" and invites tapping
                // until something does.
                is ApiResult.Failure -> _actionFailure.value = result
            }
            _adding.value = _adding.value - hit.providerId
        }
    }

    private suspend fun runSearch(text: String) {
        if (text.length < MIN_QUERY_LENGTH) {
            // Not an error: an empty search box is the screen's resting state,
            // and one character is on the way to a real query.
            _results.value = UiState.Empty
            return
        }

        _results.value = when (val current = _results.value) {
            is UiState.Content -> UiState.Refreshing(current.value)
            else -> UiState.Loading
        }

        _results.value = when (val result = repository.search(text)) {
            is ApiResult.Success ->
                result.value.results.let { if (it.isEmpty()) UiState.Empty else UiState.Content(it) }
            is ApiResult.Failure -> UiState.Error(result)
        }
    }
}
