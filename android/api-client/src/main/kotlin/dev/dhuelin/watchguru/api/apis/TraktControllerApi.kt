package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.SyncResultResponse
import dev.dhuelin.watchguru.api.models.TraktAuthorizationResponse
import dev.dhuelin.watchguru.api.models.TraktStatusResponse

interface TraktControllerApi {
    /**
     * POST api/v1/me/streaming-accounts/trakt/authorize
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [TraktAuthorizationResponse]
     */
    @POST("api/v1/me/streaming-accounts/trakt/authorize")
    suspend fun authorizeTrakt(): Response<TraktAuthorizationResponse>

    /**
     * DELETE api/v1/me/streaming-accounts/trakt
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [Unit]
     */
    @DELETE("api/v1/me/streaming-accounts/trakt")
    suspend fun disconnectTrakt(): Response<Unit>

    /**
     * GET api/v1/me/streaming-accounts/trakt
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [TraktStatusResponse]
     */
    @GET("api/v1/me/streaming-accounts/trakt")
    suspend fun getTraktStatus(): Response<TraktStatusResponse>

    /**
     * POST api/v1/me/streaming-accounts/trakt/sync
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [SyncResultResponse]
     */
    @POST("api/v1/me/streaming-accounts/trakt/sync")
    suspend fun syncTrakt(): Response<SyncResultResponse>

}
