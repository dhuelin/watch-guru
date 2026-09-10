package dev.dhuelin.watchguru.data

import java.time.Instant

/**
 * A session, as the client holds it.
 *
 * Both tokens together, because they are only meaningful as a pair: an access
 * token with no refresh token is a session that dies in fifteen minutes, and a
 * refresh token with no access token means a round trip before every first
 * request.
 *
 * @param accessTokenExpiresAt when to stop using [accessToken]. Derived from
 *   the server's `expiresIn` at the moment of receipt rather than trusted as an
 *   absolute time, because the device's clock may be wrong and the server's is
 *   the one that decides.
 */
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
) {
    /**
     * Whether the access token should be refreshed before use.
     *
     * The skew exists so a token that expires mid-flight is refreshed first
     * rather than producing a 401 the user waits through.
     */
    fun isExpired(now: Instant, skew: java.time.Duration = DEFAULT_SKEW): Boolean =
        !accessTokenExpiresAt.minus(skew).isAfter(now)

    companion object {
        val DEFAULT_SKEW: java.time.Duration = java.time.Duration.ofSeconds(30)
    }
}

/**
 * Where the session lives.
 *
 * <p>An interface rather than a concrete class so the networking layer can be
 * exercised without Android's Keystore. The production implementation is
 * [dev.dhuelin.watchguru.data.EncryptedTokenStore]; tests use an in-memory one.
 *
 * Implementations must be safe to call from any thread: the OkHttp interceptor
 * reads on whichever thread the request is on, and the authenticator writes
 * from another.
 */
interface TokenStore {

    /** The current session, or null when signed out. */
    fun tokens(): Tokens?

    fun save(tokens: Tokens)

    /**
     * Clears the session.
     *
     * Signing out must also clear cached local data -- a shared device must not
     * leak the previous user's watch history. See [LocalDataCleaner].
     */
    fun clear()
}

/** For tests and previews. */
class InMemoryTokenStore(initial: Tokens? = null) : TokenStore {

    @Volatile
    private var tokens: Tokens? = initial

    override fun tokens(): Tokens? = tokens

    override fun save(tokens: Tokens) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}
