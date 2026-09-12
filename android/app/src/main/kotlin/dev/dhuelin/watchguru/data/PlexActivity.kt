package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.LinkedAccountResponse
import dev.dhuelin.watchguru.api.models.PlexStatusResponse
import dev.dhuelin.watchguru.api.models.SyncRunResponse

/**
 * What a Plex connection has been doing, in words.
 *
 * Here rather than in the screen because it is the part worth testing: an
 * integration that silently stops is worse than one never offered, and the
 * whole difference between "working" and "stopped" is which of these sentences
 * the user is shown. Holds no Android types, so it runs on a plain JVM.
 */
object PlexActivity {

    /** How a connection is doing, as the screen needs to present it. */
    enum class Health {
        /** No webhook URL is live. */
        NOT_CONNECTED,

        /** Connected, but the server has never called. Usually a URL not pasted yet. */
        WAITING,

        /** Deliveries are arriving and being recorded. */
        WORKING,

        /** Deliveries are arriving, and the last one could not be recorded. */
        NEEDS_ATTENTION,
    }

    fun health(status: PlexStatusResponse): Health {
        if (!status.connected) return Health.NOT_CONNECTED
        val account = status.account ?: return Health.WAITING

        return when {
            // A link that has never heard anything is not broken, and saying
            // so would send somebody to re-paste a URL that is fine. Plex only
            // calls when something finishes playing.
            account.lastSyncAt == null -> Health.WAITING
            !account.lastSyncError.isNullOrBlank() -> Health.NEEDS_ATTENTION
            account.status == LinkedAccountResponse.Status.ERROR -> Health.NEEDS_ATTENTION
            else -> Health.WORKING
        }
    }

    /**
     * One delivery, in one line.
     *
     * The error message is the server's own and is written for the person
     * reading it -- "open the series once so its episodes are fetched" is
     * something they can act on, which "PARTIAL" is not.
     */
    fun describe(run: SyncRunResponse): String = when {
        run.itemsImported > 0 -> "Recorded"
        run.itemsSkipped > 0 -> "Already recorded"
        !run.errorMessage.isNullOrBlank() -> run.errorMessage!!
        run.status == SyncRunResponse.Status.RUNNING -> "In progress"
        else -> "Nothing to record"
    }

    /**
     * The headline sentence for the connection.
     *
     * Takes the last error verbatim where there is one: a generic "something
     * went wrong" would hide the only part that tells the user what to do.
     */
    fun summary(status: PlexStatusResponse): String = when (health(status)) {
        Health.NOT_CONNECTED -> "Not connected."
        Health.WAITING ->
            "Waiting for your server. Paste the webhook URL into Plex, then watch something."
        Health.WORKING -> "Connected and recording."
        Health.NEEDS_ATTENTION ->
            status.account?.lastSyncError?.takeIf { it.isNotBlank() }
                ?: "Connected, but the last delivery could not be recorded."
    }
}
