package dev.dhuelin.watchguru.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-way channel from "a renewal was refused" to "the app says so".
 *
 * [SessionAuthenticator] discovers this deep inside an OkHttp call, on a
 * background thread, with no reference to any screen. Without this the tokens
 * would be cleared and the UI would stay on the signed-in screens, 401-ing
 * quietly on every one of them.
 *
 * A refused renewal is not transient: the refresh token was revoked, expired,
 * or the server detected it being reused. There is nothing to retry.
 *
 * No Android types, so it is testable on a plain JVM.
 */
class SessionEvents {

    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    fun onSessionLost() {
        _expired.value = true
    }

    /** Called once the UI has moved; a second expiry can then be reported. */
    fun acknowledge() {
        _expired.value = false
    }
}
