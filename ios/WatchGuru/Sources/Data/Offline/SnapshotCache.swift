import Foundation

/// The last good copy of something the API returned.
///
/// Read-through rather than read-first: a screen still asks the network, and
/// this answers only when the network cannot. That ordering matters — showing a
/// stale library to someone with a working connection would be a bug, not a
/// feature.
struct SnapshotCache {

    /// Cache keys. No per-user namespacing: sign-out clears them.
    enum Key {
        static let library = "cache-library.json"
        static let upNext = "cache-up-next.json"
    }

    /// A snapshot and how old it is, so the UI can say "as of yesterday".
    struct Cached<Value> {
        let value: Value
        let storedAt: Date
        var age: TimeInterval { Date.now.timeIntervalSince(storedAt) }
    }

    let store: OfflineStore
    var now: @Sendable () -> Date = { .now }

    /// Stores a snapshot, replacing whatever was there.
    ///
    /// Failures are swallowed on purpose: a cache that cannot be written is a
    /// missing convenience, and turning it into a visible error would break a
    /// request that actually succeeded.
    func put<Value: Codable>(_ key: String, _ value: Value) {
        guard let data = try? JSONEncoder().encode(Envelope(storedAt: now(), value: value)) else { return }
        store.write(key, data)
    }

    /// - Parameter maxAge: how old a snapshot may be and still be worth
    ///   showing. `nil` means any age — correct for a library the user last saw
    ///   a week ago, because the alternative on a train is an empty screen.
    func get<Value: Codable>(
        _ key: String,
        as type: Value.Type = Value.self,
        maxAge: TimeInterval? = nil
    ) -> Cached<Value>? {
        guard let data = store.read(key) else { return nil }
        guard let envelope = try? JSONDecoder().decode(Envelope<Value>.self, from: data) else {
            // Written by an older version, or truncated. Unreadable is the same
            // as absent, and leaving it means failing to parse it on every
            // single launch.
            store.delete(key)
            return nil
        }
        let age = now().timeIntervalSince(envelope.storedAt)
        if let maxAge, age > maxAge { return nil }
        return Cached(value: envelope.value, storedAt: envelope.storedAt)
    }

    func evict(_ key: String) {
        store.delete(key)
    }

    /// Requires the full `Codable` rather than one half on each side: the
    /// envelope is a single type used for both directions, so constraining
    /// `put` to `Encodable` alone left it unable to satisfy its own
    /// `Decodable` requirement. Every value cached here is `Codable` anyway —
    /// they are all generated API models.
    private struct Envelope<Value: Codable>: Codable {
        let storedAt: Date
        let value: Value
    }
}
