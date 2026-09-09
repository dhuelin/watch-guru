package dev.dhuelin.watchguru.ui.signin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dhuelin.watchguru.data.ApiResult
import dev.dhuelin.watchguru.data.GoogleSignIn
import dev.dhuelin.watchguru.data.TokenStore
import dev.dhuelin.watchguru.data.WatchGuruRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * a refresh flow here or a token exchange endpoint on the backend; it is
 * tracked separately rather than faked with a retry that cannot succeed.
 */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val tokens: TokenStore,
    private val googleSignIn: GoogleSignIn,
    private val repository: WatchGuruRepository,
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
        _state.value = if (tokens.token() == null) AuthState.SignedOut else AuthState.SignedIn
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
                is GoogleSignIn.Result.Success -> {
                    tokens.save(result.idToken)
                    AuthState.SignedIn
                }
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
     * Clears the token.
     *
     * A shared device must not leak the previous user's watch history, so
     * anything cached from the API has to go with it. Nothing is cached on disk
     * today (#14 is the offline cache); when it is, this is where it gets
     * cleared.
     */
    fun signOut() {
        tokens.clear()
        _state.value = AuthState.SignedOut
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
}
