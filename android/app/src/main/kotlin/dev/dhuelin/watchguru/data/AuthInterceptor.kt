package dev.dhuelin.watchguru.data

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the bearer token to every outgoing request.
 *
 * Reads the token per request rather than capturing it once, so a sign-in or
 * sign-out takes effect on the next call without rebuilding the client.
 */
class AuthInterceptor(private val tokens: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.token()
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
