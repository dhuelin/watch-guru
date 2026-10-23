package dev.huelin.watchguru.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.huelin.watchguru.api.models.UpdateProfile
import dev.huelin.watchguru.api.models.UserResponse
import dev.huelin.watchguru.data.ApiResult
import dev.huelin.watchguru.data.ProfileEvents
import dev.huelin.watchguru.data.WatchGuruRepository
import dev.huelin.watchguru.data.valueOrNull
import dev.huelin.watchguru.ui.components.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /**
     * Whether each connection is live, by slug ("plex", "jellyfin", "emby",
     * "trakt"). A slug is missing while its status loads or when it could not
     * be read; the row then says nothing rather than guess.
     */
    private val _connections = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val connections: StateFlow<Map<String, Boolean>> = _connections.asStateFlow()

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
        loadConnections()
    }

    /**
     * Reads every connection's state, all at once.
     *
     * Also called when the screen comes back, because the place a connection
     * changes is the screen this one opens. In parallel: four sequential
     * round trips would make the last row's state arrive noticeably late.
     */
    fun loadConnections() {
        viewModelScope.launch {
            _connections.value = coroutineScope {
                val servers = MEDIA_SERVERS.map { slug ->
                    async { slug to repository.mediaServerStatus(slug).valueOrNull()?.connected }
                }
                val trakt = async { TRAKT to repository.traktStatus().valueOrNull()?.connected }
                (servers.map { it.await() } + trakt.await())
                    .mapNotNull { (slug, connected) -> connected?.let { slug to it } }
                    .toMap()
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

/** The media servers Profile offers, in the order it lists them. */
internal val MEDIA_SERVERS = listOf("plex", "jellyfin", "emby")

internal const val TRAKT = "trakt"
