package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.LinkedAccountResponse
import dev.dhuelin.watchguru.api.models.StreamingServiceResponse

interface StreamingControllerApi {
    /**
     * GET api/v1/me/streaming-accounts
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [kotlin.collections.List<LinkedAccountResponse>]
     */
    @GET("api/v1/me/streaming-accounts")
    suspend fun listLinkedAccounts(): Response<kotlin.collections.List<LinkedAccountResponse>>

    /**
     * GET api/v1/streaming-services
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [kotlin.collections.List<StreamingServiceResponse>]
     */
    @GET("api/v1/streaming-services")
    suspend fun listStreamingServices(): Response<kotlin.collections.List<StreamingServiceResponse>>

    /**
     * POST api/v1/streaming-services/reconcile
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param region  (optional)
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any?>]
     */
    @POST("api/v1/streaming-services/reconcile")
    suspend fun reconcileStreamingServices(@Query("region") region: kotlin.String? = null): Response<kotlin.collections.Map<kotlin.String, kotlin.Any?>>

}
