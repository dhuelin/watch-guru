package dev.dhuelin.watchguru.ui.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.api.models.StreamingServiceResponse
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.MyServices
import dev.dhuelin.watchguru.data.WatchGuruRepository
import dev.dhuelin.watchguru.ui.components.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Picking the services the user pays for.
 *
 * The list of services comes from the server, because it is the server that
 * knows which ones the catalogue can report availability on. The *selection*
 * is local — see [MyServices] for why, and for what that costs.
 *
 * Saved on every tap rather than behind a Save button. There is nothing to
 * validate and nothing to undo: the only effect is the order of a list on
 * another screen, and a Save button would invite the user to wonder what
 * happens if they do not press it.
 */
@HiltViewModel
class MyServicesViewModel @Inject constructor(
    private val repository: WatchGuruRepository,
    private val services: MyServices,
) : ViewModel() {

    private val _all = MutableStateFlow<UiState<List<StreamingServiceResponse>>>(UiState.Loading)
    val all: StateFlow<UiState<List<StreamingServiceResponse>>> = _all.asStateFlow()

    /** The chosen ids, straight from the store so it is the one source. */
    val selected: StateFlow<Set<Long>> get() = services.selected

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _all.value = when (val result = repository.streamingServices()) {
                is ApiResult.Success ->
                    if (result.value.isEmpty()) {
                        UiState.Empty
                    } else {
                        // By name, because the server's order is its seeding
                        // order and means nothing to a reader looking for one.
                        UiState.Content(result.value.sortedBy { it.name.lowercase() })
                    }
                is ApiResult.Failure -> UiState.Error(result)
            }
        }
    }

    fun toggle(id: Long) {
        val current = services.selected.value
        services.save(if (id in current) current - id else current + id)
    }
}
