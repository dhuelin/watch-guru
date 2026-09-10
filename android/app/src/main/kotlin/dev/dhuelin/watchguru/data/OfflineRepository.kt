package dev.dhuelin.watchguru.data

import dev.dhuelin.watchguru.api.apis.WatchlistControllerApi
import dev.dhuelin.watchguru.api.models.AddToWatchlist
import dev.dhuelin.watchguru.api.models.LogEpisodeWatched
import dev.dhuelin.watchguru.api.models.UpNextResponse
import dev.dhuelin.watchguru.api.models.UpdateWatchlistItem
import dev.dhuelin.watchguru.api.models.WatchlistItemResponse
import dev.dhuelin.watchguru.data.offline.MutationQueue
import dev.dhuelin.watchguru.data.offline.PendingMutation
import dev.dhuelin.watchguru.data.offline.SnapshotCache
import dev.dhuelin.watchguru.data.offline.SyncEngine
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.ZoneOffset

/**
 * The repository, with the train journey accounted for (#14).
 *
 * Wraps [WatchGuruRepository] rather than living inside it, so the network
 * layer stays a thin, testable mapping of one call to one result and the
 * decisions about what to cache and what to queue are visible in one place.
 *
 * Two behaviours, and they are not symmetric:
 *
 * - **Reads** go to the network first and fall back to the last snapshot.
 *   Never the other way round: showing a stale library to someone with a
 *   working connection would be a bug.
 * - **Writes** go to the network first and fall back to the queue, and the
 *   caller is told it succeeded. That is the point -- marking an episode on a
 *   train has to feel like it worked, because it did.
 */
class OfflineRepository(
    private val network: WatchGuruRepository,
    private val cache: SnapshotCache,
    private val queue: MutationQueue,
    private val clock: () -> Instant = Instant::now,
) {

    private val sync = SyncEngine(queue) { mutation -> replay(mutation) }

    /**
     * The library, from the network when possible.
     *
     * A cached answer is returned only for [ApiResult.Failure.Offline]. An
     * upstream error means the backend is up and answering, so its answer --
     * including an empty one -- is the truth; substituting a snapshot there
     * would hide a real state from the user.
     */
    suspend fun library(
        status: WatchlistControllerApi.StatusListWatchlist? = null,
        page: Int = 0,
        size: Int = 20,
    ): ApiResult<List<WatchlistItemResponse>> {
        val result = network.library(status, page, size)
        if (result is ApiResult.Success) {
            // Only the unfiltered first page is worth caching: it is what the
            // app opens on, and caching every filter combination would store
            // several overlapping copies of the same rows.
            if (status == null && page == 0) {
                cache.put(SnapshotCache.LIBRARY, ListSerializer(WatchlistItemResponse.serializer()), result.value)
            }
            return result
        }
        if (result is ApiResult.Failure.Offline && status == null && page == 0) {
            cached(SnapshotCache.LIBRARY, ListSerializer(WatchlistItemResponse.serializer()))
                ?.let { return ApiResult.Success(it) }
        }
        return result
    }

    suspend fun upNext(limit: Int = 20): ApiResult<List<UpNextResponse>> {
        val result = network.upNext(limit)
        if (result is ApiResult.Success) {
            cache.put(SnapshotCache.UP_NEXT, ListSerializer(UpNextResponse.serializer()), result.value)
            return result
        }
        if (result is ApiResult.Failure.Offline) {
            cached(SnapshotCache.UP_NEXT, ListSerializer(UpNextResponse.serializer()))
                ?.let { return ApiResult.Success(it) }
        }
        return result
    }

    /**
     * Marks an episode watched, queueing it if there is no network.
     *
     * Returns [Queued] rather than a [WatchEventResponse][
     * dev.dhuelin.watchguru.api.models.WatchEventResponse] in that case: the
     * screen has to know the difference, because it cannot show a server id it
     * does not have, and pretending otherwise is how an optimistic UI ends up
     * lying.
     */
    suspend fun markEpisodeWatched(episodeId: Long, titleId: Long): Written {
        val watchedAt = clock()
        return write(
            network.markEpisodeWatched(
                LogEpisodeWatched(episodeId = episodeId, watchedAt = watchedAt.atOffset(ZoneOffset.UTC))
            )
        ) { id ->
            PendingMutation.MarkEpisodeWatched(id, episodeId, titleId, watchedAt.epochSecond)
        }
    }

    suspend fun unmarkEpisode(episodeId: Long): Written =
        write(network.unmarkEpisode(episodeId)) { id -> PendingMutation.UnmarkEpisode(id, episodeId) }

    suspend fun markWatchedUpTo(episodeId: Long): Written =
        write(network.markWatchedUpTo(episodeId)) { id -> PendingMutation.MarkWatchedUpTo(id, episodeId) }

    suspend fun addToLibrary(request: AddToWatchlist): Written =
        write(network.addToLibrary(request)) { id ->
            PendingMutation.AddToLibrary(
                id, request.providerId, request.titleType.name, request.status?.name,
            )
        }

    suspend fun updateLibraryItem(itemId: Long, update: UpdateWatchlistItem): Written =
        write(network.updateLibraryItem(itemId, update)) { id ->
            PendingMutation.UpdateLibraryItem(id, itemId, update.status?.name)
        }

    suspend fun removeFromLibrary(itemId: Long): Written =
        write(network.removeFromLibrary(itemId)) { id -> PendingMutation.RemoveFromLibrary(id, itemId) }

    /** How many changes are still waiting, for the UI to show honestly. */
    fun pendingCount(): Int = queue.pending().size

    /**
     * Sends whatever is queued.
     *
     * Called when the app comes to the foreground and after a successful call
     * proves the network is back, not on a timer -- a timer would either be too
     * slow to feel immediate or too fast to be polite.
     */
    suspend fun sync(): SyncEngine.Outcome = sync.sync()

    /** Signing out must leave nothing of the previous user behind. */
    fun clearLocalData() {
        cache.evict(SnapshotCache.LIBRARY)
        cache.evict(SnapshotCache.UP_NEXT)
        queue.clear()
    }

    private fun <T> cached(key: String, serializer: kotlinx.serialization.KSerializer<T>): T? =
        // No maxAge: a library the user last saw a week ago still beats an
        // empty screen on a train, and the UI says how old it is.
        cache.get(key, serializer)?.value

    private fun write(result: ApiResult<*>, build: (Long) -> PendingMutation): Written =
        when (result) {
            is ApiResult.Success -> Written.Sent
            is ApiResult.Failure.Offline -> {
                queue.enqueue(build)
                Written.Queued
            }
            // Anything the server actually answered is a real failure. Queueing
            // a rejected change would replay it later and reject it again.
            is ApiResult.Failure -> Written.Failed(result)
        }

    private suspend fun replay(mutation: PendingMutation): ApiResult<*> = when (mutation) {
        // The stored timestamp, not "now": replaying with the current time
        // would file episodes watched last night as watched the moment the
        // train reached signal.
        is PendingMutation.MarkEpisodeWatched -> network.markEpisodeWatched(
            LogEpisodeWatched(
                episodeId = mutation.episodeId,
                watchedAt = Instant.ofEpochSecond(mutation.watchedAtEpochSecond).atOffset(ZoneOffset.UTC),
            )
        )
        is PendingMutation.UnmarkEpisode -> network.unmarkEpisode(mutation.episodeId)
        is PendingMutation.MarkWatchedUpTo -> network.markWatchedUpTo(mutation.episodeId)
        is PendingMutation.AddToLibrary -> network.addToLibrary(
            AddToWatchlist(
                providerId = mutation.providerId,
                titleType = AddToWatchlist.TitleType.valueOf(mutation.titleType),
                status = mutation.status?.let { runCatching { AddToWatchlist.Status.valueOf(it) }.getOrNull() },
            )
        )
        is PendingMutation.UpdateLibraryItem -> network.updateLibraryItem(
            mutation.itemId,
            UpdateWatchlistItem(
                status = mutation.status
                    ?.let { runCatching { UpdateWatchlistItem.Status.valueOf(it) }.getOrNull() },
            ),
        )
        is PendingMutation.RemoveFromLibrary -> network.removeFromLibrary(mutation.itemId)
    }

    /** What happened to a change the user made. */
    sealed interface Written {
        /** The server has it. */
        data object Sent : Written

        /** Stored locally; it will be sent when there is a network. */
        data object Queued : Written

        /** The server refused it, and the user needs to know. */
        data class Failed(val failure: ApiResult.Failure) : Written
    }
}
