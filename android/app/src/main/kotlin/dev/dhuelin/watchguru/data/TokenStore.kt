package dev.dhuelin.watchguru.data

/**
 * Where the OIDC access token lives.
 *
 * <p>An interface rather than a concrete class so the networking layer can be
 * exercised without Android's Keystore. The production implementation is
 * [dev.dhuelin.watchguru.data.EncryptedTokenStore]; tests use an in-memory one.
 *
 * Implementations must be safe to call from any thread: the OkHttp interceptor
 * reads the token on whichever thread the request happens to be on.
 */
interface TokenStore {

    /** The current bearer token, or null when signed out. */
    fun token(): String?

    fun save(token: String)

    /**
     * Clears the token.
     *
     * Signing out must also clear cached user data elsewhere -- a shared device
     * must not leak the previous user's watch history.
     */
    fun clear()
}

/** For tests and previews. */
class InMemoryTokenStore(initial: String? = null) : TokenStore {

    @Volatile
    private var token: String? = initial

    override fun token(): String? = token

    override fun save(token: String) {
        this.token = token
    }

    override fun clear() {
        token = null
    }
}
