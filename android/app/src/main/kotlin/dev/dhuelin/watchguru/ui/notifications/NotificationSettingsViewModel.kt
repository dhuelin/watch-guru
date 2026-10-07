package dev.dhuelin.watchguru.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.NotificationSettingsResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.DeviceRegistrar
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The notification settings screen.
 *
 * Two switches with different scopes -- one global, one per series -- and the
 * global one wins. The server enforces that; this only has to show it, which is
 * why flipping the global switch greys the list rather than hiding it: a user
 * who turns everything off and back on should find their per-series choices
 * where they left them.
 *
 * Every change is sent immediately and the server's answer replaces local
 * state. No save button, and no optimistic flip either: a switch that springs
 * back is confusing, but a switch that lies is worse.
 */
@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
    private val devices: DeviceRegistrar,
) : ViewModel() {

    private val _settings = MutableStateFlow<UiState<NotificationSettingsResponse>>(UiState.Loading)
    val settings: StateFlow<UiState<NotificationSettingsResponse>> = _settings.asStateFlow()

    /** True while a switch is in flight, so the screen can disable them all. */
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
            _settings.value = when (val current = _settings.value) {
                is UiState.Content -> UiState.Refreshing(current.value)
                else -> UiState.Loading
            }
            _settings.value = when (val result = repository.notificationSettings()) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    fun setEnabled(enabled: Boolean) = change { repository.setNotificationsEnabled(enabled) }

    fun setSeries(titleId: Long, newEpisodes: Boolean) =
        change { repository.setSeriesNotification(titleId, newEpisodes) }

    /**
     * Registers this device again, after the user has granted permission.
     *
     * Permission and registration are separate things that look like one to the
     * user: Android can let the app post notifications while the backend still
     * has no token to send to. Granting is therefore the moment to try again,
     * rather than waiting for the next launch.
     */
    fun registerDevice() {
        viewModelScope.launch {
            if (devices.register()) load()
        }
    }

    private fun change(request: suspend () -> ApiResult<NotificationSettingsResponse>) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            when (val result = request()) {
                // The server's answer rather than the value just sent: the
                // whole settings object comes back, so the global switch and
                // the list cannot drift apart.
                is ApiResult.Success -> _settings.value = UiState.Content(result.value)
                is ApiResult.Failure -> _error.value =
                    "Couldn't save that. Please try again."
            }
            _busy.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
