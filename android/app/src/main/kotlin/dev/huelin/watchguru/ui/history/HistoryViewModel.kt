package dev.huelin.watchguru.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.huelin.watchguru.api.apis.WatchHistoryControllerApi
import dev.huelin.watchguru.api.models.StreamingServiceResponse
import dev.huelin.watchguru.api.models.WatchEventResponse
import dev.huelin.watchguru.data.ApiResult
import dev.huelin.watchguru.data.WatchGuruRepository
import dev.huelin.watchguru.ui.components.UiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * The span of days the timeline is scoped to; both bounds inclusive, either
 * one absent meaning "no bound that way".
 */
data class DateRange(val from: LocalDate?, val to: LocalDate?) {

    val isAnyTime: Boolean get() = from == null && to == null

    companion object {
        fun any() = DateRange(null, null)
    }
}

/** Which kinds of viewing the timeline is showing. */
enum class HistoryType(val api: WatchHistoryControllerApi.TypeGetHistory?) {
    ALL(null),
    FILMS(WatchHistoryControllerApi.TypeGetHistory.MOVIE),
    SERIES(WatchHistoryControllerApi.TypeGetHistory.TV_SERIES),
}

/**
 * The user's viewing history, and the place mistakes get corrected.
 *
 * Backed by the append-only watch_event log, which is the real record of what
 * happened -- the watchlist and per-episode state are derived from it. That is
 * also why editing goes to the server rather than being applied here: moving a
 * date changes what those derived rows should say, and two answers to that
 * question is one too many.
 *
 * Filtering is a fresh request rather than a filter over what is already
 * loaded. The list is paged, so filtering a page would search the last fifty
 * viewings and call it a search of the history.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
) : ViewModel() {

    private val _events = MutableStateFlow<UiState<List<WatchEventResponse>>>(UiState.Loading)
    val events: StateFlow<UiState<List<WatchEventResponse>>> = _events.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _type = MutableStateFlow(HistoryType.ALL)
    val type: StateFlow<HistoryType> = _type.asStateFlow()

    private val _serviceId = MutableStateFlow<Long?>(null)
    val serviceId: StateFlow<Long?> = _serviceId.asStateFlow()

    /**
     * The days the timeline is scoped to, both bounds inclusive.
     *
     * Dates rather than instants all the way down: the user picks days, the API
     * takes days, and the server turns the last one into the instant that ends
     * it in their own zone. Converting here would put that arithmetic in three
     * places, each free to disagree about what "the 14th" means.
     */
    private val _range = MutableStateFlow(DateRange.any())
    val range: StateFlow<DateRange> = _range.asStateFlow()

    /** For the filter row and the edit sheet's picker. */
    private val _services = MutableStateFlow<List<StreamingServiceResponse>>(emptyList())
    val services: StateFlow<List<StreamingServiceResponse>> = _services.asStateFlow()

    private var loading: Job? = null

    init {
        refresh()
        loadServices()
        watchTheSearchBox()
    }

    fun refresh() {
        loading?.cancel()
        loading = viewModelScope.launch {
            _events.value = when (val current = _events.value) {
                is UiState.Content -> UiState.Refreshing(current.value)
                else -> UiState.Loading
            }
            _events.value = when (
                val result = repository.history(
                    from = _range.value.from,
                    to = _range.value.to,
                    type = _type.value.api,
                    serviceId = _serviceId.value,
                    query = _query.value,
                )
            ) {
                is ApiResult.Success ->
                    if (result.value.isEmpty()) UiState.Empty else UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setType(type: HistoryType) {
        if (_type.value == type) return
        _type.value = type
        refresh()
    }

    fun setService(serviceId: Long?) {
        if (_serviceId.value == serviceId) return
        _serviceId.value = serviceId
        refresh()
    }

    /** Scopes the timeline to a span of days, or to all of them. */
    fun setRange(range: DateRange) {
        if (_range.value == range) return
        _range.value = range
        refresh()
    }

    /**
     * Corrects one entry.
     *
     * Refreshes rather than patching the row in place: moving a date can move
     * the entry into a different day, and can change which viewing counts as
     * the rewatch. Both are the server's arithmetic, and guessing at them here
     * would show something that disagrees with the next refresh.
     */
    fun edit(event: WatchEventResponse, watchedAt: Instant?, serviceId: Long?) {
        viewModelScope.launch {
            val result = repository.updateWatchEvent(
                eventId = event.id,
                watchedAt = watchedAt?.atOffset(OffsetDateTime.now(ZoneId.systemDefault()).offset),
                serviceId = serviceId,
            )
            if (result is ApiResult.Success) refresh()
        }
    }

    /**
     * An entry the user has deleted but the server has not been told about.
     *
     * Deleting waits for the undo snackbar to go: the row disappears at once,
     * and the call is only made when the snackbar times out, is swiped away,
     * or the screen is left. Undo then needs no server call at all, which is
     * the only kind of undo that cannot fail -- re-creating a history entry
     * after the fact would lose which viewing was the rewatch.
     */
    private val _pendingDelete = MutableStateFlow<WatchEventResponse?>(null)
    val pendingDelete: StateFlow<WatchEventResponse?> = _pendingDelete.asStateFlow()

    /** A delete the server refused, for a one-off snackbar. */
    private val _deleteFailure = MutableStateFlow<ApiResult.Failure?>(null)
    val deleteFailure: StateFlow<ApiResult.Failure?> = _deleteFailure.asStateFlow()

    /**
     * Hides one entry and offers to bring it back.
     *
     * A second delete while the first is still undoable commits the first:
     * only one snackbar shows at a time, and an entry whose undo was never
     * offered must not sit hidden for ever.
     */
    fun delete(event: WatchEventResponse) {
        commitPendingDelete()
        val before = (_events.value as? UiState.Content)?.value ?: return
        _events.value = UiState.Content(before.filterNot { it.id == event.id })
        _pendingDelete.value = event
    }

    /** Brings the hidden entry back; nothing was sent, so nothing to reverse. */
    fun undoDelete() {
        _pendingDelete.value ?: return
        _pendingDelete.value = null
        refresh()
    }

    /**
     * Sends the delete the snackbar was holding back.
     *
     * NonCancellable because this is also called as the screen goes away, and
     * a delete the user saw happen must not be dropped halfway because the
     * screen's scope ended. Deleting the last entry for an episode also makes
     * that episode unwatched again -- handled server-side, so a refresh is
     * what surfaces it rather than anything guessed here.
     */
    fun commitPendingDelete() {
        val event = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            val result = withContext(NonCancellable) { repository.deleteWatchEvent(event.id) }
            when (result) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> {
                    _deleteFailure.value = result
                    refresh()
                }
            }
        }
    }

    fun dismissDeleteFailure() {
        _deleteFailure.value = null
    }

    private fun loadServices() {
        viewModelScope.launch {
            val result = repository.streamingServices()
            if (result is ApiResult.Success) {
                _services.value = result.value.sortedBy { it.name }
            }
        }
    }

    /**
     * A request per pause in typing, not per keystroke.
     *
     * The first value is dropped because it is the empty box the screen opens
     * with, and re-fetching the unfiltered history a moment after loading it
     * would be two requests for one screen.
     */
    private fun watchTheSearchBox() {
        viewModelScope.launch {
            _query.drop(1).debounce(300).collect { refresh() }
        }
    }
}
