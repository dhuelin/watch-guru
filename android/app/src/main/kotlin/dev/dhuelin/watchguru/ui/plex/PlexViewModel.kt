package dev.dhuelin.watchguru.ui.plex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.PlexStatusResponse
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
 * Connecting a Plex server.
 *
 * The webhook URL is held here, in memory, for exactly as long as the screen
 * lives. It is deliberately not saved: the server keeps only a hash of it, so
 * anything this app wrote down would be the only copy in existence and a copy
 * nobody asked it to keep. Losing it costs one tap on Connect, which issues a
 * new one and retires the old.
 */
@HiltViewModel
class PlexViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
) : ViewModel() {

    private val _status = MutableStateFlow<UiState<PlexStatusResponse>>(UiState.Loading)
    val status: StateFlow<UiState<PlexStatusResponse>> = _status.asStateFlow()

    /** The URL just issued, shown until the user leaves the screen. */
    private val _issuedUrl = MutableStateFlow<String?>(null)
    val issuedUrl: StateFlow<String?> = _issuedUrl.asStateFlow()

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
            _status.value = when (val result = repository.plexStatus()) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    fun connect(plexUsername: String?) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.connectPlex(plexUsername)) {
                is ApiResult.Success -> {
                    _issuedUrl.value = result.value.webhookUrl
                    _status.value = UiState.Content(
                        PlexStatusResponse(
                            connected = true,
                            recentRuns = emptyList(),
                            account = result.value.account,
                        ),
                    )
                    // The connect response is authoritative about the link but
                    // knows nothing about past deliveries, so the real status
                    // is fetched behind the URL the user is already reading.
                    load()
                }
                is ApiResult.Failure -> _error.value = "That did not work. Try again."
            }
            _busy.value = false
        }
    }

    fun disconnect() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (repository.disconnectPlex()) {
                is ApiResult.Success -> {
                    _issuedUrl.value = null
                    load()
                }
                is ApiResult.Failure -> _error.value = "That did not work. Try again."
            }
            _busy.value = false
        }
    }

    /** Called once the URL has been copied, or dismissed without copying. */
    fun dismissIssuedUrl() {
        _issuedUrl.value = null
    }

    fun dismissError() {
        _error.value = null
    }
}
