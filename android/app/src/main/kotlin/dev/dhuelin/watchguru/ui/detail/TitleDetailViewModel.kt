package dev.dhuelin.watchguru.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.LogEpisodeWatched
import dev.dhuelin.watchguru.api.models.TitleProgress
import dev.dhuelin.watchguru.api.models.TitleResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TitleDetailViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val titleId: Long = checkNotNull(savedStateHandle[Routes.ARG_TITLE_ID])

    private val _title = MutableStateFlow<UiState<TitleResponse>>(UiState.Loading)
    val title: StateFlow<UiState<TitleResponse>> = _title.asStateFlow()

    /** Null for films, and for series the user has not started. */
    private val _progress = MutableStateFlow<TitleProgress?>(null)
    val progress: StateFlow<TitleProgress?> = _progress.asStateFlow()

    private val _marking = MutableStateFlow(false)
    val marking: StateFlow<Boolean> = _marking.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _title.value = when (val result = repository.title(titleId)) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
            refreshProgress()
        }
    }

    /**
     * Records the next unwatched episode as watched.
     *
     * This is the interaction the product is judged on, so it has to feel
     * certain: the button reports itself busy immediately and progress is
     * re-read from the server afterwards rather than guessed at, because the
     * server owns what "next" means and the two must not disagree.
     */
    fun markNextEpisodeWatched() {
        val next = _progress.value?.nextEpisodeId ?: return
        if (_marking.value) return

        viewModelScope.launch {
            _marking.value = true
            val result = repository.markEpisodeWatched(LogEpisodeWatched(episodeId = next))
            if (result is ApiResult.Success) {
                refreshProgress()
            }
            _marking.value = false
        }
    }

    private suspend fun refreshProgress() {
        // A film has no episode progress, and the endpoint says so with a 404
        // rather than an error worth showing anyone.
        _progress.value = when (val result = repository.progress(titleId)) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> null
        }
    }
}
