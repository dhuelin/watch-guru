package dev.dhuelin.watchguru.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.apis.WatchHistoryControllerApi
import dev.dhuelin.watchguru.api.models.WatchStats
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which slice of the history the screen is showing. */
enum class StatsPeriod(val api: WatchHistoryControllerApi.PeriodGetStats) {
    MONTH(WatchHistoryControllerApi.PeriodGetStats.MONTH),
    YEAR(WatchHistoryControllerApi.PeriodGetStats.YEAR),
    ALL_TIME(WatchHistoryControllerApi.PeriodGetStats.ALL_TIME),
}

/**
 * Everything the server already knows about what this person has watched.
 *
 * All of it is computed in SQL over the append-only event log, so the screen
 * asks again when the period changes rather than slicing a cached answer --
 * a "this month" total filtered from an all-time payload would be wrong the
 * moment the payload was trimmed at the top.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow<UiState<WatchStats>>(UiState.Loading)
    val stats: StateFlow<UiState<WatchStats>> = _stats.asStateFlow()

    private val _period = MutableStateFlow(StatsPeriod.ALL_TIME)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    /** The request in flight, so a second tap cannot be answered by the first. */
    private var loading: Job? = null

    init {
        load()
    }

    fun setPeriod(period: StatsPeriod) {
        if (_period.value == period) return
        _period.value = period
        load()
    }

    fun load() {
        loading?.cancel()
        loading = viewModelScope.launch {
            _stats.value = when (val current = _stats.value) {
                // Keep the numbers on screen while the new ones arrive:
                // replacing a populated screen with a spinner on every tap of
                // the period selector is worse than briefly stale figures.
                is UiState.Content -> UiState.Refreshing(current.value)
                else -> UiState.Loading
            }
            _stats.value = when (val result = repository.stats(period = _period.value.api)) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }
}
