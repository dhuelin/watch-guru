import Foundation
import WatchGuruAPI

/// The app's single door to the backend.
///
/// Views never touch the generated API types directly. Everything here throws
/// ``APIFailure``, so no screen handles an HTTP status code, and the mapping
/// from "what went wrong" to "what the user is told" is made once.
///
/// An actor because the bearer token is mutable shared state: it changes on
/// sign-in and sign-out while requests may be in flight.
actor WatchGuruClient {

    private let tokens: TokenStore
    private let sessions: SessionClient
    private let configuration: WatchGuruAPIAPIConfiguration

    /// The renewal in progress, if any.
    ///
    /// Refresh tokens rotate, and the backend treats a token presented twice as
    /// theft and revokes the whole session. Two calls that 401 at the same
    /// moment must therefore produce one renewal, not two — so the second joins
    /// the first rather than starting its own. The check-and-set is safe
    /// because it runs without an `await` between reading and writing, so the
    /// actor cannot interleave another task in the middle.
    private var renewal: Task<Tokens, Error>?

    /// Called when the refresh token is refused. Not transient: it means
    /// revoked, expired, or detected as reused, and the user must sign in
    /// again.
    private var onSessionLost: (@Sendable () -> Void)?

    init(baseURL: URL, tokens: TokenStore, sessions: SessionClient? = nil) {
        self.tokens = tokens
        self.sessions = sessions ?? SessionClient(baseURL: baseURL)
        // A configuration of our own rather than the shared singleton, so tests
        // and previews can hold several clients without fighting over global
        // state.
        self.configuration = WatchGuruAPIAPIConfiguration(basePath: baseURL.absoluteString)
    }

    func setOnSessionLost(_ handler: @escaping @Sendable () -> Void) {
        onSessionLost = handler
    }

    /// Refreshes the Authorization header from the token store.
    ///
    /// Applied before each call rather than once at construction, so a sign-in
    /// or sign-out takes effect on the very next request without rebuilding the
    /// client.
    private func applyAuthorization() {
        if let session = tokens.tokens() {
            configuration.customHeaders["Authorization"] = "Bearer \(session.accessToken)"
        } else {
            configuration.customHeaders.removeValue(forKey: "Authorization")
        }
    }

    /// Renews the session, joining a renewal already under way.
    ///
    /// - Parameter presented: the refresh token the caller believes is current.
    ///   If the store has moved on, another call already renewed and this one
    ///   takes that result instead of spending a second, rotated token.
    private func renewSession(presented: String) async -> Tokens? {
        if let existing = renewal {
            return try? await existing.value
        }
        if let current = tokens.tokens(), current.refreshToken != presented {
            // Renewed while this call was in flight.
            return current
        }

        let task = Task { [sessions] () throws -> Tokens in
            try await sessions.refresh(refreshToken: presented)
        }
        renewal = task
        defer { renewal = nil }

        guard let renewed = try? await task.value else {
            tokens.clear()
            onSessionLost?()
            return nil
        }
        tokens.save(renewed)
        return renewed
    }

    /// Whether a failed call is worth retrying after a renewal, and with what.
    ///
    /// Only `.unauthorised`, and only once — a retry that is refused again
    /// means the fresh token was rejected too, and renewing a second time
    /// cannot change that.
    private func renewedSession(after failure: APIFailure) async -> Tokens? {
        guard case .unauthorised = failure, let current = tokens.tokens() else { return nil }
        return await renewSession(presented: current.refreshToken)
    }

    // MARK: - Profile

    func profile() async throws(APIFailure) -> UserResponse {
        try await run { try await MeControllerAPI.getProfile(apiConfiguration: $0) }
    }

    func updateProfile(_ update: UpdateProfile) async throws(APIFailure) -> UserResponse {
        try await run { try await MeControllerAPI.updateProfile(updateProfile: update, apiConfiguration: $0) }
    }

    func deleteAccount() async throws(APIFailure) {
        try await runVoid { try await MeControllerAPI.deleteAccount(apiConfiguration: $0) }
    }

    // MARK: - Catalogue

    /// The backend answers queries shorter than two characters with an empty
    /// page rather than an error, so the UI needs no matching rule — but it
    /// debounces anyway, because a request per keystroke is wasteful even when
    /// it is cheap.
    func search(_ query: String, page: Int = 1) async throws(APIFailure) -> SearchResponse {
        try await run { try await TitleControllerAPI.searchTitles(query: query, page: page, apiConfiguration: $0) }
    }

    func title(_ id: Int64) async throws(APIFailure) -> TitleResponse {
        try await run { try await TitleControllerAPI.getTitle(titleId: id, apiConfiguration: $0) }
    }

    // MARK: - Library

    func library(
        status: WatchlistControllerAPI.Status_listWatchlist? = nil
    ) async throws(APIFailure) -> [WatchlistItemResponse] {
        try await run { try await WatchlistControllerAPI.listWatchlist(status: status, apiConfiguration: $0) }
    }

    func addToLibrary(_ request: AddToWatchlist) async throws(APIFailure) -> WatchlistItemResponse {
        try await run {
            try await WatchlistControllerAPI.addToWatchlist(addToWatchlist: request, apiConfiguration: $0)
        }
    }

    func updateLibraryItem(
        itemId: Int64,
        _ update: UpdateWatchlistItem
    ) async throws(APIFailure) -> WatchlistItemResponse {
        try await run {
            try await WatchlistControllerAPI.updateWatchlistItem(
                itemId: itemId, updateWatchlistItem: update, apiConfiguration: $0
            )
        }
    }

    func removeFromLibrary(itemId: Int64) async throws(APIFailure) {
        try await runVoid {
            try await WatchlistControllerAPI.removeFromWatchlist(itemId: itemId, apiConfiguration: $0)
        }
    }

    /// Every season of a series with the caller's watched state already folded
    /// in — one call, because an episode list without its ticks is not a screen
    /// anyone wants.
    func seasons(titleId: Int64) async throws(APIFailure) -> SeasonsResponse {
        try await run { try await TitleControllerAPI.getSeasons(titleId: titleId, apiConfiguration: $0) }
    }

    func progress(titleId: Int64) async throws(APIFailure) -> TitleProgress {
        try await run { try await WatchlistControllerAPI.getTitleProgress(titleId: titleId, apiConfiguration: $0) }
    }

    // MARK: - History

    /// - Parameter watchedAt: when the user watched it, defaulting to now.
    ///   Passed explicitly by the offline replay, which must record the moment
    ///   the user actually watched rather than the moment the train reached
    ///   signal.
    func markEpisodeWatched(
        episodeId: Int64,
        watchedAt: Date? = nil
    ) async throws(APIFailure) -> WatchEventResponse {
        try await run {
            try await WatchHistoryControllerAPI.logEpisodeWatched(
                logEpisodeWatched: LogEpisodeWatched(episodeId: episodeId, watchedAt: watchedAt),
                apiConfiguration: $0
            )
        }
    }

    /// Marks everything up to and including one episode.
    ///
    /// One request rather than one per episode, and idempotent on the server,
    /// so a mis-tap cannot turn a season into rewatches.
    func markWatchedUpTo(episodeId: Int64) async throws(APIFailure) -> BulkMarkResponse {
        try await run {
            try await WatchHistoryControllerAPI.markWatchedUpTo(
                markWatchedUpTo: MarkWatchedUpTo(episodeId: episodeId),
                apiConfiguration: $0
            )
        }
    }

    /// Removes an episode from the watched history entirely.
    ///
    /// Idempotent server-side, so a retry after a dropped response is safe.
    func unmarkEpisode(episodeId: Int64) async throws(APIFailure) {
        try await runVoid {
            try await WatchHistoryControllerAPI.unmarkEpisode(episodeId: episodeId, apiConfiguration: $0)
        }
    }

    /// Deletes one history entry, leaving other rewatches of it intact.
    func deleteWatchEvent(eventId: Int64) async throws(APIFailure) {
        try await runVoid {
            try await WatchHistoryControllerAPI.deleteWatchEvent(eventId: eventId, apiConfiguration: $0)
        }
    }

    /// The next unwatched episode of every series in progress.
    func upNext(limit: Int = 20) async throws(APIFailure) -> [UpNextResponse] {
        try await run { try await WatchHistoryControllerAPI.getUpNext(limit: limit, apiConfiguration: $0) }
    }

    func history(page: Int = 0, size: Int = 50) async throws(APIFailure) -> [WatchEventResponse] {
        try await run {
            try await WatchHistoryControllerAPI.getHistory(page: page, size: size, apiConfiguration: $0)
        }
    }

    func stats(months: Int = 12) async throws(APIFailure) -> WatchStats {
        try await run { try await WatchHistoryControllerAPI.getStats(months: months, apiConfiguration: $0) }
    }

    // MARK: - Failure mapping

    /// Runs one call, renewing the session once if the API says the access
    /// token is no longer good.
    ///
    /// Acting on the server's 401 rather than on this device's clock is
    /// deliberate: the server decides when a token is dead, and a device with a
    /// wrong clock would otherwise renew constantly or never.
    private func run<T>(
        _ operation: (WatchGuruAPIAPIConfiguration) async throws -> T
    ) async throws(APIFailure) -> T {
        do {
            return try await attempt(operation)
        } catch let failure {
            guard await renewedSession(after: failure) != nil else { throw failure }
            return try await attempt(operation)
        }
    }

    private func runVoid(
        _ operation: (WatchGuruAPIAPIConfiguration) async throws -> Void
    ) async throws(APIFailure) {
        do {
            try await attempt(operation)
        } catch let failure {
            guard await renewedSession(after: failure) != nil else { throw failure }
            try await attempt(operation)
        }
    }

    @discardableResult
    private func attempt<T>(
        _ operation: (WatchGuruAPIAPIConfiguration) async throws -> T
    ) async throws(APIFailure) -> T {
        applyAuthorization()
        do {
            return try await operation(configuration)
        } catch let error as ErrorResponse {
            throw WatchGuruClient.failure(for: error)
        } catch let error as URLError {
            throw WatchGuruClient.failure(for: error)
        } catch {
            throw .unexpected(status: nil, message: error.localizedDescription)
        }
    }

    /// Maps an HTTP status onto something the UI knows how to show.
    static func failure(for error: ErrorResponse) -> APIFailure {
        switch error {
        case .error(let status, _, _, let underlying):
            // A transport failure surfaces here wrapped rather than as a bare
            // URLError, so unwrap before deciding this was the server's fault.
            if let urlError = underlying as? URLError {
                return failure(for: urlError)
            }
            switch status {
            case 401, 403: return .unauthorised
            case 404: return .notFound
            // The backend reports an unreachable or unconfigured TMDB as
            // 502/503 while still serving the user's own data, so this must
            // not read as a total outage.
            case 502, 503: return .upstream
            default: return .unexpected(status: status, message: nil)
            }
        }
    }

    static func failure(for error: URLError) -> APIFailure {
        switch error.code {
        case .notConnectedToInternet, .networkConnectionLost,
             .cannotFindHost, .cannotConnectToHost, .timedOut,
             .dataNotAllowed, .internationalRoamingOff:
            // All one thing to a user standing in a lift.
            return .offline
        default:
            return .unexpected(status: nil, message: error.localizedDescription)
        }
    }
}
