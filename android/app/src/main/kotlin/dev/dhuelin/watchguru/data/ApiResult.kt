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

/**
 * Runs one call and turns everything it can do into an [ApiResult].
 *
 * Shared by every repository rather than reimplemented per repository: two
 * copies of this mapping would eventually disagree about which status is a
 * failure the user should see, and the screens would show two different things
 * for the same server response.
 *
 * A 204 with no body is a success carrying [Unit]; Retrofit gives null for the
 * body there, which is why null is not treated as a failure for [Unit] results.
 *
 * Does not switch dispatchers -- the caller decides where the work runs.
 */
suspend fun <T> apiCall(block: suspend () -> retrofit2.Response<T>): ApiResult<T> =
    try {
        val response = block()
        if (response.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(response.body() ?: Unit as T)
        } else {
            failureFor(response.code(), response.message())
        }
    } catch (e: java.io.IOException) {
        // No route to host, DNS failure, timeout: all "offline" as far as the
        // user is concerned.
        ApiResult.Failure.Offline
    } catch (e: Exception) {
        ApiResult.Failure.Unexpected(null, e.message)
    }

private fun failureFor(status: Int, message: String?): ApiResult.Failure = when (status) {
    401, 403 -> ApiResult.Failure.Unauthorised
    404 -> ApiResult.Failure.NotFound
    // The backend reports an unreachable or unconfigured TMDB as 502/503. It
    // keeps serving the user's own data, so this must not read as a total
    // outage.
    502, 503 -> ApiResult.Failure.Upstream
    else -> ApiResult.Failure.Unexpected(status, message)
}
