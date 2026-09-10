package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.NotificationSettingsResponse
import dev.dhuelin.watchguru.api.models.RegisterDevice
import dev.dhuelin.watchguru.api.models.UpdateNotificationSettings
import dev.dhuelin.watchguru.api.models.UpdateSeriesNotification

interface NotificationControllerApi {
    /**
     * GET api/v1/me/notifications
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [NotificationSettingsResponse]
     */
    @GET("api/v1/me/notifications")
    suspend fun getNotificationSettings(): Response<NotificationSettingsResponse>

    /**
     * POST api/v1/me/devices
     * 
     * 
     * Responses:
     *  - 204: No Content
     *
     * @param registerDevice 
     * @return [Unit]
     */
    @POST("api/v1/me/devices")
    suspend fun registerDevice(@Body registerDevice: RegisterDevice): Response<Unit>

    /**
     * DELETE api/v1/me/devices/{token}
     * 
     * 
     * Responses:
     *  - 204: No Content
     *
     * @param token 
     * @return [Unit]
     */
    @DELETE("api/v1/me/devices/{token}")
    suspend fun unregisterDevice(@Path("token") token: kotlin.String): Response<Unit>

    /**
     * PATCH api/v1/me/notifications
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param updateNotificationSettings 
     * @return [NotificationSettingsResponse]
     */
    @PATCH("api/v1/me/notifications")
    suspend fun updateNotificationSettings(@Body updateNotificationSettings: UpdateNotificationSettings): Response<NotificationSettingsResponse>

    /**
     * PUT api/v1/me/notifications/titles/{titleId}
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param titleId 
     * @param updateSeriesNotification 
     * @return [NotificationSettingsResponse]
     */
    @PUT("api/v1/me/notifications/titles/{titleId}")
    suspend fun updateSeriesNotification(@Path("titleId") titleId: kotlin.Long, @Body updateSeriesNotification: UpdateSeriesNotification): Response<NotificationSettingsResponse>

}
