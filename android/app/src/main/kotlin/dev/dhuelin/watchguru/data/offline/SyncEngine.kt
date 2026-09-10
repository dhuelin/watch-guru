package dev.dhuelin.watchguru.data.offline

import dev.dhuelin.watchguru.data.ApiResult

/**
 * Sends queued changes to the server, oldest first.
 *
 * The rules it enforces are the whole point, and each of them is a bug if
 * inverted:
 *
 * - **In order, and stop at the first offline failure.** Skipping a stuck
 *   mutation to make progress on later ones would apply them out of order --
 *   an unmark landing before the mark it was meant to undo leaves the episode
 *   watched.
 * - **A rejection is permanent; drop it.** A 404 for an episode that no longer
 *   exists, or a 409, will not become a success on the tenth attempt. Keeping
 *   it would block every mutation behind it for ever.
 * - **A server error is transient; keep it.** 5xx and offline both mean try
 *   again later.
 *
 * Plain Kotlin with the actual API calls injected, so all of that is testable
 * without a network or a device.
 */
class SyncEngine(
    private val queue: MutationQueue,
    private val send: suspend (PendingMutation) -> ApiResult<*>,
) {

    /**
     * Drains what it can.
     *
     * @return what happened, so a caller can decide whether to refresh the
     *   screen or say "still offline".
     */
    suspend fun sync(): Outcome {
        var sent = 0
        var dropped = 0

        for (mutation in queue.pending()) {
            when (val result = send(mutation)) {
                is ApiResult.Success -> {
                    queue.remove(mutation.id)
                    sent++
                }

                // Still no network. Everything after this one has to wait its
                // turn, so stop rather than reorder.
                is ApiResult.Failure.Offline -> return Outcome(sent, dropped, stoppedOffline = true)

                // The session died mid-sync. Nothing queued can succeed until
                // the user signs in again, and burning through the queue
                // collecting 401s would drop nothing and achieve nothing.
                is ApiResult.Failure.Unauthorised -> return Outcome(sent, dropped, stoppedUnauthorised = true)

                // The server understood and said no. Retrying cannot change
                // that, and keeping it blocks the queue.
                is ApiResult.Failure.NotFound -> {
                    queue.remove(mutation.id)
                    dropped++
                }

                is ApiResult.Failure.Unexpected -> {
                    if (result.status != null && result.status in 400..499) {
                        queue.remove(mutation.id)
                        dropped++
                    } else {
                        // 5xx, or a failure with no status at all. Transient
                        // until proven otherwise; the user's work is not
                        // thrown away on a server having a bad minute.
                        return Outcome(sent, dropped, stoppedTransient = true)
                    }
                }

                is ApiResult.Failure.Upstream -> return Outcome(sent, dropped, stoppedTransient = true)
            }
        }
        return Outcome(sent, dropped)
    }

    /**
     * @param sent mutations the server accepted
     * @param dropped mutations the server rejected permanently. Worth surfacing
     *   if it is ever non-zero: it means something the user did was silently
     *   discarded.
     */
    data class Outcome(
        val sent: Int,
        val dropped: Int,
        val stoppedOffline: Boolean = false,
        val stoppedUnauthorised: Boolean = false,
        val stoppedTransient: Boolean = false,
    ) {
        val finished: Boolean
            get() = !stoppedOffline && !stoppedUnauthorised && !stoppedTransient
    }
}
