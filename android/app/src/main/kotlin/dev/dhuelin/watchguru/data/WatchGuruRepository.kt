package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.apis.MeControllerApi
import dev.dhuelin.watchguru.api.apis.TitleControllerApi
import dev.dhuelin.watchguru.api.apis.WatchHistoryControllerApi
import dev.dhuelin.watchguru.api.apis.WatchlistControllerApi
import dev.dhuelin.watchguru.api.models.AddToWatchlist
import dev.dhuelin.watchguru.api.models.BulkMarkResponse
import dev.dhuelin.watchguru.api.models.LogEpisodeWatched
import dev.dhuelin.watchguru.api.models.MarkWatchedUpTo
import dev.dhuelin.watchguru.api.models.SeasonsResponse
import dev.dhuelin.watchguru.api.models.UpNextResponse
import dev.dhuelin.watchguru.api.models.SearchResponse
import dev.dhuelin.watchguru.api.models.TitleProgress
import dev.dhuelin.watchguru.api.models.TitleResponse
import dev.dhuelin.watchguru.api.models.UpdateProfile
import dev.dhuelin.watchguru.api.models.UpdateWatchlistItem
import dev.dhuelin.watchguru.api.models.UserResponse
import dev.dhuelin.watchguru.api.models.WatchEventResponse
import dev.dhuelin.watchguru.api.models.WatchStats
import dev.dhuelin.watchguru.api.models.WatchlistItemResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

/**
 * The app's single door to the backend.
 *
 * Screens do not touch the generated API interfaces. Everything here returns
 * [ApiResult], so no screen ever handles an HTTP status code or a raw
 * exception, and the mapping from "what went wrong" to "what the user is told"
 * is made once rather than per screen.
 *
 * Contains no Android types on purpose: this is where the app's logic lives,
 * and it stays unit-testable on a plain JVM.
 */
class WatchGuruRepository(
    private val titles: TitleControllerApi,
    private val watchlist: WatchlistControllerApi,
    private val history: WatchHistoryControllerApi,
    private val me: MeControllerApi,
    private val io: CoroutineDispatcher,
) {

    suspend fun profile(): ApiResult<UserResponse> = call { me.getProfile() }

    suspend fun updateProfile(update: UpdateProfile): ApiResult<UserResponse> =
        call { me.updateProfile(update) }

    suspend fun deleteAccount(): ApiResult<Unit> = call { me.deleteAccount() }

    /**
     * Searches the catalogue.
     *
     * The backend suppresses queries shorter than two characters and answers
     * them with an empty page rather than an error, so the UI does not need a
     * matching rule -- but it debounces anyway, because a request per keystroke
     * is wasteful even when it is cheap.
     */
    suspend fun search(query: String, page: Int = 1): ApiResult<SearchResponse> =
        call { titles.searchTitles(query = query, page = page, language = null) }

    suspend fun title(titleId: Long, region: String? = null): ApiResult<TitleResponse> =
        call { titles.getTitle(titleId = titleId, region = region) }

    suspend fun library(
        status: WatchlistControllerApi.StatusListWatchlist? = null,
        page: Int = 0,
        size: Int = 20,
    ): ApiResult<List<WatchlistItemResponse>> =
        call { watchlist.listWatchlist(status = status, page = page, size = size) }

    suspend fun addToLibrary(request: AddToWatchlist): ApiResult<WatchlistItemResponse> =
        call { watchlist.addToWatchlist(request) }

    suspend fun updateLibraryItem(
        itemId: Long,
        update: UpdateWatchlistItem,
    ): ApiResult<WatchlistItemResponse> = call { watchlist.updateWatchlistItem(itemId, update) }

    suspend fun removeFromLibrary(itemId: Long): ApiResult<Unit> =
        call { watchlist.removeFromWatchlist(itemId) }

    suspend fun progress(titleId: Long): ApiResult<TitleProgress> =
        call { watchlist.getTitleProgress(titleId) }

    suspend fun markEpisodeWatched(request: LogEpisodeWatched): ApiResult<WatchEventResponse> =
        call { history.logEpisodeWatched(request) }

    /** Every season of a series with the caller's watched state already folded in. */
    suspend fun seasons(titleId: Long): ApiResult<SeasonsResponse> =
        call { titles.getSeasons(titleId) }

    /**
     * Marks everything up to and including one episode.
     *
     * One request rather than one per episode, and idempotent on the server:
     * a double tap cannot turn a season into rewatches.
     */
    suspend fun markWatchedUpTo(episodeId: Long): ApiResult<BulkMarkResponse> =
        call { history.markWatchedUpTo(MarkWatchedUpTo(episodeId = episodeId)) }

    /**
     * Removes an episode from the watched history entirely.
     *
     * Idempotent server-side, so a retry after a dropped response is safe.
     */
    suspend fun unmarkEpisode(episodeId: Long): ApiResult<Unit> =
        call { history.unmarkEpisode(episodeId) }

    /** Deletes one history entry, leaving other rewatches of it intact. */
    suspend fun deleteWatchEvent(eventId: Long): ApiResult<Unit> =
        call { history.deleteWatchEvent(eventId) }

    /** The next unwatched episode of every series in progress. */
    suspend fun upNext(limit: Int = 20): ApiResult<List<UpNextResponse>> =
        call { history.getUpNext(limit) }

    suspend fun stats(months: Int = 12): ApiResult<WatchStats> = call { history.getStats(months) }

    suspend fun history(page: Int = 0, size: Int = 50): ApiResult<List<WatchEventResponse>> =
        call { history.getHistory(page = page, size = size) }

    /**
     * Runs one call and turns everything it can do into an [ApiResult].
     *
     * A 204 with no body is a success carrying [Unit]; Retrofit gives null for
     * the body there, which is why the null case is not treated as a failure
     * for [Unit] results.
     */
    private suspend fun <T> call(block: suspend () -> Response<T>): ApiResult<T> =
        withContext(io) {
            try {
                val response = block()
                if (response.isSuccessful) {
                    @Suppress("UNCHECKED_CAST")
                    val body = response.body() ?: Unit as T
                    ApiResult.Success(body)
                } else {
                    failureFor(response.code(), response.message())
                }
            } catch (e: IOException) {
                // No route to host, DNS failure, timeout: all "offline" as far
                // as the user is concerned.
                ApiResult.Failure.Offline
            } catch (e: Exception) {
                ApiResult.Failure.Unexpected(null, e.message)
            }
        }

    private fun failureFor(status: Int, message: String?): ApiResult.Failure = when (status) {
        401, 403 -> ApiResult.Failure.Unauthorised
        404 -> ApiResult.Failure.NotFound
        // The backend reports an unreachable or unconfigured TMDB as 502/503.
        // It keeps serving the user's own data, so this must not read as a
        // total outage.
        502, 503 -> ApiResult.Failure.Upstream
        else -> ApiResult.Failure.Unexpected(status, message)
    }
}
