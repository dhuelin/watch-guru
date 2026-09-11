package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.apis.MeControllerApi
import dev.dhuelin.watchguru.api.apis.TitleControllerApi
import dev.dhuelin.watchguru.api.apis.WatchHistoryControllerApi
import dev.dhuelin.watchguru.api.apis.WatchlistControllerApi
import dev.dhuelin.watchguru.api.models.SearchResponse
import dev.dhuelin.watchguru.api.models.UserResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import retrofit2.Response
import java.io.IOException
import org.junit.Test

/**
 * How backend outcomes become things the UI can act on.
 *
 * The mapping matters more than it looks. The backend deliberately keeps
 * serving a user's own library when TMDB is down, so "the catalogue is
 * unavailable" and "you are offline" are different situations with different
 * messages -- and only one of them means the user cannot see their own data.
 */
class WatchGuruRepositoryTest {

    /** Fails every call the same way, so one test can pin one behaviour. */
    private class StubTitles(
        private val responder: suspend () -> Response<SearchResponse>,
    ) : TitleControllerApi {
        override suspend fun searchTitles(query: String, page: Int?, language: String?) = responder()
        override suspend fun getTitle(titleId: Long, region: String?) = notUsed()
        override suspend fun importTitle(
            titleType: TitleControllerApi.TitleTypeImportTitle,
            providerId: Long,
            language: String?,
        ) = notUsed()
        override suspend fun getSeasons(titleId: Long) = notUsed()
        private fun notUsed(): Nothing = throw UnsupportedOperationException("not used in this test")
    }

    private class StubMe(private val responder: suspend () -> Response<UserResponse>) : MeControllerApi {
        override suspend fun getProfile() = responder()
        override suspend fun updateProfile(updateProfile: dev.dhuelin.watchguru.api.models.UpdateProfile) = responder()
        override suspend fun deleteAccount(): Response<Unit> = Response.success(Unit)
    }

    private fun repositoryWith(
        titles: TitleControllerApi = StubTitles { Response.success(emptyPage()) },
        me: MeControllerApi = StubMe { Response.success(user()) },
    ) = WatchGuruRepository(
        titles = titles,
        watchlist = object : WatchlistControllerApi {
            override suspend fun addToWatchlist(addToWatchlist: dev.dhuelin.watchguru.api.models.AddToWatchlist) = fail()
            override suspend fun getTitleProgress(titleId: Long) = fail()
            override suspend fun listWatchlist(
                status: WatchlistControllerApi.StatusListWatchlist?,
                page: Int?,
                size: Int?,
            ) = fail()
            override suspend fun removeFromWatchlist(itemId: Long) = fail()
            override suspend fun updateWatchlistItem(
                itemId: Long,
                updateWatchlistItem: dev.dhuelin.watchguru.api.models.UpdateWatchlistItem,
            ) = fail()
            private fun fail(): Nothing = throw UnsupportedOperationException()
        },
        history = object : WatchHistoryControllerApi {
            override suspend fun getHistory(page: Int?, size: Int?) = fail()
            override suspend fun getStats(
                months: Int?,
                period: WatchHistoryControllerApi.PeriodGetStats?,
            ) = fail()
            override suspend fun logEpisodeWatched(
                logEpisodeWatched: dev.dhuelin.watchguru.api.models.LogEpisodeWatched,
            ) = fail()
            override suspend fun logMovieWatched(
                logMovieWatched: dev.dhuelin.watchguru.api.models.LogMovieWatched,
            ) = fail()
            override suspend fun getUpNext(limit: Int?) = fail()
            override suspend fun unmarkEpisode(episodeId: Long) = fail()
            override suspend fun deleteWatchEvent(eventId: Long) = fail()
            override suspend fun markWatchedUpTo(
                markWatchedUpTo: dev.dhuelin.watchguru.api.models.MarkWatchedUpTo,
            ) = fail()
            private fun fail(): Nothing = throw UnsupportedOperationException()
        },
        me = me,
        io = Dispatchers.Unconfined,
    )

    private fun emptyPage() = SearchResponse(results = emptyList(), page = 1, totalPages = 0, totalResults = 0)

    private fun user() = UserResponse(
        displayName = "Viewer", email = "viewer@example.com", id = 1L,
        language = "en-GB", region = "CH", timeZone = "Europe/Zurich",
    )

    private fun <T> errorResponse(code: Int): Response<T> =
        Response.error(code, "".toResponseBody("application/problem+json".toMediaType()))

    @Test
    fun `a successful call returns its body`() = runTest {
        val result = repositoryWith().search("breaking bad")

        assertTrue(result is ApiResult.Success)
        assertEquals(0L, (result as ApiResult.Success).value.totalResults)
    }

    @Test
    fun `a network failure reads as offline, not as an error`() = runTest {
        val result = repositoryWith(
            titles = StubTitles { throw IOException("no route to host") },
        ).search("breaking bad")

        // Timeouts, DNS failures and unreachable hosts are all one thing to a
        // user standing in a lift.
        assertEquals(ApiResult.Failure.Offline, result)
    }

    @Test
    fun `401 asks for re-authentication rather than showing an error`() = runTest {
        val result = repositoryWith(titles = StubTitles { errorResponse(401) }).search("x")

        assertEquals(ApiResult.Failure.Unauthorised, result)
    }

    @Test
    fun `403 is treated as unauthorised too`() = runTest {
        // The backend answers 404 rather than 403 for another user's item, so a
        // 403 here means the token itself is not acceptable.
        val result = repositoryWith(titles = StubTitles { errorResponse(403) }).search("x")

        assertEquals(ApiResult.Failure.Unauthorised, result)
    }

    @Test
    fun `a TMDB outage is distinct from being offline`() = runTest {
        // The backend reports an unreachable or unconfigured TMDB as 502/503
        // while still serving the user's own library. Collapsing this into
        // Offline would tell people their data is gone when it is not.
        assertEquals(ApiResult.Failure.Upstream, repositoryWith(titles = StubTitles { errorResponse(502) }).search("x"))
        assertEquals(ApiResult.Failure.Upstream, repositoryWith(titles = StubTitles { errorResponse(503) }).search("x"))
    }

    @Test
    fun `404 is its own case`() = runTest {
        assertEquals(ApiResult.Failure.NotFound, repositoryWith(titles = StubTitles { errorResponse(404) }).search("x"))
    }

    @Test
    fun `an unrecognised status keeps its code for the bug report`() = runTest {
        val result = repositoryWith(titles = StubTitles { errorResponse(418) }).search("x")

        assertTrue(result is ApiResult.Failure.Unexpected)
        assertEquals(418, (result as ApiResult.Failure.Unexpected).status)
    }

    @Test
    fun `a 204 with no body is a success, not a parse failure`() = runTest {
        // Retrofit hands back a null body for 204. Account deletion and item
        // removal both return 204, so treating null as failure would report
        // every successful delete as an error.
        val result = repositoryWith().deleteAccount()

        assertTrue(result is ApiResult.Success)
    }

    @Test
    fun `map transforms a success and passes a failure through untouched`() {
        val ok: ApiResult<Int> = ApiResult.Success(2)
        assertEquals(ApiResult.Success(4), ok.map { it * 2 })

        val failed: ApiResult<Int> = ApiResult.Failure.Offline
        assertEquals(ApiResult.Failure.Offline, failed.map { it * 2 })
    }
}
