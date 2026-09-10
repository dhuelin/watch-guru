package dev.dhuelin.watchguru.data

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Renewing a session on the server's 401.
 *
 * The concurrency test is the one that matters. Refresh tokens rotate, and the
 * backend treats a token presented twice as theft and revokes the whole
 * session -- so two requests that 401 at the same moment must produce one
 * refresh, not two. Getting that wrong signs the user out precisely when the
 * app is busiest, which is also when it is hardest to reproduce.
 */
class SessionAuthenticatorTest {

    private fun tokens(access: String, refresh: String = "refresh-1") =
        Tokens(access, refresh, Instant.now().plusSeconds(900))

    private fun unauthorised(request: Request, prior: Response? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body("".toResponseBody(null))
            .apply { if (prior != null) priorResponse(bodiless(prior)) }
            .build()

    /**
     * A prior response as OkHttp actually hands one over: its body has already
     * been consumed, and the builder rejects one that still has a body.
     */
    private fun bodiless(response: Response): Response =
        response.newBuilder().body(null).build()

    private fun request(token: String?): Request =
        Request.Builder()
            .url("https://api.watch-guru.test/api/v1/me")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

    @Test
    fun `refreshes once and retries with the new token`() {
        val store = InMemoryTokenStore(tokens("old-access"))
        val calls = AtomicInteger()
        val authenticator = SessionAuthenticator(
            tokens = store,
            refresh = { presented ->
                calls.incrementAndGet()
                assertEquals("refresh-1", presented)
                tokens("new-access", "refresh-2")
            },
            onSessionLost = { throw AssertionError("session should not be lost") },
        )

        val retry = authenticator.authenticate(null, unauthorised(request("old-access")))

        assertEquals("Bearer new-access", retry?.header("Authorization"))
        assertEquals(1, calls.get())
        // The rotated refresh token is what the next renewal must present.
        assertEquals("refresh-2", store.tokens()?.refreshToken)
    }

    @Test
    fun `gives up and signs out when the refresh token is refused`() {
        val store = InMemoryTokenStore(tokens("old-access"))
        var lost = false
        val authenticator = SessionAuthenticator(
            tokens = store,
            refresh = { null },
            onSessionLost = { lost = true },
        )

        val retry = authenticator.authenticate(null, unauthorised(request("old-access")))

        // Null hands the 401 back to the caller rather than looping.
        assertNull(retry)
        assertTrue(lost)
    }

    @Test
    fun `does not retry a request that already carried a fresh token`() {
        // A retry that 401s too means the new token was rejected as well.
        // Refreshing again cannot help, and would loop.
        val store = InMemoryTokenStore(tokens("access"))
        val calls = AtomicInteger()
        val authenticator = SessionAuthenticator(
            tokens = store,
            refresh = { calls.incrementAndGet(); tokens("another") },
            onSessionLost = {},
        )

        val first = unauthorised(request("access"))
        val second = unauthorised(request("access"), prior = first)

        assertNull(authenticator.authenticate(null, second))
        assertEquals(0, calls.get())
    }

    @Test
    fun `does nothing when there is no session`() {
        val authenticator = SessionAuthenticator(
            tokens = InMemoryTokenStore(null),
            refresh = { throw AssertionError("must not refresh without a session") },
            onSessionLost = {},
        )

        assertNull(authenticator.authenticate(null, unauthorised(request(null))))
    }

    @Test
    fun `concurrent failures cause exactly one refresh`() {
        val store = InMemoryTokenStore(tokens("old-access"))
        val refreshes = AtomicInteger()
        val start = CountDownLatch(1)

        val authenticator = SessionAuthenticator(
            tokens = store,
            refresh = {
                refreshes.incrementAndGet()
                // Widen the window a real network call would occupy, so a
                // missing lock actually shows up rather than passing by luck.
                Thread.sleep(50)
                tokens("new-access", "refresh-2")
            },
            onSessionLost = { throw AssertionError("session should not be lost") },
        )

        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val results = (1..threads).map {
            pool.submit<Request?> {
                start.await()
                authenticator.authenticate(null, unauthorised(request("old-access")))
            }
        }
        start.countDown()
        val retries = results.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        // One refresh, and every waiting request retried with its result.
        assertEquals(1, refreshes.get())
        retries.forEach { assertEquals("Bearer new-access", it?.header("Authorization")) }
    }

    @Test
    fun `a request holding a stale token retries with the current one without refreshing`() {
        // This is the queued-request case: the session was already renewed
        // while this request was in flight, so spending another refresh token
        // would be both wasteful and, because they rotate, dangerous.
        val store = InMemoryTokenStore(tokens("current-access", "refresh-2"))
        val authenticator = SessionAuthenticator(
            tokens = store,
            refresh = { throw AssertionError("must not refresh; the store is already current") },
            onSessionLost = {},
        )

        val retry = authenticator.authenticate(null, unauthorised(request("stale-access")))

        assertEquals("Bearer current-access", retry?.header("Authorization"))
    }
}

/** [Tokens] expiry arithmetic, which decides when a refresh is even attempted. */
class TokensTest {

    @Test
    fun `expiry accounts for skew so a token is not used at the last moment`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val session = Tokens("a", "r", now.plusSeconds(20))

        // Twenty seconds left, thirty seconds of skew: treat it as expired
        // rather than send a request that arrives after it dies.
        assertTrue(session.isExpired(now))
    }

    @Test
    fun `a token with plenty of life left is not expired`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertTrue(!Tokens("a", "r", now.plusSeconds(900)).isExpired(now))
    }

    @Test
    fun `expiresIn is converted against the moment the response arrived`() {
        // Not against an absolute server timestamp: this device's clock may be
        // wrong, and the difference would be applied to every refresh.
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val session = tokensFrom("a", "r", expiresIn = 900, now = now)

        assertEquals(now.plusSeconds(900), session.accessTokenExpiresAt)
    }
}
