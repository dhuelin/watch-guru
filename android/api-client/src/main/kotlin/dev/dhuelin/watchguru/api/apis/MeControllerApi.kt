package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.UpdateProfile
import dev.dhuelin.watchguru.api.models.UserResponse

interface MeControllerApi {
    /**
     * DELETE api/v1/me
     * 
     * 
     * Responses:
     *  - 204: No Content
     *
     * @return [Unit]
     */
    @DELETE("api/v1/me")
    suspend fun deleteAccount(): Response<Unit>

    /**
     * GET api/v1/me
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @return [UserResponse]
     */
    @GET("api/v1/me")
    suspend fun getProfile(): Response<UserResponse>

    /**
     * PATCH api/v1/me
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param updateProfile 
     * @return [UserResponse]
     */
    @PATCH("api/v1/me")
    suspend fun updateProfile(@Body updateProfile: UpdateProfile): Response<UserResponse>

}
