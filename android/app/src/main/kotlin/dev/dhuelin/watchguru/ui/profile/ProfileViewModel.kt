package dev.dhuelin.watchguru.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.UpdateProfile
import dev.dhuelin.watchguru.api.models.UserResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.ProfileEvents
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
    private val profileEvents: ProfileEvents,
) : ViewModel() {

    private val _profile = MutableStateFlow<UiState<UserResponse>>(UiState.Loading)
    val profile: StateFlow<UiState<UserResponse>> = _profile.asStateFlow()

    private val _savingRegion = MutableStateFlow(false)
    val savingRegion: StateFlow<Boolean> = _savingRegion.asStateFlow()

    /**
     * A failure that must leave the profile on screen.
     *
     * It travels as a flag rather than as [UiState.Error]: the profile loaded
     * fine and is still valid, so replacing the whole screen with an error --
     * including the sign-out button -- would be a heavy answer to a rejected
     * PATCH. The screen turns it into a snackbar.
     */
    private val _regionError = MutableStateFlow(false)
    val regionError: StateFlow<Boolean> = _regionError.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _profile.value = when (val result = repository.profile()) {
                is ApiResult.Success -> UiState.Content(result.value)
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    /**
     * Changes which country's streaming offers the user is shown.
     *
     * Deliberately not optimistic. The screen is the only place the region is
     * visible, so showing the new one before the server has taken it would
     * leave someone believing they had changed it when they had not -- and the
     * symptom, offers for the wrong country, is exactly what they were trying
     * to fix. The server's own response is what lands in the state.
     */
    fun setRegion(code: String) {
        val current = (_profile.value as? UiState.Content)?.value ?: return
        if (code.equals(current.region, ignoreCase = true)) return

        viewModelScope.launch {
            _savingRegion.value = true
            when (val result = repository.updateProfile(UpdateProfile(region = code))) {
                is ApiResult.Success -> {
                    _profile.value = UiState.Content(result.value)
                    // Any title screen still on the back stack is now showing
                    // offers for the country the user just left.
                    profileEvents.profileChanged()
                }
                is ApiResult.Failure -> _regionError.value = true
            }
            _savingRegion.value = false
        }
    }

    /** Acknowledges the snackbar shown after a failed region change. */
    fun dismissRegionError() {
        _regionError.value = false
    }
}
