package dev.dhuelin.watchguru.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.apis.WatchlistControllerApi
import dev.dhuelin.watchguru.api.models.WatchlistItemResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow<WatchlistControllerApi.StatusListWatchlist?>(null)
    val filter: StateFlow<WatchlistControllerApi.StatusListWatchlist?> = _filter.asStateFlow()

    private val _items = MutableStateFlow<UiState<List<WatchlistItemResponse>>>(UiState.Loading)
    val items: StateFlow<UiState<List<WatchlistItemResponse>>> = _items.asStateFlow()

    init {
        refresh()
    }

    fun setFilter(status: WatchlistControllerApi.StatusListWatchlist?) {
        if (_filter.value == status) return
        _filter.value = status
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Keep showing what is already on screen while the refresh runs.
            // Replacing a populated library with a spinner every time the user
            // returns to the tab is worse than briefly stale rows.
            _items.value = when (val current = _items.value) {
                is UiState.Content -> UiState.Refreshing(current.value)
                else -> UiState.Loading
            }

            _items.value = when (val result = repository.library(status = _filter.value)) {
                is ApiResult.Success ->
                    if (result.value.isEmpty()) UiState.Empty else UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    /**
     * Removes an item, optimistically.
     *
     * The row disappears immediately and comes back if the call fails, which is
     * what makes the undo snackbar honest: the user sees the outcome they asked
     * for, and a failure restores exactly what was there.
     */
    fun remove(item: WatchlistItemResponse) {
        val before = (_items.value as? UiState.Content)?.value ?: return
        _items.value = UiState.Content(before.filterNot { it.id == item.id })

        viewModelScope.launch {
            if (repository.removeFromLibrary(item.id) !is ApiResult.Success) {
                _items.value = UiState.Content(before)
            }
        }
    }
}
