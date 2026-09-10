package dev.dhuelin.watchguru.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.data.OfflineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sends whatever was made offline, and says how much is still waiting.
 *
 * Driven by the app coming to the foreground rather than a timer. A timer would
 * either be too slow to feel immediate when the signal returns, or fast enough
 * to be a battery cost for something that is usually a no-op.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val offline: OfflineRepository,
) : ViewModel() {

    private val _pending = MutableStateFlow(0)

    /** Changes not yet accepted by the server. Zero almost always. */
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private var running = false

    init {
        _pending.value = offline.pendingCount()
    }

    fun syncNow() {
        if (running) return
        viewModelScope.launch {
            running = true
            try {
                offline.sync()
            } finally {
                // Whatever happened -- drained, still offline, session gone --
                // the count is what the queue actually holds now, not what the
                // outcome hoped for.
                _pending.value = offline.pendingCount()
                running = false
            }
        }
    }
}
