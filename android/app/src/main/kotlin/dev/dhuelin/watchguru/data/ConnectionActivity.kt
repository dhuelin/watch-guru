package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.LinkedAccountResponse
import dev.dhuelin.watchguru.api.models.SyncRunResponse

/**
 * What a connection to another service has been doing, in words.
 *
 * Shared by Plex and Trakt, and here rather than in either screen because it
 * is the part worth testing: an integration that silently stops is worse than
 * one never offered, and the whole difference between "working" and "stopped"
 * is which of these sentences the user is shown. Holds no Android types, so it
 * runs on a plain JVM.
 *
 * The two services differ in exactly one place -- what a connection that has
 * never heard anything should say -- so that sentence is passed in rather than
 * decided here, and everything else is the same for both.
 */
object ConnectionActivity {

    /** How a connection is doing, as a screen needs to present it. */
    enum class Health {
        /** Nothing is connected. */
        NOT_CONNECTED,

        /** Connected, but nothing has arrived yet. */
        WAITING,

        /** Viewings are arriving and being recorded. */
        WORKING,

        /** Connected, and the last attempt could not be completed. */
        NEEDS_ATTENTION,
    }

    fun health(connected: Boolean, account: LinkedAccountResponse?): Health {
        if (!connected) return Health.NOT_CONNECTED
        if (account == null) return Health.WAITING

        return when {
            // A connection that has never heard anything is not broken, and
            // saying so would send somebody to redo setup that is already
            // right. Plex only calls when something finishes playing, and a
            // Trakt sync only runs every couple of hours.
            account.lastSyncAt == null -> Health.WAITING
            !account.lastSyncError.isNullOrBlank() -> Health.NEEDS_ATTENTION
            account.status == LinkedAccountResponse.Status.ERROR -> Health.NEEDS_ATTENTION
            else -> Health.WORKING
        }
    }

    /**
     * One delivery or sync run, in one line.
     *
     * The error message is the server's own and is written for the person
     * reading it -- "open the series once so its episodes are fetched" is
     * something they can act on, which "PARTIAL" is not.
     */
    fun describe(run: SyncRunResponse): String = when {
        run.itemsImported > 0 -> "Recorded ${run.itemsImported}"
        run.itemsSkipped > 0 -> "Nothing new"
        !run.errorMessage.isNullOrBlank() -> run.errorMessage!!
        run.status == SyncRunResponse.Status.RUNNING -> "In progress"
        else -> "Nothing to record"
    }

    /**
     * The headline sentence for a connection.
     *
     * Takes the last error verbatim where there is one: a generic "something
     * went wrong" would hide the only part that tells the user what to do.
     *
     * @param waiting what to say when nothing has arrived yet, which is the
     *   one sentence that differs between a webhook and a scheduled sync
     */
    fun summary(
        connected: Boolean,
        account: LinkedAccountResponse?,
        waiting: String,
    ): String = when (health(connected, account)) {
        Health.NOT_CONNECTED -> "Not connected."
        Health.WAITING -> waiting
        Health.WORKING -> "Connected and recording."
        Health.NEEDS_ATTENTION ->
            account?.lastSyncError?.takeIf { it.isNotBlank() }
                ?: "Connected, but the last attempt could not be completed."
    }
}
