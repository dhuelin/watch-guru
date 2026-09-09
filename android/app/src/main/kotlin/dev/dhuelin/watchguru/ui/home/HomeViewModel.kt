package dev.dhuelin.watchguru.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.UpNextResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
) : ViewModel() {

    private val _upNext = MutableStateFlow<UiState<List<UpNextResponse>>>(UiState.Loading)
    val upNext: StateFlow<UiState<List<UpNextResponse>>> = _upNext.asStateFlow()

    /** Title ids with a mark in flight, so one card can be busy without the rest. */
    private val _marking = MutableStateFlow<Set<Long>>(emptySet())
    val marking: StateFlow<Set<Long>> = _marking.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _upNext.value = when (val current = _upNext.value) {
                is UiState.Content -> UiState.Refreshing(current.value)
                else -> UiState.Loading
            }
            _upNext.value = when (val result = repository.upNext()) {
                is ApiResult.Success ->
                    if (result.value.isEmpty()) UiState.Empty else UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    /**
     * Marks the shown episode watched and advances the card.
     *
     * The whole point of this screen: record an episode without navigating
     * anywhere. Progress is re-read afterwards rather than guessed, because the
     * server decides what "next" is and the card must not claim otherwise.
     */
    fun markWatched(entry: UpNextResponse) {
        if (entry.titleId in _marking.value) return

        viewModelScope.launch {
            _marking.value = _marking.value + entry.titleId
            val result = repository.markEpisodeWatched(
                dev.dhuelin.watchguru.api.models.LogEpisodeWatched(episodeId = entry.nextEpisodeId)
            )
            if (result is ApiResult.Success) {
                refresh()
            }
            _marking.value = _marking.value - entry.titleId
        }
    }
}
