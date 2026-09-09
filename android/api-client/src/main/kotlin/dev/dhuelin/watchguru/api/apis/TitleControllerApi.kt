package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.SearchResponse
import dev.dhuelin.watchguru.api.models.SeasonsResponse
import dev.dhuelin.watchguru.api.models.TitleResponse

interface TitleControllerApi {
    /**
     * GET api/v1/titles/{titleId}/seasons
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param titleId 
     * @return [SeasonsResponse]
     */
    @GET("api/v1/titles/{titleId}/seasons")
    suspend fun getSeasons(@Path("titleId") titleId: kotlin.Long): Response<SeasonsResponse>

    /**
     * GET api/v1/titles/{titleId}
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param titleId 
     * @param region  (optional)
     * @return [TitleResponse]
     */
    @GET("api/v1/titles/{titleId}")
    suspend fun getTitle(@Path("titleId") titleId: kotlin.Long, @Query("region") region: kotlin.String? = null): Response<TitleResponse>


    /**
    * enum for parameter titleType
    */
    @Serializable
    enum class TitleTypeImportTitle(val value: kotlin.String) {
        @SerialName(value = "MOVIE") MOVIE("MOVIE"),
        @SerialName(value = "TV_SERIES") TV_SERIES("TV_SERIES")
    }

    /**
     * POST api/v1/titles/import
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param titleType 
     * @param providerId 
     * @param language  (optional)
     * @return [TitleResponse]
     */
    @POST("api/v1/titles/import")
    suspend fun importTitle(@Query("titleType") titleType: TitleTypeImportTitle, @Query("providerId") providerId: kotlin.Long, @Query("language") language: kotlin.String? = null): Response<TitleResponse>

    /**
     * GET api/v1/titles/search
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param query 
     * @param page  (optional, default to 1)
     * @param language  (optional)
     * @return [SearchResponse]
     */
    @GET("api/v1/titles/search")
    suspend fun searchTitles(@Query("query") query: kotlin.String, @Query("page") page: kotlin.Int? = 1, @Query("language") language: kotlin.String? = null): Response<SearchResponse>

}
