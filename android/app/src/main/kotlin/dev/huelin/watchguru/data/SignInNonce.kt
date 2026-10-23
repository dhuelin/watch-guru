package dev.huelin.watchguru.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The one-time value that ties a provider sign-in to this exchange.
 *
 * Google is given only [hashed] and puts it in the ID token; [raw] goes to
 * Watch Guru alongside the token and nowhere else. A token that leaks without
 * its nonce cannot start a session, and the backend accepts each nonce once,
 * so a token cannot be replayed even with it.
 */
data class SignInNonce(val raw: String) {

    /** SHA-256 of [raw] as lowercase hex, which is what the backend compares against. */
    val hashed: String get() = sha256Hex(raw)

    companion object {
        private val random = SecureRandom()

        /** 32 random bytes, base64url without padding. */
        fun make(): SignInNonce {
            val bytes = ByteArray(32).also(random::nextBytes)
            return SignInNonce(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        }

        fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
