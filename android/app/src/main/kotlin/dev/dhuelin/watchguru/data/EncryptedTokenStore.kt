package dev.dhuelin.watchguru.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.Instant

/**
 * The session, held in Keystore-backed encrypted preferences.
 *
 * Never plain SharedPreferences: on a rooted or backed-up device those are
 * readable, and these values are the user's whole account -- the refresh token
 * especially, which is good for thirty days rather than fifteen minutes.
 */
class EncryptedTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "watch-guru-auth",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * The stored session, or null.
     *
     * An app upgraded from the version that stored a bare provider token under
     * the same key lands here with an access token and no refresh token, and
     * gets null -- so it asks the user to sign in once. That is the right
     * outcome: the old value was a provider token, which the new code would
     * otherwise try to rotate at an endpoint that has never issued it.
     */
    override fun tokens(): Tokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        // A half-written session is no session. Reading one back would produce
        // a request with a token whose expiry is the epoch, which the
        // authenticator would then try to refresh with a token that isn't there.
        if (expiresAt == 0L) return null
        return Tokens(access, refresh, Instant.ofEpochSecond(expiresAt))
    }

    override fun save(tokens: Tokens) {
        // commit(), not apply(): the authenticator writes this from an OkHttp
        // thread and the retried request reads it immediately afterwards.
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT, tokens.accessTokenExpiresAt.epochSecond)
            .commit()
    }

    override fun clear() {
        // commit(), not apply(): sign-out must have taken effect before the
        // caller clears the rest of the local data and navigates away.
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES_AT)
            .commit()
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "access_token_expires_at"
    }
}
