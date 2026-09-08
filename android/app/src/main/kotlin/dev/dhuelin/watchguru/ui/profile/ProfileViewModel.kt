package dev.dhuelin.watchguru.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.UserResponse
import dev.dhuelin.watchguru.data.ApiResult
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
) : ViewModel() {

    private val _profile = MutableStateFlow<UiState<UserResponse>>(UiState.Loading)
    val profile: StateFlow<UiState<UserResponse>> = _profile.asStateFlow()

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
}
