import Foundation

/// What the app and its widget both know.
///
/// A widget on iOS is a separate process in a separate bundle, so nothing is
/// shared by default — not a file, not a Keychain item, not a constant. This
/// file is compiled into both targets and is the only place that says where the
/// shared things are.
///
/// It is deliberately thin. The widget does **not** link the app's networking,
/// the generated API client, or the sync engine: a widget extension has a hard
/// memory budget (tens of megabytes, and it is killed rather than warned), and
/// linking an app into one to reuse four fields is how widgets end up as blank
/// rectangles on somebody's home screen.
enum WidgetSharing {

    /// The App Group both targets are entitled to.
    ///
    /// Must match `com.apple.security.application-groups` in *both*
    /// entitlements files. A mismatch is not a build error: the container
    /// lookup simply returns nil and the widget quietly has no data.
    static let appGroup = "group.dev.dhuelin.watchguru"

    /// Where the offline files live, shared by both processes.
    ///
    /// Falls back to the process's own caches directory when the group is not
    /// available — which happens in unit tests and previews, where there is no
    /// entitlement. The fallback is per-process and therefore unshared, which
    /// is correct for a test and useless for a widget; the widget treats an
    /// empty feed as "nothing to show" rather than as an error, so the failure
    /// mode is a quiet widget rather than a crashing one.
    static var offlineDirectory: URL {
        let base = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)
            ?? URL.cachesDirectory
        return base.appending(path: "offline")
    }

    /// The API, for both the app and the widget's one request.
    ///
    /// One definition rather than two: a widget pointed at a different host
    /// than the app is a bug that looks like an authentication problem.
    ///
    /// The simulator reaches a backend on the developer's machine at
    /// `localhost`; a device does not, which is the usual first surprise.
    static var defaultBaseURL: URL {
        #if DEBUG
        URL(string: "http://localhost:8080")!
        #else
        URL(string: "https://api.watch-guru.example")!
        #endif
    }
}

/// What the widget draws, written by the app.
///
/// A purpose-built file rather than the app's own Up Next cache, whose envelope
/// is generic over the generated `UpNextResponse` — decoding that in the widget
/// would mean linking the whole generated package for six fields. This shape is
/// the widget's contract with the app, and it changes only when the widget
/// needs something new.
struct WidgetFeed: Codable, Equatable, Sendable {

    /// One series and the episode it is waiting on.
    struct Episode: Codable, Equatable, Sendable, Identifiable {
        var id: Int64 { episodeId }

        let titleId: Int64
        let episodeId: Int64
        let title: String
        let episodeCode: String
        let episodeName: String?
        let watchedEpisodes: Int
        let airedEpisodes: Int

        /// `S2E4 · Woe's Hollow`, or just the code when the name is unknown.
        ///
        /// Data rather than copy — a code, a separator and a title the server
        /// supplied — which is why it is a plain `String` and not a localised
        /// format. Here rather than in the view so the app, the widget and the
        /// tests all render a row the same way.
        var subtitle: String {
            episodeName.map { "\(episodeCode) · \($0)" } ?? episodeCode
        }
    }

    let entries: [Episode]

    /// When the app last wrote this.
    ///
    /// The widget shows it when the feed is old, because a home screen gives no
    /// other clue that what it is looking at is from Tuesday.
    let storedAt: Date
}

/// Reading and writing the feed.
///
/// Its own file rather than a key in the snapshot cache: the cache is read
/// through by screens that can fall back to the network, and this one is read by
/// a process that cannot. Keeping them apart means a change to caching policy
/// cannot silently empty the home screen.
struct WidgetFeedStore: Sendable {

    private static let fileName = "widget-feed.json"

    private let directory: URL

    init(directory: URL = WidgetSharing.offlineDirectory) {
        self.directory = directory
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    private var url: URL { directory.appending(path: Self.fileName) }

    func read() -> WidgetFeed? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(WidgetFeed.self, from: data)
    }

    /// - Note: `.atomic`, so a process killed mid-write leaves the previous feed
    ///   rather than a truncated one the widget would discard.
    func write(_ feed: WidgetFeed) {
        guard let data = try? JSONEncoder().encode(feed) else { return }
        try? data.write(to: url, options: .atomic)
    }

    /// Called on sign-out. The feed is the user's viewing, in all but name.
    func clear() {
        try? FileManager.default.removeItem(at: url)
    }
}

extension WidgetFeedStore {

    /// Drops one episode from the feed, leaving the rest.
    ///
    /// Both the app and the widget need this and for the same reason: a mark has
    /// been accepted, and the home screen must stop offering that episode before
    /// anything refetches Up Next. Neither side works out what the *next*
    /// episode of that series is — only the server knows — so the row goes and
    /// the following one moves up.
    ///
    /// `storedAt` is deliberately unchanged: the feed is no fresher than it was,
    /// and claiming otherwise would suppress the "as of" note that tells the
    /// user how old this is.
    func advance(past episodeId: Int64) {
        guard let current = read() else { return }
        write(
            WidgetFeed(
                entries: current.entries.filter { $0.episodeId != episodeId },
                storedAt: current.storedAt
            )
        )
    }
}

/// What became of a mark made from the widget.
enum WidgetMarkOutcome: Equatable {
    /// The server has it.
    case sent
    /// Stored locally; the app will send it.
    case queued
    /// The server refused it. The episode really is still next.
    case refused

    /// What an HTTP status means for a mark.
    ///
    /// The split that matters is queue-or-refuse, and it is not "2xx versus the
    /// rest". A 401 is an expired session, a 429 is a rate limit and a 503 is a
    /// bad minute: all three are worth another attempt by the app, and treating
    /// them as refusals would throw away a mark the user was told had landed.
    /// A 404 or a 400 is the server disagreeing about the request itself, and
    /// retrying that for ever is how a queue wedges.
    init(status: Int) {
        switch status {
        case 200..<300: self = .sent
        case 401, 408, 429, 500...: self = .queued
        default: self = .refused
        }
    }
}
