import Foundation

/// Somewhere to put bytes that survive the process.
///
/// The whole offline layer sits on this one seam, and it is deliberately tiny:
/// everything above it — staleness, ordering, replay, collapsing — is plain
/// Swift, and the only part that needs a device is the few lines that know
/// where the caches directory is.
///
/// Not SwiftData or Core Data. Their advantage is querying, and nothing here
/// queries: the app loads a library of a few hundred rows and filters it in
/// memory. A JSON snapshot buys the same behaviour with no model container, no
/// schema migration, and logic that is testable without a simulator. If the
/// library ever grows past the point where loading it whole is sensible, that
/// is the moment to introduce a real store — behind this same protocol.
protocol OfflineStore: Sendable {
    func read(_ name: String) -> Data?
    func write(_ name: String, _ contents: Data)
    func delete(_ name: String)
}

/// Files in the app's caches directory.
///
/// Caches rather than Documents for the snapshots — the system may reclaim them
/// under pressure, and losing a cached library costs a refresh. The pending
/// queue is the exception and says so.
struct FileOfflineStore: OfflineStore {

    private let directory: URL

    init(directory: URL? = nil) {
        let base = directory ?? URL.cachesDirectory.appending(path: "offline")
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        self.directory = base
    }

    func read(_ name: String) -> Data? {
        try? Data(contentsOf: directory.appending(path: name))
    }

    func write(_ name: String, _ contents: Data) {
        // .atomic, so a process killed mid-write leaves the previous snapshot
        // intact rather than a truncated one. The reader tolerates corruption,
        // but only by discarding it, which loses queued work.
        try? contents.write(to: directory.appending(path: name), options: .atomic)
    }

    func delete(_ name: String) {
        try? FileManager.default.removeItem(at: directory.appending(path: name))
    }
}

/// For tests and previews.
final class InMemoryOfflineStore: OfflineStore, @unchecked Sendable {

    private let lock = NSLock()
    private var files: [String: Data] = [:]

    func read(_ name: String) -> Data? {
        lock.withLock { files[name] }
    }

    func write(_ name: String, _ contents: Data) {
        lock.withLock { files[name] = contents }
    }

    func delete(_ name: String) {
        _ = lock.withLock { files.removeValue(forKey: name) }
    }
}
