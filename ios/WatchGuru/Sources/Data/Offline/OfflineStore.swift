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

/// Files in the App Group container shared with the widget.
///
/// The group container rather than Caches, which is where these lived before the
/// home-screen widget existed. Two reasons, and the second is the one that
/// matters: a widget is a separate process and can only read a shared
/// container, and the pending queue must not be reclaimed by the system under
/// pressure — losing it loses marks the user was told had been accepted. The
/// snapshots could still afford to be reclaimable; splitting them across two
/// directories to express that would buy nothing.
///
/// Falls back to this process's own caches directory when no group is
/// available, which is the case in tests.
struct FileOfflineStore: OfflineStore {

    private let directory: URL

    init(directory: URL? = nil) {
        let base = directory ?? WidgetSharing.offlineDirectory
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
