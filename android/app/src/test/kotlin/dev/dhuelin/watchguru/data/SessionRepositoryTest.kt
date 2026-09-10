package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.apis.AuthenticationApi
import dev.dhuelin.watchguru.api.models.ExchangeToken
import dev.dhuelin.watchguru.api.models.RefreshSession
import dev.dhuelin.watchguru.api.models.SessionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Exchanging a provider sign-in for a session, and renewing one.
 *
 * The conversion of `expiresIn` is the part worth pinning: it is done against
 * the moment the response arrived, not against an absolute timestamp from the
 * server, because a device whose clock is wrong would otherwise apply that
 * error to every renewal for the life of the session.
 */
class SessionRepositoryTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private class StubAuth(
        private val onCreate: suspend (ExchangeToken) -> Response<SessionResponse> = { notUsed() },
        private val onRefresh: suspend (RefreshSession) -> Response<SessionResponse> = { notUsed() },
        private val onEnd: suspend (RefreshSession) -> Response<Unit> = { notUsed() },
    ) : AuthenticationApi {
        override suspend fun createSession(exchangeToken: ExchangeToken) = onCreate(exchangeToken)
        override suspend fun refreshSession(refreshSession: RefreshSession) = onRefresh(refreshSession)
        override suspend fun endSession(refreshSession: RefreshSession) = onEnd(refreshSession)

        private companion object {
            fun notUsed(): Nothing = throw UnsupportedOperationException("not used in this test")
        }
    }

    private fun session(access: String, refresh: String, expiresIn: Long) = SessionResponse(
        accessToken = access,
        tokenType = "Bearer",
        expiresIn = expiresIn,
        refreshToken = refresh,
        refreshTokenExpiresAt = OffsetDateTime.ofInstant(
            Instant.parse("2026-01-31T12:00:00Z"), ZoneOffset.UTC,
        ),
    )

    private fun repository(api: AuthenticationApi) =
        SessionRepository(api, Dispatchers.Unconfined, clock = { now })

    @Test
    fun `exchange returns tokens with expiry measured from receipt`() = runTest {
        var sent: String? = null
        val repository = repository(StubAuth(onCreate = {
            sent = it.providerToken
            Response.success(session("access-1", "refresh-1", expiresIn = 900))
        }))

        val result = repository.exchange("google-id-token")

        assertEquals("google-id-token", sent)
        assertTrue(result is ApiResult.Success)
        val tokens = (result as ApiResult.Success).value
        assertEquals("access-1", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
        assertEquals(now.plusSeconds(900), tokens.accessTokenExpiresAt)
    }

    @Test
    fun `a rejected provider token is a failure, not a crash`() = runTest {
        // The realistic cause is a configuration mismatch: the app was built
        // with a client id the backend's audience list does not contain.
        val repository = repository(StubAuth(onCreate = {
            Response.error(401, "".toResponseBody("application/json".toMediaType()))
        }))

        assertTrue(repository.exchange("token") is ApiResult.Failure.Unauthorised)
    }

    @Test
    fun `being offline during exchange is reported as offline`() = runTest {
        val repository = repository(StubAuth(onCreate = { throw IOException("no route to host") }))

        assertTrue(repository.exchange("token") is ApiResult.Failure.Offline)
    }

    @Test
    fun `refresh presents the stored token and returns the rotated pair`() = runTest {
        var presented: String? = null
        val repository = repository(StubAuth(onRefresh = {
            presented = it.refreshToken
            Response.success(session("access-2", "refresh-2", expiresIn = 900))
        }))

        val tokens = repository.refresh("refresh-1")

        assertEquals("refresh-1", presented)
        assertEquals("access-2", tokens?.accessToken)
        // The rotated token, not the one presented: storing the old one would
        // spend it twice on the next renewal, which the server reads as theft.
        assertEquals("refresh-2", tokens?.refreshToken)
    }

    @Test
    fun `a refused refresh returns null rather than a failure to interpret`() = runTest {
        // Its only caller is the authenticator, which has two outcomes to act
        // on: a new session, or none.
        val repository = repository(StubAuth(onRefresh = {
            Response.error(401, "".toResponseBody("application/json".toMediaType()))
        }))

        assertNull(repository.refresh("revoked"))
    }

    @Test
    fun `logout does not throw when the server cannot be reached`() = runTest {
        // Sign-out has already cleared the local tokens by this point. A user
        // who taps sign out on a plane must still end up signed out.
        val repository = repository(StubAuth(onEnd = { throw IOException("offline") }))

        repository.logout("refresh-1")
    }
}
