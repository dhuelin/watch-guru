package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.AddToWatchlist
import dev.dhuelin.watchguru.api.models.TitleProgress
import dev.dhuelin.watchguru.api.models.UpdateWatchlistItem
import dev.dhuelin.watchguru.api.models.WatchlistItemResponse

interface WatchlistControllerApi {
    /**
     * POST api/v1/me/watchlist
     * 
     * 
     * Responses:
     *  - 201: Created
     *
     * @param addToWatchlist 
     * @return [WatchlistItemResponse]
     */
    @POST("api/v1/me/watchlist")
    suspend fun addToWatchlist(@Body addToWatchlist: AddToWatchlist): Response<WatchlistItemResponse>

    /**
     * GET api/v1/me/watchlist/titles/{titleId}/progress
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param titleId 
     * @return [TitleProgress]
     */
    @GET("api/v1/me/watchlist/titles/{titleId}/progress")
    suspend fun getTitleProgress(@Path("titleId") titleId: kotlin.Long): Response<TitleProgress>


    /**
    * enum for parameter status
    */
    @Serializable
    enum class StatusListWatchlist(val value: kotlin.String) {
        @SerialName(value = "WATCHLIST") WATCHLIST("WATCHLIST"),
        @SerialName(value = "WATCHING") WATCHING("WATCHING"),
        @SerialName(value = "COMPLETED") COMPLETED("COMPLETED"),
        @SerialName(value = "ON_HOLD") ON_HOLD("ON_HOLD"),
        @SerialName(value = "DROPPED") DROPPED("DROPPED")
    }

    /**
     * GET api/v1/me/watchlist
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param status  (optional)
     * @param page  (optional, default to 0)
     * @param size  (optional, default to 20)
     * @return [kotlin.collections.List<WatchlistItemResponse>]
     */
    @GET("api/v1/me/watchlist")
    suspend fun listWatchlist(@Query("status") status: StatusListWatchlist? = null, @Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 20): Response<kotlin.collections.List<WatchlistItemResponse>>

    /**
     * DELETE api/v1/me/watchlist/{itemId}
     * 
     * 
     * Responses:
     *  - 204: No Content
     *
     * @param itemId 
     * @return [Unit]
     */
    @DELETE("api/v1/me/watchlist/{itemId}")
    suspend fun removeFromWatchlist(@Path("itemId") itemId: kotlin.Long): Response<Unit>

    /**
     * PATCH api/v1/me/watchlist/{itemId}
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param itemId 
     * @param updateWatchlistItem 
     * @return [WatchlistItemResponse]
     */
    @PATCH("api/v1/me/watchlist/{itemId}")
    suspend fun updateWatchlistItem(@Path("itemId") itemId: kotlin.Long, @Body updateWatchlistItem: UpdateWatchlistItem): Response<WatchlistItemResponse>

}
