package dev.dhuelin.watchguru.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.AddToWatchlist
import dev.dhuelin.watchguru.api.models.SearchHit
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
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

    /** Provider ids added during this session, so rows can show as added. */
    private val _added = MutableStateFlow<Set<Long>>(emptySet())
    val added: StateFlow<Set<Long>> = _added.asStateFlow()

    init {
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

    fun retry() {
        viewModelScope.launch { runSearch(_query.value.trim()) }
    }

    /**
     * Adds a hit to the library straight from the results list.
     *
     * The backend imports the title from the provider as part of this call, so
     * there is nothing to do first.
     */
    fun addToLibrary(hit: SearchHit) {
        viewModelScope.launch {
            val request = AddToWatchlist(
                providerId = hit.providerId,
                titleType = AddToWatchlist.TitleType.valueOf(hit.titleType.value),
            )
            if (repository.addToLibrary(request) is ApiResult.Success) {
                _added.value = _added.value + hit.providerId
            }
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
