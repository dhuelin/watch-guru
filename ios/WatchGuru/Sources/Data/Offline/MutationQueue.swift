import Foundation

/// The changes waiting to reach the server, in the order they were made.
///
/// Persisted, because the case this exists for is a user who marks three
/// episodes on a train and then closes the app. Losing those is worse than
/// never having accepted them — which is also why this one file is written to
/// Documents rather than Caches: the system must not reclaim it.
final class MutationQueue: @unchecked Sendable {

    private static let fileName = "pending-mutations.json"

    private let store: OfflineStore
    private let lock = NSLock()

    init(store: OfflineStore) {
        self.store = store
    }

    /// Appends a mutation and returns it with its assigned id.
    ///
    /// Collapsing happens here rather than at replay time so the queue never
    /// grows unboundedly offline: toggling one episode forty times leaves one
    /// entry, not forty. Safe only because these operations are absolute rather
    /// than relative — "unmark episode 5" does not depend on what preceded it.
    @discardableResult
    func enqueue(_ build: (Int64) -> PendingMutation) -> PendingMutation {
        lock.withLock {
            let current = load()
            let mutation = build((current.map(\.id).max() ?? 0) + 1)
            save(current.filter { $0.target != mutation.target } + [mutation])
            return mutation
        }
    }

    func pending() -> [PendingMutation] {
        lock.withLock { load().sorted { $0.id < $1.id } }
    }

    func remove(id: Int64) {
        lock.withLock { save(load().filter { $0.id != id }) }
    }

    func clear() {
        lock.withLock { store.delete(Self.fileName) }
    }

    private func load() -> [PendingMutation] {
        guard let data = store.read(Self.fileName) else { return [] }
        guard let decoded = try? JSONDecoder().decode([PendingMutation].self, from: data) else {
            // Written by a version that knew different mutation cases.
            // Dropping it loses work, which is bad; retrying for ever against a
            // decoder that cannot read it is worse, and wedges every later sync.
            store.delete(Self.fileName)
            return []
        }
        return decoded
    }

    private func save(_ mutations: [PendingMutation]) {
        guard !mutations.isEmpty else {
            store.delete(Self.fileName)
            return
        }
        guard let data = try? JSONEncoder().encode(mutations) else { return }
        store.write(Self.fileName, data)
    }
}
