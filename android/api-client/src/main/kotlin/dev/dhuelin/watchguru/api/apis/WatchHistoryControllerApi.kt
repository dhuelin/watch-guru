package dev.dhuelin.watchguru.api.apis

import dev.dhuelin.watchguru.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import dev.dhuelin.watchguru.api.models.BulkMarkResponse
import dev.dhuelin.watchguru.api.models.LogEpisodeWatched
import dev.dhuelin.watchguru.api.models.LogMovieWatched
import dev.dhuelin.watchguru.api.models.MarkWatchedUpTo
import dev.dhuelin.watchguru.api.models.UpNextResponse
import dev.dhuelin.watchguru.api.models.WatchEventResponse
import dev.dhuelin.watchguru.api.models.WatchStats

interface WatchHistoryControllerApi {
    /**
     * GET api/v1/me/history
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param page  (optional, default to 0)
     * @param size  (optional, default to 50)
     * @return [kotlin.collections.List<WatchEventResponse>]
     */
    @GET("api/v1/me/history")
    suspend fun getHistory(@Query("page") page: kotlin.Int? = 0, @Query("size") size: kotlin.Int? = 50): Response<kotlin.collections.List<WatchEventResponse>>

    /**
     * GET api/v1/me/stats
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param months  (optional, default to 12)
     * @return [WatchStats]
     */
    @GET("api/v1/me/stats")
    suspend fun getStats(@Query("months") months: kotlin.Int? = 12): Response<WatchStats>

    /**
     * GET api/v1/me/up-next
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param limit  (optional, default to 20)
     * @return [kotlin.collections.List<UpNextResponse>]
     */
    @GET("api/v1/me/up-next")
    suspend fun getUpNext(@Query("limit") limit: kotlin.Int? = 20): Response<kotlin.collections.List<UpNextResponse>>

    /**
     * POST api/v1/me/watch-events/episode
     * 
     * 
     * Responses:
     *  - 201: Created
     *
     * @param logEpisodeWatched 
     * @return [WatchEventResponse]
     */
    @POST("api/v1/me/watch-events/episode")
    suspend fun logEpisodeWatched(@Body logEpisodeWatched: LogEpisodeWatched): Response<WatchEventResponse>

    /**
     * POST api/v1/me/watch-events/movie
     * 
     * 
     * Responses:
     *  - 201: Created
     *
     * @param logMovieWatched 
     * @return [WatchEventResponse]
     */
    @POST("api/v1/me/watch-events/movie")
    suspend fun logMovieWatched(@Body logMovieWatched: LogMovieWatched): Response<WatchEventResponse>

    /**
     * POST api/v1/me/watch-events/episodes/up-to
     * 
     * 
     * Responses:
     *  - 200: OK
     *
     * @param markWatchedUpTo 
     * @return [BulkMarkResponse]
     */
    @POST("api/v1/me/watch-events/episodes/up-to")
    suspend fun markWatchedUpTo(@Body markWatchedUpTo: MarkWatchedUpTo): Response<BulkMarkResponse>

}
