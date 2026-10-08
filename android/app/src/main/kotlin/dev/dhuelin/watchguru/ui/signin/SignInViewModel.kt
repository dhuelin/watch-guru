package dev.dhuelin.watchguru.ui.signin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.DeviceRegistrar
import dev.dhuelin.watchguru.data.GoogleSignIn
import dev.dhuelin.watchguru.data.LocalDataCleaner
import dev.dhuelin.watchguru.data.SessionEvents
import dev.dhuelin.watchguru.data.SessionRepository
import dev.dhuelin.watchguru.data.TokenStore
import dev.dhuelin.watchguru.data.WatchGuruRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Where the app stands with respect to having a usable token. */
sealed interface AuthState {
    /** Deciding, on launch, whether a stored token exists. */
    data object Checking : AuthState

    data object SignedOut : AuthState

    data object SigningIn : AuthState

    data object SignedIn : AuthState

    data class Failed(val message: String) : AuthState
}

/**
 * Holds the sign-in state for the whole app.
 *
 * Scoped to the activity rather than to a screen, because the navigation host
 * and the profile screen both need to act on the same state.
 *
 * Known limitation: the provider ID token is used directly as the bearer
 * token, and those expire in about an hour. There is no refresh yet, so a long
 * session ends in 401s rather than a silent renewal. Fixing that means either
 * a token exchange endpoint on the backend, tracked as #26, rather than a
 * retry here that cannot succeed.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val tokens: TokenStore,
    private val googleSignIn: GoogleSignIn,
    private val repository: WatchGuruRepository,
    private val sessions: SessionRepository,
    private val sessionEvents: SessionEvents,
    private val localData: LocalDataCleaner,
    private val devices: DeviceRegistrar,
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Checking)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /**
     * A failure that must not change the auth state.
     *
     * A delete that fails leaves the user signed in, so it cannot travel as
     * [AuthState.Failed] -- that would drop them onto the sign-in screen while
     * their account still exists.
     */
    private val _accountError = MutableStateFlow<String?>(null)
    val accountError: StateFlow<String?> = _accountError.asStateFlow()

    init {
        _state.value = if (tokens.tokens() == null) AuthState.SignedOut else AuthState.SignedIn

        // Every launch, not only the one after signing in: push services
        // reissue tokens without telling the app, so the only reliable policy
        // is to send whatever this device has each time. The server treats a
        // token it already knows as the same device.
        if (_state.value == AuthState.SignedIn) registerDevice()

        // A renewal refused mid-request clears the tokens on a background
        // thread. Without this the app would stay on the signed-in screens and
        // 401 quietly on every one of them.
        viewModelScope.launch {
            sessionEvents.expired.collect { expired ->
                if (expired && _state.value == AuthState.SignedIn) {
                    _state.value = AuthState.Failed("Your session expired. Please sign in again.")
                    sessionEvents.acknowledge()
                }
            }
        }
    }

    /**
     * @param activityContext the hosting Activity: Credential Manager presents
     *   a bottom sheet and cannot do so from the application context.
     */
    fun signInWithGoogle(activityContext: Context) {
        if (_state.value == AuthState.SigningIn) return

        viewModelScope.launch {
            _state.value = AuthState.SigningIn
            _state.value = when (val result = googleSignIn.signIn(activityContext)) {
                is GoogleSignIn.Result.Success -> exchange(result.idToken)
                // Dismissing the sheet returns to the sign-in screen rather than
                // showing an error; the user did not fail at anything.
                GoogleSignIn.Result.Cancelled -> AuthState.SignedOut
                GoogleSignIn.Result.NoAccount ->
                    AuthState.Failed(
                        "No Google account on this device. Add one in Settings and try again.",
                    )
                is GoogleSignIn.Result.Failed ->
                    AuthState.Failed(result.message ?: "Couldn't sign in. Please try again.")
            }
        }
    }

    /**
     * Trades the Google ID token for a session of our own.
     *
     * The provider token is not stored: it lasts about an hour and cannot be
     * renewed without prompting the user again. What is stored is the pair the
     * backend returns, which refreshes silently for thirty days.
     */
    private suspend fun exchange(providerToken: String): AuthState =
        when (val result = sessions.exchange(providerToken)) {
            is ApiResult.Success -> {
                tokens.save(result.value)
                registerDevice()
                AuthState.SignedIn
            }
            // Google accepted the user, we did not. Almost always a
            // configuration mismatch -- the web client id the app was built
            // with is not in the backend's audience list -- so the message says
            // "couldn't sign you in", not "wrong password".
            is ApiResult.Failure -> AuthState.Failed(
                "Signed in with Google, but Watch Guru couldn't start a session. Please try again.",
            )
        }

    /**
     * Clears the token and everything derived from it.
     *
     * The token goes first and synchronously, so the very next request is
     * already unauthenticated and the UI switches immediately; wiping the
     * caches is disk I/O and follows on its own.
     *
     * A shared device must not leak the previous user's watch history, and
     * cached poster art alone is enough to do that -- see [LocalDataCleaner].
     */
    fun signOut() {
        val refreshToken = tokens.tokens()?.refreshToken
        // The screen changes now; the token survives a moment longer. Nothing
        // on the sign-in screen makes a request, so the user waits for nothing
        // -- and the one call below genuinely needs the session it is ending.
        _state.value = AuthState.SignedOut
        viewModelScope.launch {
            // Before the token is cleared, because forgetting this device is an
            // authenticated call about the caller's own device. It matters on a
            // shared phone: a device left registered sends the next user
            // notifications about the last one's series. Best-effort like the
            // rest -- signing out must work on a plane -- and bounded, so a
            // dead network cannot leave the token in place indefinitely.
            withTimeoutOrNull(UnregisterTimeoutMillis) { devices.unregister() }
            tokens.clear()
            // Revoking server-side is best-effort too. The refresh token
            // expires on its own if this never reaches the server.
            refreshToken?.let { sessions.logout(it) }
            localData.clear()
        }
    }

    /**
     * Registers this device, without letting a failure touch the auth state.
     *
     * Notifications are not what the user came for: somebody signing in wants
     * to be signed in, and a push token that could not be obtained or sent is
     * not a reason to tell them anything.
     */
    private fun registerDevice() {
        viewModelScope.launch { runCatching { devices.register() } }
    }

    /**
     * Deletes the account server-side, then signs out.
     *
     * Required by Play for any app offering account creation, and the backend
     * cascades the delete rather than setting a flag.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            when (repository.deleteAccount()) {
                is ApiResult.Success -> signOut()
                is ApiResult.Failure ->
                    _accountError.value = "Couldn't delete your account. Please try again."
            }
        }
    }

    /** Dismisses a sign-in error and returns to the sign-in screen. */
    fun dismissError() {
        if (_state.value is AuthState.Failed) _state.value = AuthState.SignedOut
    }

    /** Acknowledges the snackbar shown for [accountError]. */
    fun dismissAccountError() {
        _accountError.value = null
    }

    private companion object {
        /** Long enough for one request, short enough not to strand a token. */
        const val UnregisterTimeoutMillis = 5_000L
    }
}
