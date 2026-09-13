package dev.dhuelin.watchguru.ui.trakt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.SyncResultResponse
import dev.dhuelin.watchguru.api.models.TraktStatusResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Connecting Trakt.
 *
 * Authorising happens in a browser, so this screen cannot watch it happen: the
 * user leaves, approves, and comes back. [load] is therefore called again when
 * the screen resumes rather than only when it is created -- coming back to a
 * screen that still says "not connected" after connecting is the failure this
 * avoids.
 */
@HiltViewModel
class TraktViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
) : ViewModel() {

    private val _status = MutableStateFlow<UiState<TraktStatusResponse>>(UiState.Loading)
    val status: StateFlow<UiState<TraktStatusResponse>> = _status.asStateFlow()

    /** Where to send the user to approve; consumed by the screen once opened. */
    private val _authorizeUrl = MutableStateFlow<String?>(null)
    val authorizeUrl: StateFlow<String?> = _authorizeUrl.asStateFlow()

    /** What the last manual sync did, shown until the user leaves. */
    private val _lastSync = MutableStateFlow<SyncResultResponse?>(null)
    val lastSync: StateFlow<SyncResultResponse?> = _lastSync.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loading: Job? = null

    init {
        load()
    }

    fun load() {
        loading?.cancel()
        loading = viewModelScope.launch {
            _status.value = when (val current = _status.value) {
                is UiState.Content -> UiState.Refreshing(current.value)
                else -> UiState.Loading
            }
            _status.value = when (val result = repository.traktStatus()) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    fun connect() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.authorizeTrakt()) {
                is ApiResult.Success -> _authorizeUrl.value = result.value.authorizeUrl
                // A 409 here is the honest case: this server has no Trakt
                // application, and no amount of retrying changes that.
                is ApiResult.Failure -> _error.value =
                    "Trakt cannot be connected from this server right now."
            }
            _busy.value = false
        }
    }

    /** Called once the browser has been opened, so it is not opened twice. */
    fun authorizeUrlOpened() {
        _authorizeUrl.value = null
    }

    fun syncNow() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.syncTrakt()) {
                is ApiResult.Success -> {
                    _lastSync.value = result.value
                    load()
                }
                is ApiResult.Failure -> _error.value = "That sync did not work. Try again."
            }
            _busy.value = false
        }
    }

    fun disconnect() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (repository.disconnectTrakt()) {
                is ApiResult.Success -> {
                    _lastSync.value = null
                    load()
                }
                is ApiResult.Failure -> _error.value = "That did not work. Try again."
            }
            _busy.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
