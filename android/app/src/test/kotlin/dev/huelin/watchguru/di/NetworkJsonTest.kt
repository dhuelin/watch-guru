package dev.huelin.watchguru.di

import dev.huelin.watchguru.api.models.UpNextResponse
import org.junit.Assert.assertEquals
import org.junit.Test
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
}
