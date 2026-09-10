package dev.dhuelin.watchguru.data.offline

import dev.dhuelin.watchguru.data.ApiResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Working without a signal, and catching up afterwards.
 *
 * The ordering rules are what these mostly pin down. An unmark that overtakes
 * the mark it was meant to undo leaves an episode watched that the user
 * unwatched, and it does so silently, days later, on a device nobody is looking
 * at. That class of bug does not show up in manual testing.
 */
class OfflineSyncTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var now = Instant.parse("2026-01-01T12:00:00Z")

    private fun queue(files: FileStore = InMemoryFileStore()) = MutationQueue(files, json)

    // MARK: - Queue

    @Test
    fun `mutations replay in the order they were made`() {
        val queue = queue()
        queue.enqueue { PendingMutation.MarkEpisodeWatched(it, episodeId = 1, titleId = 9, watchedAtEpochSecond = 0) }
        queue.enqueue { PendingMutation.MarkWatchedUpTo(it, episodeId = 5) }
        queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = 7) }

        assertEquals(listOf(1L, 2L, 3L), queue.pending().map { it.id })
    }

    @Test
    fun `a later change to the same target replaces the earlier one`() {
        // Toggling one episode forty times on a train must leave one entry, not
        // forty. Safe only because these operations are absolute rather than
        // relative -- "unmark episode 5" does not depend on what preceded it.
        val queue = queue()
        queue.enqueue { PendingMutation.MarkEpisodeWatched(it, episodeId = 5, titleId = 9, watchedAtEpochSecond = 0) }
        queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = 5) }
        queue.enqueue { PendingMutation.MarkEpisodeWatched(it, episodeId = 5, titleId = 9, watchedAtEpochSecond = 10) }

        val pending = queue.pending()
        assertEquals(1, pending.size)
        assertTrue(pending.single() is PendingMutation.MarkEpisodeWatched)
    }

    @Test
    fun `a bulk mark is not collapsed against a single episode`() {
        // "Mark all up to episode 5" covers a range; treating it as the same
        // target as episode 5 alone would silently drop the rest of the range.
        val queue = queue()
        queue.enqueue { PendingMutation.MarkEpisodeWatched(it, episodeId = 5, titleId = 9, watchedAtEpochSecond = 0) }
        queue.enqueue { PendingMutation.MarkWatchedUpTo(it, episodeId = 5) }

        assertEquals(2, queue.pending().size)
    }

    @Test
    fun `the queue survives a restart`() {
        // The case this whole layer exists for: three episodes marked on a
        // train, then the app is closed.
        val files = InMemoryFileStore()
        queue(files).enqueue {
            PendingMutation.MarkEpisodeWatched(it, episodeId = 1, titleId = 9, watchedAtEpochSecond = 1_700_000_000)
        }

        val afterRestart = queue(files).pending()

        assertEquals(1, afterRestart.size)
        val mark = afterRestart.single() as PendingMutation.MarkEpisodeWatched
        // The timestamp travels: replaying with "now" would file it as watched
        // when the train reached signal.
        assertEquals(1_700_000_000L, mark.watchedAtEpochSecond)
    }

    @Test
    fun `an unreadable queue is discarded rather than wedging every future sync`() {
        val files = InMemoryFileStore()
        files.write("pending-mutations.json", "{ this is not the queue you are looking for")

        assertTrue(queue(files).pending().isEmpty())
    }

    // MARK: - Sync

    private fun engine(queue: MutationQueue, responses: MutableList<ApiResult<*>>) =
        SyncEngine(queue) { responses.removeAt(0) }

    @Test
    fun `a successful drain empties the queue`() = runTest {
        val queue = queue()
        repeat(3) { i -> queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = i.toLong()) } }

        val outcome = engine(queue, mutableListOf(
            ApiResult.Success(Unit), ApiResult.Success(Unit), ApiResult.Success(Unit),
        )).sync()

        assertEquals(3, outcome.sent)
        assertTrue(outcome.finished)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `going offline mid-drain stops rather than skipping ahead`() = runTest {
        // Skipping the stuck one to make progress would apply the rest out of
        // order.
        val queue = queue()
        repeat(3) { i -> queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = i.toLong()) } }

        val outcome = engine(queue, mutableListOf(
            ApiResult.Success(Unit), ApiResult.Failure.Offline, ApiResult.Success(Unit),
        )).sync()

        assertEquals(1, outcome.sent)
        assertTrue(outcome.stoppedOffline)
        // Both the failed one and everything behind it are still waiting, in
        // their original order.
        assertEquals(listOf(2L, 3L), queue.pending().map { it.id })
    }

    @Test
    fun `a permanent rejection is dropped so it cannot block the queue`() = runTest {
        // An episode that no longer exists will not start existing on the tenth
        // attempt, and keeping it would stall every later change for ever.
        val queue = queue()
        queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = 1) }
        queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = 2) }

        val outcome = engine(queue, mutableListOf(
            ApiResult.Failure.NotFound, ApiResult.Success(Unit),
        )).sync()

        assertEquals(1, outcome.sent)
        assertEquals(1, outcome.dropped)
        assertTrue(outcome.finished)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `a server error keeps the change for later`() = runTest {
        // The user's work is not thrown away because the server had a bad
        // minute.
        val queue = queue()
        queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = 1) }

        val outcome = engine(queue, mutableListOf(
            ApiResult.Failure.Unexpected(status = 500, message = "boom"),
        )).sync()

        assertEquals(0, outcome.sent)
        assertEquals(0, outcome.dropped)
        assertTrue(outcome.stoppedTransient)
        assertEquals(1, queue.pending().size)
    }

    @Test
    fun `a 4xx that is not 404 is still permanent`() = runTest {
        val queue = queue()
        queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = 1) }

        val outcome = engine(queue, mutableListOf(
            ApiResult.Failure.Unexpected(status = 422, message = "no"),
        )).sync()

        assertEquals(1, outcome.dropped)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `an expired session stops the drain instead of burning the queue`() = runTest {
        // Nothing queued can succeed until the user signs in again. Continuing
        // would collect 401s for every entry and achieve nothing.
        val queue = queue()
        repeat(3) { i -> queue.enqueue { PendingMutation.UnmarkEpisode(it, episodeId = i.toLong()) } }

        val outcome = engine(queue, mutableListOf(ApiResult.Failure.Unauthorised)).sync()

        assertTrue(outcome.stoppedUnauthorised)
        assertEquals(3, queue.pending().size)
    }

    // MARK: - Snapshot cache

    private fun cache(files: FileStore = InMemoryFileStore()) =
        SnapshotCache(files, json, clock = { now })

    @Test
    fun `a snapshot round-trips`() {
        val files = InMemoryFileStore()
        cache(files).put("k", ListSerializer(String.serializer()), listOf("a", "b"))

        assertEquals(listOf("a", "b"), cache(files).get("k", ListSerializer(String.serializer()))?.value)
    }

    @Test
    fun `a snapshot older than the caller allows is not returned`() {
        val files = InMemoryFileStore()
        cache(files).put("k", String.serializer(), "old")

        now = now.plus(Duration.ofHours(2))

        assertNull(cache(files).get("k", String.serializer(), maxAge = Duration.ofHours(1)))
        // With no limit it is still worth showing: a stale library beats an
        // empty screen on a train, and the UI says how old it is.
        assertEquals("old", cache(files).get("k", String.serializer())?.value)
    }

    @Test
    fun `an unreadable snapshot is deleted rather than reparsed on every launch`() {
        val files = InMemoryFileStore()
        files.write("k", "not json")

        assertNull(cache(files).get("k", String.serializer()))
        assertNull(files.read("k"))
    }

    @Test
    fun `age is reported so the UI can say how stale it is`() {
        val files = InMemoryFileStore()
        cache(files).put("k", String.serializer(), "v")
        now = now.plus(Duration.ofMinutes(90))

        assertEquals(Duration.ofMinutes(90), cache(files).get("k", String.serializer())?.age)
    }
}
