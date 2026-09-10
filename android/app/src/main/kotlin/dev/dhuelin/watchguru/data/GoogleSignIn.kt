package dev.dhuelin.watchguru.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Obtains a Google ID token via Credential Manager.
 *
 * The backend accepts the provider's ID token directly as a bearer token, so
 * there is no exchange step and no registration call: the first valid token
 * provisions an account.
 *
 * Credential Manager rather than the deprecated `GoogleSignInClient`. The
 * token's audience is the **web** client id, not the Android one -- that is
 * what the backend's `WATCH_GURU_AUTH_AUDIENCES` must contain, and getting it
 * wrong produces a token the API correctly refuses.
 */
class GoogleSignIn(private val webClientId: String) {

    sealed interface Result {
        data class Success(val idToken: String) : Result

        /** The user dismissed the sheet. Not an error worth showing. */
        data object Cancelled : Result

        /** No Google account on the device, or none the user chose to offer. */
        data object NoAccount : Result

        data class Failed(val message: String?) : Result
    }

    /**
     * @param activityContext must be an Activity context. Credential Manager
     *   presents a bottom sheet, so the application context will not do; this
     *   is why the context is a parameter rather than a constructor argument.
     */
    suspend fun signIn(activityContext: Context): Result {
        if (webClientId.isBlank()) {
            return Result.Failed(
                "No Google web client id configured. Set watchguru.googleWebClientId " +
                    "in gradle.properties or pass it as a Gradle property.",
            )
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            // False, so a user with no previously authorised account still sees
            // the chooser instead of an immediate NoCredentialException on
            // their very first sign-in.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        return try {
            val response = CredentialManager.create(activityContext).getCredential(
                context = activityContext,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )

            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                // A credential of an unexpected type is a configuration problem
                // rather than something the user can act on.
                Result.Failed("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.NoAccount
        } catch (e: GetCredentialException) {
            Result.Failed(e.message)
        }
    }
}
