package dev.huelin.watchguru.di

import dev.huelin.watchguru.api.models.SearchResponse
import dev.huelin.watchguru.api.models.TitleResponse
import dev.huelin.watchguru.api.models.UpNextResponse
import dev.huelin.watchguru.api.models.UpdateWatchlistItem
import dev.huelin.watchguru.ui.detail.ratingUpdate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * That the Json every API call is decoded with can read the generated models.
 *
 * The models mark timestamps @Contextual, so a Json without the client's
 * adapters fails on the first response that carries one. Up Next did, and the
 * home screen showed "Something went wrong" over a perfectly good 200.
 */
class NetworkJsonTest {

    @Test
    fun `a response with a timestamp decodes`() {
        val body = """
            [{"titleId":3,"primaryTitle":"Deep Field","nextEpisodeId":41,
              "nextEpisodeCode":"S01E03","watchedEpisodes":2,"airedEpisodes":10,
              "lastWatchedAt":"2026-10-20T09:37:33.855035Z"}]
        """.trimIndent()

        val decoded = NetworkModule.json().decodeFromString<List<UpNextResponse>>(body)

        assertEquals(OffsetDateTime.parse("2026-10-20T09:37:33.855035Z"), decoded.single().lastWatchedAt)
    }

    @Test
    fun `a decimal sent as a JSON number decodes`() {
        // Unquoted, which is how the API sends it. The generated adapter asked
        // for a string here, and title detail, search and library all failed.
        val body = """
            {"page":1,"totalPages":1,"totalResults":1,
             "results":[{"providerId":9003,"title":"Paper Moons","titleType":"MOVIE",
                         "providerRating":8.7,"releaseDate":"2024-05-01"}]}
        """.trimIndent()

        val decoded = NetworkModule.json().decodeFromString<SearchResponse>(body)

        assertEquals(BigDecimal("8.7"), decoded.results.single().providerRating)
    }

    @Test
    fun `a title with both ratings keeps every digit`() {
        val body = """
            {"id":3,"providerId":8001,"primaryTitle":"Deep Field","titleType":"TV_SERIES",
             "availability":[],"genres":[],"providerRating":8.7,"imdbRating":7.95}
        """.trimIndent()

        val decoded = NetworkModule.json().decodeFromString<TitleResponse>(body)

        assertEquals(BigDecimal("8.7"), decoded.providerRating)
        // Not 7.949999... -- the value never passes through a double.
        assertEquals(BigDecimal("7.95"), decoded.imdbRating)
    }

    @Test
    fun `a decimal cached as a string still decodes`() {
        // Snapshots written before the fix hold quoted decimals.
        val body = """
            {"id":3,"providerId":8001,"primaryTitle":"Deep Field","titleType":"TV_SERIES",
             "availability":[],"genres":[],"providerRating":"8.7"}
        """.trimIndent()

        val decoded = NetworkModule.json().decodeFromString<TitleResponse>(body)

        assertEquals(BigDecimal("8.7"), decoded.providerRating)
    }

    @Test
    fun `a rating is sent as a number, not a string`() {
        val encoded = NetworkModule.json().encodeToString(
            UpdateWatchlistItem.serializer(),
            UpdateWatchlistItem(rating = BigDecimal(7)),
        )

        assertEquals("""{"rating":7}""", encoded)
    }

    @Test
    fun `clearing a rating is sent as clearRating, not as a missing rating`() {
        val encoded = NetworkModule.json().encodeToString(
            UpdateWatchlistItem.serializer(),
            ratingUpdate(null),
        )

        assertEquals("""{"clearRating":true}""", encoded)
    }

    @Test
    fun `a search hit carries the caller's library entry`() {
        val body = """
            {"results":[{"providerId":8001,"titleType":"MOVIE","title":"Deep Field",
              "library":{"itemId":3,"status":"WATCHING"}}],
             "page":1,"totalPages":1,"totalResults":1}
        """.trimIndent()

        val decoded = NetworkModule.json().decodeFromString<SearchResponse>(body)

        assertEquals(3L, decoded.results.single().library?.itemId)
    }
}
