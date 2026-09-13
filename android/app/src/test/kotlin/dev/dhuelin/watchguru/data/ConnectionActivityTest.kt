package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.LinkedAccountResponse
import dev.dhuelin.watchguru.api.models.StreamingServiceResponse
import dev.dhuelin.watchguru.api.models.SyncRunResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * What a connection screen says about a connection.
 *
 * The distinction these pin down is the one both integrations rest on: an
 * integration that silently stops is worse than one that was never offered, so
 * "connected but nothing has arrived yet" and "connected and the last attempt
 * failed" must not read the same.
 */
class ConnectionActivityTest {

    private val now: OffsetDateTime = OffsetDateTime.of(2026, 9, 13, 20, 0, 0, 0, ZoneOffset.UTC)

    private val plexWaiting = "Waiting for your server."
    private val traktWaiting = "Approve the connection in your browser, then come back here."

    @Test
    fun `nothing connected is not connected`() {
        assertEquals(
            ConnectionActivity.Health.NOT_CONNECTED,
            ConnectionActivity.health(connected = false, account = null),
        )
        assertEquals(
            "Not connected.",
            ConnectionActivity.summary(false, null, plexWaiting),
        )
    }

    @Test
    fun `a connection that has never heard anything is waiting, not broken`() {
        // Plex only calls when something finishes playing, and a Trakt sync
        // runs every couple of hours. Silence right after connecting is the
        // normal case, and calling it an error sends somebody to redo setup
        // that is already right.
        val account = account(lastSyncAt = null)

        assertEquals(ConnectionActivity.Health.WAITING, ConnectionActivity.health(true, account))
        assertEquals(plexWaiting, ConnectionActivity.summary(true, account, plexWaiting))
        assertEquals(traktWaiting, ConnectionActivity.summary(true, account, traktWaiting))
    }

    @Test
    fun `activity arriving cleanly reads as working`() {
        val account = account(lastSyncAt = now.minusHours(2))

        assertEquals(ConnectionActivity.Health.WORKING, ConnectionActivity.health(true, account))
        assertEquals(
            "Connected and recording.",
            ConnectionActivity.summary(true, account, traktWaiting),
        )
    }

    @Test
    fun `the last error is shown verbatim, because it says what to do`() {
        val note = "Trakt access has expired. Connect Trakt again to resume syncing."
        val account = account(lastSyncAt = now, lastSyncError = note)

        assertEquals(
            ConnectionActivity.Health.NEEDS_ATTENTION,
            ConnectionActivity.health(true, account),
        )
        assertEquals(note, ConnectionActivity.summary(true, account, traktWaiting))
    }

    @Test
    fun `a link the server marked ERROR needs attention even with no message`() {
        val account = account(lastSyncAt = now, status = LinkedAccountResponse.Status.ERROR)

        assertEquals(
            ConnectionActivity.Health.NEEDS_ATTENTION,
            ConnectionActivity.health(true, account),
        )
        assertTrue(ConnectionActivity.summary(true, account, traktWaiting).isNotBlank())
    }

    @Test
    fun `a run describes what became of it`() {
        assertEquals("Recorded 1", ConnectionActivity.describe(run(imported = 1)))
        assertEquals("Recorded 12", ConnectionActivity.describe(run(imported = 12)))
        assertEquals("Nothing new", ConnectionActivity.describe(run(skipped = 3)))
        assertEquals(
            "Nothing in your catalogue matches \"Heat\".",
            ConnectionActivity.describe(
                run(failed = 1, error = "Nothing in your catalogue matches \"Heat\"."),
            ),
        )
        assertEquals("Nothing to record", ConnectionActivity.describe(run()))
    }

    private fun account(
        lastSyncAt: OffsetDateTime?,
        lastSyncError: String? = null,
        status: LinkedAccountResponse.Status = LinkedAccountResponse.Status.CONNECTED,
    ) = LinkedAccountResponse(
        id = 1L,
        service = StreamingServiceResponse(id = 9L, name = "Trakt", slug = "trakt", supportsSync = true),
        status = status,
        syncEnabled = true,
        accountLabel = null,
        lastSyncAt = lastSyncAt,
        lastSyncError = lastSyncError,
    )

    private fun run(
        imported: Int = 0,
        skipped: Int = 0,
        failed: Int = 0,
        error: String? = null,
    ) = SyncRunResponse(
        id = 1L,
        itemsFailed = failed,
        itemsImported = imported,
        itemsSkipped = skipped,
        startedAt = now,
        status = SyncRunResponse.Status.SUCCESS,
        errorMessage = error,
        finishedAt = now,
    )
}
