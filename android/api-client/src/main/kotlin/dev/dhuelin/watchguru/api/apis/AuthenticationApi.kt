package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.ExchangeToken
import dev.dhuelin.watchguru.api.models.RefreshSession
import dev.dhuelin.watchguru.api.models.SessionResponse

interface AuthenticationApi {
    /**
     * POST api/v1/auth/session
     * Exchange a provider ID token for a session
     * Verifies the provider&#39;s ID token and returns an access token and a refresh token issued by this service. Provisions the account on first use.
     * Responses:
     *  - 200: OK
     *
     * @param exchangeToken 
     * @return [SessionResponse]
     */
    @POST("api/v1/auth/session")
    suspend fun createSession(@Body exchangeToken: ExchangeToken): Response<SessionResponse>

    /**
     * POST api/v1/auth/logout
     * Revoke a session
     * Revokes the refresh token and every token descended from the same sign-in. Succeeds even if the token was already invalid.
     * Responses:
     *  - 200: OK
     *
     * @param refreshSession 
     * @return [Unit]
     */
    @POST("api/v1/auth/logout")
    suspend fun endSession(@Body refreshSession: RefreshSession): Response<Unit>

    /**
     * POST api/v1/auth/refresh
     * Exchange a refresh token for a new session
     * Rotates the refresh token. The presented token is invalidated, and presenting it a second time revokes the entire session.
     * Responses:
     *  - 200: OK
     *
     * @param refreshSession 
     * @return [SessionResponse]
     */
    @POST("api/v1/auth/refresh")
    suspend fun refreshSession(@Body refreshSession: RefreshSession): Response<SessionResponse>

}
