package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.models.LinkedAccountResponse
import dev.dhuelin.watchguru.api.models.PlexStatusResponse
import dev.dhuelin.watchguru.api.models.StreamingServiceResponse
import dev.dhuelin.watchguru.api.models.SyncRunResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * What the Plex screen says about a connection.
 *
 * The distinction these pin down is the one the whole feature rests on: an
 * integration that silently stops is worse than one that was never offered, so
 * "connected but nothing has arrived yet" and "connected and the last delivery
 * failed" must not read the same.
 */
class PlexActivityTest {

    private val now: OffsetDateTime = OffsetDateTime.of(2026, 9, 12, 20, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `no link at all is not connected`() {
        val status = PlexStatusResponse(connected = false, recentRuns = emptyList(), account = null)

        assertEquals(PlexActivity.Health.NOT_CONNECTED, PlexActivity.health(status))
        assertEquals("Not connected.", PlexActivity.summary(status))
    }

    @Test
    fun `a link that has never heard anything is waiting, not broken`() {
        // Plex only calls when something finishes playing, so silence right
        // after connecting is the normal case. Calling it an error would send
        // somebody to re-paste a URL that is fine.
        val status = status(account(lastSyncAt = null))

        assertEquals(PlexActivity.Health.WAITING, PlexActivity.health(status))
        assertTrue(PlexActivity.summary(status).contains("Paste the webhook URL"))
    }

    @Test
    fun `deliveries arriving cleanly read as working`() {
        val status = status(account(lastSyncAt = now.minusHours(2)))

        assertEquals(PlexActivity.Health.WORKING, PlexActivity.health(status))
        assertEquals("Connected and recording.", PlexActivity.summary(status))
    }

    @Test
    fun `the last error is shown verbatim, because it says what to do`() {
        val note = "Found Severance, but not the episode this row names."
        val status = status(account(lastSyncAt = now, lastSyncError = note))

        assertEquals(PlexActivity.Health.NEEDS_ATTENTION, PlexActivity.health(status))
        assertEquals(note, PlexActivity.summary(status))
    }

    @Test
    fun `a link the server marked ERROR needs attention even with no message`() {
        val status = status(
            account(lastSyncAt = now, status = LinkedAccountResponse.Status.ERROR),
        )

        assertEquals(PlexActivity.Health.NEEDS_ATTENTION, PlexActivity.health(status))
        assertTrue(PlexActivity.summary(status).isNotBlank())
    }

    @Test
    fun `a delivery describes what became of it`() {
        assertEquals("Recorded", PlexActivity.describe(run(imported = 1)))
        assertEquals("Already recorded", PlexActivity.describe(run(skipped = 1)))
        assertEquals(
            "Nothing in your catalogue matches \"Heat\".",
            PlexActivity.describe(
                run(failed = 1, error = "Nothing in your catalogue matches \"Heat\"."),
            ),
        )
        assertEquals("Nothing to record", PlexActivity.describe(run()))
    }

    private fun status(account: LinkedAccountResponse?) = PlexStatusResponse(
        connected = true,
        recentRuns = emptyList(),
        account = account,
    )

    private fun account(
        lastSyncAt: OffsetDateTime?,
        lastSyncError: String? = null,
        status: LinkedAccountResponse.Status = LinkedAccountResponse.Status.CONNECTED,
    ) = LinkedAccountResponse(
        id = 1L,
        service = StreamingServiceResponse(id = 9L, name = "Plex", slug = "plex", supportsSync = true),
        status = status,
        syncEnabled = true,
        accountLabel = "denis",
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
