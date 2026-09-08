package dev.dhuelin.watchguru.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The token, held in Keystore-backed encrypted preferences.
 *
 * Never plain SharedPreferences: on a rooted or backed-up device those are
 * readable, and this value is the user's whole account.
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

    override fun token(): String? = prefs.getString(KEY_TOKEN, null)

    override fun save(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    override fun clear() {
        // commit(), not apply(): sign-out must have taken effect before the
        // caller clears the rest of the local data and navigates away.
        prefs.edit().remove(KEY_TOKEN).commit()
    }

    private companion object {
        const val KEY_TOKEN = "access_token"
    }
}
