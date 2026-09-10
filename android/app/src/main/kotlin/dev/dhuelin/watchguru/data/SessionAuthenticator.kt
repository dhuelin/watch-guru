package dev.dhuelin.watchguru.data

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.time.Instant

/**
 * Renews the session when the API says the access token is no longer good.
 *
 * Access tokens last fifteen minutes; refresh tokens last thirty days and
 * rotate on every use. This is what makes a session survive longer than the
 * access token without ever prompting the user again.
 *
 * An OkHttp [Authenticator] rather than an interceptor that checks the clock:
 * the server decides when a token is dead, and a device with a wrong clock
 * would otherwise either refresh constantly or never.
 *
 * @param refresh exchanges a refresh token for a new session, or returns null
 *   if the server refused. A lambda rather than the generated API type so this
 *   class stays free of Retrofit and testable on a plain JVM -- it must be
 *   backed by an HTTP client that does *not* have this authenticator installed,
 *   or a failing refresh recurses.
 * @param onSessionLost called when the refresh token is no longer accepted.
 *   That is not a transient error: the token was revoked, expired, or the
 *   server detected it being reused. The user has to sign in again.
 */
class SessionAuthenticator(
    private val tokens: TokenStore,
    private val refresh: suspend (String) -> Tokens?,
    private val onSessionLost: () -> Unit,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // A retry that also came back 401 means the fresh token was rejected
        // too, and refreshing again will not change that. Returning null gives
        // the 401 to the caller instead of looping until OkHttp gives up.
        if (response.priorResponse != null) {
            return null
        }

        val attempted = response.request.header(HEADER)

        // Serialised because refresh tokens rotate: two concurrent refreshes
        // would spend the same token twice, and the server treats a token used
        // twice as theft and revokes the whole session. Getting this wrong
        // would log the user out precisely when the app is busiest.
        synchronized(this) {
            val current = tokens.tokens() ?: return null

            // Another request refreshed while this one waited for the lock.
            // Retry with what it obtained rather than spending a second token.
            if (bearer(current.accessToken) != attempted) {
                return retryWith(response.request, current.accessToken)
            }

            val renewed = runBlocking { refresh(current.refreshToken) }
            if (renewed == null) {
                onSessionLost()
                return null
            }

            tokens.save(renewed)
            return retryWith(response.request, renewed.accessToken)
        }
    }

    private fun retryWith(request: Request, accessToken: String): Request =
        request.newBuilder().header(HEADER, bearer(accessToken)).build()

    private companion object {
        const val HEADER = "Authorization"
        fun bearer(token: String) = "Bearer $token"
    }
}

/**
 * Turns the server's `expiresIn` into an absolute instant.
 *
 * Kept here rather than in the DTO so the arithmetic is done once, at the
 * moment the response arrives, when "now" is still meaningful.
 */
fun tokensFrom(accessToken: String, refreshToken: String, expiresIn: Long, now: Instant): Tokens =
    Tokens(accessToken, refreshToken, now.plusSeconds(expiresIn))
