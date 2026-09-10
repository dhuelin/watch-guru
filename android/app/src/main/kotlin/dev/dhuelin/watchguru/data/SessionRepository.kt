package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.apis.AuthenticationApi
import dev.dhuelin.watchguru.api.models.ExchangeToken
import dev.dhuelin.watchguru.api.models.RefreshSession
import dev.dhuelin.watchguru.api.models.SessionResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Exchanging a provider sign-in for a session, and ending one.
 *
 * Separate from [WatchGuruRepository] because it is the only part of the app
 * that talks to the API without a session, and because it must run on an HTTP
 * client that has no [SessionAuthenticator] installed -- otherwise a refusal
 * here triggers a refresh, which fails, which triggers a refresh.
 *
 * Contains no Android types, so it is testable on a plain JVM.
 */
class SessionRepository(
    private val auth: AuthenticationApi,
    private val io: CoroutineDispatcher,
    private val clock: () -> Instant = Instant::now,
) {

    /**
     * Turns an Apple or Google ID token into a session.
     *
     * Also the account-creation call: the backend provisions a user the first
     * time it sees a valid token from a trusted issuer.
     */
    suspend fun exchange(providerToken: String): ApiResult<Tokens> = withContext(io) {
        callForTokens { auth.createSession(ExchangeToken(providerToken)) }
    }

    /**
     * Rotates a refresh token.
     *
     * Returns null rather than an [ApiResult] because the only caller is
     * [SessionAuthenticator], which has exactly two outcomes to act on: a new
     * session, or none.
     */
    suspend fun refresh(refreshToken: String): Tokens? = withContext(io) {
        when (val result = callForTokens { auth.refreshSession(RefreshSession(refreshToken)) }) {
            is ApiResult.Success -> result.value
            is ApiResult.Failure -> null
        }
    }

    /**
     * Ends the session server-side.
     *
     * Best-effort by design: the local tokens are cleared whether or not this
     * succeeds, because a user who taps sign out on a plane must still be
     * signed out. The server-side token then expires on its own.
     */
    suspend fun logout(refreshToken: String) {
        withContext(io) {
            runCatching { auth.endSession(RefreshSession(refreshToken)) }
        }
    }

    private suspend fun callForTokens(
        block: suspend () -> retrofit2.Response<SessionResponse>,
    ): ApiResult<Tokens> = apiCall(block).map { session ->
        tokensFrom(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            // expiresIn rather than the absolute timestamp: this device's clock
            // may be wrong, and the server's is the one that decides.
            expiresIn = session.expiresIn,
            now = clock(),
        )
    }
}
