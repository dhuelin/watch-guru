package dev.huelin.watchguru.data

import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference

/** What Trakt handed back at the end of an authorisation. */
data class TraktCallback(val code: String, val state: String) {

    companion object {
        const val SCHEME = "watchguru"

        /**
         * The code and state from `watchguru://trakt/callback?code=…&state=…`,
         * or null for anything else.
         */
        fun parse(uri: String): TraktCallback? {
            val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
            if (!parsed.scheme.equals(SCHEME, ignoreCase = true)) return null
            if (!parsed.host.equals("trakt", ignoreCase = true)) return null
            if (parsed.path != "/callback") return null
            val query = parsed.rawQuery ?: return null
            val params = query.split("&").mapNotNull { pair ->
                val at = pair.indexOf('=')
                if (at <= 0) return@mapNotNull null
                val decode = { value: String -> URLDecoder.decode(value, Charsets.UTF_8) }
                decode(pair.substring(0, at)) to decode(pair.substring(at + 1))
            }.toMap()
            val code = params["code"]?.takeIf { it.isNotEmpty() } ?: return null
            val state = params["state"]?.takeIf { it.isNotEmpty() } ?: return null
            return TraktCallback(code, state)
        }
    }
}

/**
 * Carries a Trakt callback from the activity the browser opens to the screen
 * that finishes the flow.
 *
 * The browser lands on [dev.huelin.watchguru.TraktCallbackActivity], which has
 * no screen of its own and no view model to hand the result to; the Trakt
 * screen picks it up when it resumes a moment later. Holding one value is
 * enough: a newer callback supersedes an older one, whose state the server
 * would refuse anyway.
 *
 * No Android types, so it is testable on a plain JVM.
 */
class TraktCallbacks {

    private val pending = AtomicReference<TraktCallback?>(null)

    fun offer(callback: TraktCallback) {
        pending.set(callback)
    }

    /** The pending callback, at most once. */
    fun take(): TraktCallback? = pending.getAndSet(null)
}
