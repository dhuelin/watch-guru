package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.ConnectMediaServer
import dev.dhuelin.watchguru.api.models.MediaServerConnectionResponse
import dev.dhuelin.watchguru.api.models.MediaServerStatusResponse

interface MediaServerControllerApi {
    /**
     * POST api/v1/me/streaming-accounts/servers/{service}
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param service 
     * @param connectMediaServer  (optional)
     * @return [MediaServerConnectionResponse]
     */
    @POST("api/v1/me/streaming-accounts/servers/{service}")
    suspend fun connectMediaServer(@Path("service") service: kotlin.String, @Body connectMediaServer: ConnectMediaServer? = null): Response<MediaServerConnectionResponse>

    /**
     * DELETE api/v1/me/streaming-accounts/servers/{service}
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param service 
     * @return [Unit]
     */
    @DELETE("api/v1/me/streaming-accounts/servers/{service}")
    suspend fun disconnectMediaServer(@Path("service") service: kotlin.String): Response<Unit>

    /**
     * GET api/v1/me/streaming-accounts/servers/{service}
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param service 
     * @return [MediaServerStatusResponse]
     */
    @GET("api/v1/me/streaming-accounts/servers/{service}")
    suspend fun getMediaServerStatus(@Path("service") service: kotlin.String): Response<MediaServerStatusResponse>

}
