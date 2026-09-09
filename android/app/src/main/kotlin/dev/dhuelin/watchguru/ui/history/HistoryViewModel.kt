package dev.dhuelin.watchguru.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.WatchEventResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The user's viewing history, and the place mistakes get corrected.
 *
 * Backed by the append-only watch_event log, which is the real record of what
 * happened -- the watchlist and per-episode state are derived from it.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
) : ViewModel() {

    private val _events = MutableStateFlow<UiState<List<WatchEventResponse>>>(UiState.Loading)
    val events: StateFlow<UiState<List<WatchEventResponse>>> = _events.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _events.value = when (val current = _events.value) {
                is UiState.Content -> UiState.Refreshing(current.value)
                else -> UiState.Loading
            }
            _events.value = when (val result = repository.history()) {
                is ApiResult.Success ->
                    if (result.value.isEmpty()) UiState.Empty else UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    /**
     * Deletes one entry, optimistically.
     *
     * The row goes immediately and comes back if the call fails. Deleting the
     * last entry for an episode also makes that episode unwatched again --
     * handled server-side, so a refresh is what surfaces it rather than
     * anything guessed here.
     */
    fun delete(event: WatchEventResponse) {
        val before = (_events.value as? UiState.Content)?.value ?: return
        _events.value = UiState.Content(before.filterNot { it.id == event.id })

        viewModelScope.launch {
            if (repository.deleteWatchEvent(event.id) is ApiResult.Success) {
                // Re-read: removing an event can change derived state the list
                // does not show, and a stale list would disagree with the
                // library screen.
                refresh()
            } else {
                _events.value = UiState.Content(before)
            }
        }
    }
}
