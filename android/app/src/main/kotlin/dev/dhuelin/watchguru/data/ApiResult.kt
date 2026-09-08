package dev.dhuelin.watchguru.data

/**
 * The outcome of a call to the backend.
 *
 * Deliberately not Kotlin's [Result]: the failure cases here are things the UI
 * shows differently, and collapsing them into a single Throwable pushes the
 * "what do we tell the user" decision into every screen. The distinction that
 * matters most is [Failure.Offline] versus [Failure.Upstream] -- the backend
 * degrades to stale catalogue data when TMDB is down, so "the catalogue is
 * unavailable" and "you have no connection" are genuinely different messages
 * and only one of them means the user's own library is unreachable.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val value: T) : ApiResult<T>

    sealed interface Failure : ApiResult<Nothing> {

        /** No usable network. Reads may still be served from cache. */
        data object Offline : Failure

        /** The token is missing, expired or rejected. Re-authenticate. */
        data object Unauthorised : Failure

        /** The thing asked for does not exist, or does not belong to this user. */
        data object NotFound : Failure

        /**
         * The backend reached us but could not reach TMDB. The user's own
         * library, progress and history are unaffected.
         */
        data object Upstream : Failure

        /** Anything else, including malformed responses. */
        data class Unexpected(val status: Int?, val message: String?) : Failure
    }
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value
