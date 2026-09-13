package dev.dhuelin.watchguru.ui.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.MediaServerStatusResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import dev.dhuelin.watchguru.ui.navigation.Routes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Connecting a media server: Plex, Jellyfin or Emby.
 *
 * One view model for all three, because the connection is identical in every
 * respect the screen can see. Which server this is arrives as a navigation
 * argument, and everything that differs between them -- what it is called,
 * where to paste the URL, whether a username is required -- comes back from the
 * server rather than being three copies of a screen here.
 *
 * The webhook URL is held in memory for exactly as long as the screen lives.
 * It is deliberately not saved: the server keeps only a hash of it, so anything
 * this app wrote down would be the only copy in existence and a copy nobody
 * asked it to keep. Losing it costs one tap, which issues a new one and retires
 * the old.
 */
@HiltViewModel
class MediaServerViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val service: String = checkNotNull(savedStateHandle[Routes.ARG_SERVICE])

    private val _status = MutableStateFlow<UiState<MediaServerStatusResponse>>(UiState.Loading)
    val status: StateFlow<UiState<MediaServerStatusResponse>> = _status.asStateFlow()

    /** The URL just issued, shown until the user leaves the screen. */
    private val _issuedUrl = MutableStateFlow<String?>(null)
    val issuedUrl: StateFlow<String?> = _issuedUrl.asStateFlow()

    /** What to do with it, in that server's own words. */
    private val _setUpHint = MutableStateFlow<String?>(null)
    val setUpHint: StateFlow<String?> = _setUpHint.asStateFlow()

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
            _status.value = when (val result = repository.mediaServerStatus(service)) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    fun connect(accountName: String?) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val result = repository.connectMediaServer(service, accountName)) {
                is ApiResult.Success -> {
                    _issuedUrl.value = result.value.webhookUrl
                    _setUpHint.value = result.value.setUpHint
                    load()
                }
                // A 409 here is the honest case: Jellyfin and Emby refuse to
                // connect without the username whose viewing counts, because
                // their webhook fires for everybody on the server.
                is ApiResult.Failure -> _error.value =
                    "That did not work. Check the username and try again."
            }
            _busy.value = false
        }
    }

    fun disconnect() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (repository.disconnectMediaServer(service)) {
                is ApiResult.Success -> {
                    _issuedUrl.value = null
                    _setUpHint.value = null
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
        _setUpHint.value = null
    }

    fun dismissError() {
        _error.value = null
    }
}
