package dev.huelin.watchguru.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.huelin.watchguru.api.models.UpNextResponse
import dev.huelin.watchguru.data.ApiResult
import dev.huelin.watchguru.data.OfflineRepository
import dev.huelin.watchguru.data.WatchGuruRepository
import dev.huelin.watchguru.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: OfflineRepository,
    private val network: WatchGuruRepository,
) : ViewModel() {

    private val _upNext = MutableStateFlow<UiState<List<UpNextResponse>>>(UiState.Loading)
    val upNext: StateFlow<UiState<List<UpNextResponse>>> = _upNext.asStateFlow()

    /** Title ids with a mark in flight, so one card can be busy without the rest. */
    private val _marking = MutableStateFlow<Set<Long>>(emptySet())
    val marking: StateFlow<Set<Long>> = _marking.asStateFlow()

    /**
     * The last episode marked from this screen, while it can still be undone.
     *
     * Up Next is where accidental marks happen: a stale card, a tap meant for
     * the card itself. The screen turns this into a snackbar with Undo.
     */
    private val _lastMarked = MutableStateFlow<Marked?>(null)
    val lastMarked: StateFlow<Marked?> = _lastMarked.asStateFlow()

    /** A mark or an undo that the server refused, for a one-off snackbar. */
    private val _failure = MutableStateFlow<ApiResult.Failure?>(null)
    val failure: StateFlow<ApiResult.Failure?> = _failure.asStateFlow()

    private var loading: Job? = null

    init {
        refresh()
    }

    /** Whether [onResume] has fired yet; the first one is the initial load. */
    private var resumedBefore = false

    /**
     * The screen came back to the foreground, or back from another screen.
     *
     * The first resume is skipped because [init] has just loaded, and a second
     * identical request a frame later would only cancel the first.
     */
    fun onResume() {
        if (!resumedBefore) {
            resumedBefore = true
            return
        }
        refresh()
    }

    /**
     * Re-reads Up Next.
     *
     * Also called on every return to the screen, not only on first load: the
     * list goes stale the moment an episode is marked anywhere else -- on
     * another phone, from the widget, on the detail screen -- and a stale card
     * is how a second tap on "Mark watched" became an accidental rewatch.
     */
    fun refresh() {
        // The newest request wins. Resuming while a load is in flight would
        // otherwise run two, and the slower one would land last.
        loading?.cancel()
        loading = viewModelScope.launch {
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
            var eventId: Long? = null
            val written = repository.markEpisodeWatched(entry.nextEpisodeId, entry.titleId) { eventId = it }
            when (written) {
                OfflineRepository.Written.Sent -> {
                    eventId?.let { _lastMarked.value = Marked(it, entry.nextEpisodeCode, entry.primaryTitle) }
                    refresh()
                }
                // Queued counts as done from here: it is stored and it will be
                // sent. Refreshing is pointless offline, and the card stays put
                // rather than pretending the server moved on. No undo either:
                // there is no server entry yet to take back.
                OfflineRepository.Written.Queued -> Unit
                is OfflineRepository.Written.Failed -> _failure.value = written.failure
            }
            _marking.value = _marking.value - entry.titleId
        }
    }

    /**
     * Takes back the last mark.
     *
     * Deletes that one history entry rather than unmarking the episode: if the
     * accidental mark was a rewatch, unmarking would also erase the real first
     * viewing. The server makes the episode unwatched again when its last
     * entry goes, and the refresh brings the card back.
     */
    fun undoLastMark() {
        val marked = _lastMarked.value ?: return
        _lastMarked.value = null
        viewModelScope.launch {
            when (val result = network.deleteWatchEvent(marked.eventId)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure -> _failure.value = result
            }
        }
    }

    /** The snackbar has gone, by timeout or by swipe; the mark stands. */
    fun dismissLastMark() {
        _lastMarked.value = null
    }

    fun dismissFailure() {
        _failure.value = null
    }

    /** One mark that may still be undone. */
    data class Marked(val eventId: Long, val episodeCode: String, val title: String)
}
