package dev.dhuelin.watchguru.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.SeasonsResponse
import dev.dhuelin.watchguru.api.models.TitleProgress
import dev.dhuelin.watchguru.api.models.TitleResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.OfflineRepository
import dev.dhuelin.watchguru.data.ProfileEvents
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TitleDetailViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
    private val offline: OfflineRepository,
    profileEvents: ProfileEvents,
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

    /** Null for films, which have no seasons. */
    private val _seasons = MutableStateFlow<SeasonsResponse?>(null)
    val seasons: StateFlow<SeasonsResponse?> = _seasons.asStateFlow()

    /** Which season is expanded. Defaults to the one holding the next episode. */
    private val _expandedSeason = MutableStateFlow<Int?>(null)
    val expandedSeason: StateFlow<Int?> = _expandedSeason.asStateFlow()

    init {
        load()

        // Offers are per country and are loaded once. This screen stays on the
        // back stack while the user changes their region in Profile, so
        // without this they would come back to the offers for the country they
        // just left. drop(1) because the first value is the state at
        // subscription, not a change.
        viewModelScope.launch {
            profileEvents.changes.drop(1).collect { load() }
        }
    }

    fun load() {
        viewModelScope.launch {
            _title.value = when (val result = repository.title(titleId)) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
            refreshProgress()
            refreshSeasons()
        }
    }

    fun toggleSeason(seasonNumber: Int) {
        _expandedSeason.value = if (_expandedSeason.value == seasonNumber) null else seasonNumber
    }

    /** Marks or unmarks one episode. */
    fun toggleEpisode(episodeId: Long, currentlyWatched: Boolean) {
        if (_marking.value) return
        viewModelScope.launch {
            _marking.value = true
            val result = if (currentlyWatched) {
                offline.unmarkEpisode(episodeId)
            } else {
                offline.markEpisodeWatched(episodeId, titleId)
            }
            // Sent refreshes from the server. Queued does not: there is no
            // network to refresh from, and the local state already reflects
            // what the user asked for.
            if (result is OfflineRepository.Written.Sent) {
                refreshProgress()
                refreshSeasons()
            }
            _marking.value = false
        }
    }

    /**
     * Marks everything up to and including one episode.
     *
     * The action people reach for when logging a series they finished years
     * ago. One request, and idempotent on the server, so a mis-tap costs
     * nothing.
     */
    fun markUpTo(episodeId: Long) {
        if (_marking.value) return
        viewModelScope.launch {
            _marking.value = true
            if (offline.markWatchedUpTo(episodeId) is OfflineRepository.Written.Sent) {
                refreshProgress()
                refreshSeasons()
            }
            _marking.value = false
        }
    }

    private suspend fun refreshSeasons() {
        _seasons.value = when (val result = repository.seasons(titleId)) {
            is ApiResult.Success -> result.value.also { response ->
                if (_expandedSeason.value == null) {
                    // Open the season holding the next episode, so the thing
                    // the user came to do is already on screen.
                    val next = _progress.value?.nextEpisodeId
                    _expandedSeason.value = response.seasons
                        .firstOrNull { season -> season.episodes.any { it.id == next } }
                        ?.seasonNumber
                        ?: response.seasons.firstOrNull { it.seasonNumber > 0 }?.seasonNumber
                }
            }
            is ApiResult.Failure -> null
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
            if (offline.markEpisodeWatched(next, titleId) is OfflineRepository.Written.Sent) {
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
