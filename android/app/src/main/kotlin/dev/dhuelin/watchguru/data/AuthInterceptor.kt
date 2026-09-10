package dev.dhuelin.watchguru.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the access token to every outgoing request.
 *
 * Reads it per request rather than capturing it once, so a sign-in, a sign-out
 * or a refresh takes effect on the next call without rebuilding the client.
 *
 * Renewal is not here: see [SessionAuthenticator], which acts on the server's
 * 401 rather than on this device's opinion of the time.
 */
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.tokens()?.accessToken
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
