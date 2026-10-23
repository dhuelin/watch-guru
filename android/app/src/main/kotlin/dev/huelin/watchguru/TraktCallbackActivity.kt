package dev.huelin.watchguru

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import dev.huelin.watchguru.data.TraktCallback
import dev.huelin.watchguru.data.TraktCallbacks
import javax.inject.Inject

/**
 * Where the browser lands after Trakt: `watchguru://trakt/callback`.
 *
 * Hands the code and state over and goes straight back to the app, where the
 * Trakt screen finishes the flow with this app's own session. That last step
 * is the security of the whole thing -- the server connects Trakt only for the
 * account that started the flow, so a link forwarded to somebody else ends
 * here on their phone and connects nothing.
 *
 * A separate activity rather than an intent filter on [MainActivity], so the
 * main activity keeps its launch mode and its back stack, and a stray link
 * cannot start a second copy of the whole app on top of the first.
 */
@AndroidEntryPoint
class TraktCallbackActivity : ComponentActivity() {

    @Inject
    lateinit var callbacks: TraktCallbacks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let(TraktCallback::parse)?.let(callbacks::offer)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
