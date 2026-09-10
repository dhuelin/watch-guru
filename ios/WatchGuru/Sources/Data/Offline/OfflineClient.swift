import Foundation
import WatchGuruAPI

/// The client, with the train journey accounted for (#14).
///
/// Wraps ``WatchGuruClient`` rather than living inside it, so the network layer
/// stays a thin mapping of one call to one result and the decisions about what
/// to cache and what to queue are visible in one place.
///
/// Two behaviours, and they are not symmetric:
///
/// - **Reads** go to the network first and fall back to the last snapshot.
///   Never the other way round: showing a stale library to someone with a
///   working connection would be a bug.
/// - **Writes** go to the network first and fall back to the queue, and the
///   caller is told it worked. That is the point — marking an episode on a
///   train has to feel like it worked, because it did.
actor OfflineClient {

    /// What happened to a change the user made.
    enum Written: Equatable {
        /// The server has it.
        case sent
        /// Stored locally; it will be sent when there is a network.
        case queued
        /// The server refused it, and the user needs to know.
        case failed(APIFailure)
    }

    private let client: WatchGuruClient
    private let cache: SnapshotCache
    private let queue: MutationQueue

    init(client: WatchGuruClient, cache: SnapshotCache, queue: MutationQueue) {
        self.client = client
        self.cache = cache
        self.queue = queue
    }

    // MARK: - Reads

    /// The library, from the network when possible.
    ///
    /// A cached answer is returned only when offline. An upstream error means
    /// the backend is up and answering, so its answer — including an empty one
    /// — is the truth; substituting a snapshot there would hide a real state.
    func library(
        status: WatchlistControllerAPI.Status_listWatchlist? = nil
    ) async throws(APIFailure) -> [WatchlistItemResponse] {
        do {
            let items = try await client.library(status: status)
            // Only the unfiltered list is cached: it is what the app opens on,
            // and caching every filter would store overlapping copies of the
            // same rows.
            if status == nil { cache.put(SnapshotCache.Key.library, items) }
            return items
        } catch {
            if case .offline = error, status == nil,
               let cached: SnapshotCache.Cached<[WatchlistItemResponse]> =
                    cache.get(SnapshotCache.Key.library) {
                return cached.value
            }
            throw error
        }
    }

    func upNext(limit: Int = 20) async throws(APIFailure) -> [UpNextResponse] {
        do {
            let entries = try await client.upNext(limit: limit)
            cache.put(SnapshotCache.Key.upNext, entries)
            return entries
        } catch {
            if case .offline = error,
               let cached: SnapshotCache.Cached<[UpNextResponse]> =
                    cache.get(SnapshotCache.Key.upNext) {
                return cached.value
            }
            throw error
        }
    }

    // MARK: - Writes

    func markEpisodeWatched(episodeId: Int64, titleId: Int64) async -> Written {
        let watchedAt = Date.now
        return await write {
            try await client.markEpisodeWatched(episodeId: episodeId, watchedAt: watchedAt)
        } queueing: { id in
            .markEpisodeWatched(id: id, episodeId: episodeId, titleId: titleId, watchedAt: watchedAt)
        }
    }

    func unmarkEpisode(episodeId: Int64) async -> Written {
        await write {
            try await client.unmarkEpisode(episodeId: episodeId)
        } queueing: { id in
            .unmarkEpisode(id: id, episodeId: episodeId)
        }
    }

    func markWatchedUpTo(episodeId: Int64) async -> Written {
        await write {
            _ = try await client.markWatchedUpTo(episodeId: episodeId)
        } queueing: { id in
            .markWatchedUpTo(id: id, episodeId: episodeId)
        }
    }

    func addToLibrary(_ request: AddToWatchlist) async -> Written {
        await write {
            _ = try await client.addToLibrary(request)
        } queueing: { id in
            .addToLibrary(
                id: id,
                providerId: request.providerId,
                titleType: request.titleType.rawValue,
                status: request.status?.rawValue
            )
        }
    }

    func removeFromLibrary(itemId: Int64) async -> Written {
        await write {
            try await client.removeFromLibrary(itemId: itemId)
        } queueing: { id in
            .removeFromLibrary(id: id, itemId: itemId)
        }
    }

    // MARK: - Sync

    /// Changes not yet accepted by the server. Zero almost always.
    func pendingCount() -> Int { queue.pending().count }

    /// Sends whatever is queued.
    ///
    /// Called when the app comes to the foreground, not on a timer: a timer
    /// would either be too slow to feel immediate when the signal returns, or
    /// fast enough to be a battery cost for something that is usually a no-op.
    func sync() async -> SyncEngine.Outcome {
        await SyncEngine(queue: queue) { [self] mutation in
            await replay(mutation)
        }.sync()
    }

    /// Signing out must leave nothing of the previous user behind.
    func clearLocalData() {
        cache.evict(SnapshotCache.Key.library)
        cache.evict(SnapshotCache.Key.upNext)
        queue.clear()
    }

    // MARK: - Internals

    private func write(
        _ operation: () async throws(APIFailure) -> Void,
        queueing build: (Int64) -> PendingMutation
    ) async -> Written {
        do {
            try await operation()
            return .sent
        } catch {
            if case .offline = error {
                queue.enqueue(build)
                return .queued
            }
            // Anything the server actually answered is a real failure. Queueing
            // a rejected change would replay it later and be rejected again.
            return .failed(error)
        }
    }

    private func replay(_ mutation: PendingMutation) async -> APIFailure? {
        do {
            switch mutation {
            // The stored timestamp, not "now": replaying with the current time
            // would file episodes watched last night as watched the moment the
            // train reached signal.
            case .markEpisodeWatched(_, let episodeId, _, let watchedAt):
                _ = try await client.markEpisodeWatched(episodeId: episodeId, watchedAt: watchedAt)
            case .unmarkEpisode(_, let episodeId):
                try await client.unmarkEpisode(episodeId: episodeId)
            case .markWatchedUpTo(_, let episodeId):
                _ = try await client.markWatchedUpTo(episodeId: episodeId)
            case .addToLibrary(_, let providerId, let titleType, let status):
                guard let type = AddToWatchlist.TitleType(rawValue: titleType) else {
                    // Written by a version that knew a type this one does not.
                    // Permanent, so report it as such and let it be dropped.
                    return .notFound
                }
                // Argument order follows the generated initialiser
                // (providerId, status, titleType), which Swift enforces.
                _ = try await client.addToLibrary(AddToWatchlist(
                    providerId: providerId,
                    status: status.flatMap(AddToWatchlist.Status.init(rawValue:)),
                    titleType: type
                ))
            case .updateLibraryItem(_, let itemId, let status):
                _ = try await client.updateLibraryItem(
                    itemId: itemId,
                    UpdateWatchlistItem(status: status.flatMap(UpdateWatchlistItem.Status.init(rawValue:)))
                )
            case .removeFromLibrary(_, let itemId):
                try await client.removeFromLibrary(itemId: itemId)
            }
            return nil
        } catch {
            return error
        }
    }
}
