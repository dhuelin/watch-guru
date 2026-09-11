package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.ConnectPlex
import dev.dhuelin.watchguru.api.models.PlexConnectionResponse
import dev.dhuelin.watchguru.api.models.PlexStatusResponse

interface PlexControllerApi {
    /**
     * POST api/v1/me/streaming-accounts/plex
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param connectPlex  (optional)
     * @return [PlexConnectionResponse]
     */
    @POST("api/v1/me/streaming-accounts/plex")
    suspend fun connectPlex(@Body connectPlex: ConnectPlex? = null): Response<PlexConnectionResponse>

    /**
     * DELETE api/v1/me/streaming-accounts/plex
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [Unit]
     */
    @DELETE("api/v1/me/streaming-accounts/plex")
    suspend fun disconnectPlex(): Response<Unit>

    /**
     * GET api/v1/me/streaming-accounts/plex
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [PlexStatusResponse]
     */
    @GET("api/v1/me/streaming-accounts/plex")
    suspend fun getPlexStatus(): Response<PlexStatusResponse>

}
