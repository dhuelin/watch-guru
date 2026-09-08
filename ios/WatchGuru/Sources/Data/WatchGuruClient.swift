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
    private let configuration: WatchGuruAPIAPIConfiguration

    init(baseURL: URL, tokens: TokenStore) {
        self.tokens = tokens
        // A configuration of our own rather than the shared singleton, so tests
        // and previews can hold several clients without fighting over global
        // state.
        self.configuration = WatchGuruAPIAPIConfiguration(basePath: baseURL.absoluteString)
    }

    /// Refreshes the Authorization header from the token store.
    ///
    /// Applied before each call rather than once at construction, so a sign-in
    /// or sign-out takes effect on the very next request without rebuilding the
    /// client.
    private func applyAuthorization() {
        if let token = tokens.token() {
            configuration.customHeaders["Authorization"] = "Bearer \(token)"
        } else {
            configuration.customHeaders.removeValue(forKey: "Authorization")
        }
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
        status: WatchlistControllerAPI.StatusListWatchlist? = nil
    ) async throws(APIFailure) -> [WatchlistItemResponse] {
        try await run { try await WatchlistControllerAPI.listWatchlist(status: status, apiConfiguration: $0) }
    }

    func addToLibrary(_ request: AddToWatchlist) async throws(APIFailure) -> WatchlistItemResponse {
        try await run {
            try await WatchlistControllerAPI.addToWatchlist(addToWatchlist: request, apiConfiguration: $0)
        }
    }

    func removeFromLibrary(itemId: Int64) async throws(APIFailure) {
        try await runVoid {
            try await WatchlistControllerAPI.removeFromWatchlist(itemId: itemId, apiConfiguration: $0)
        }
    }

    func progress(titleId: Int64) async throws(APIFailure) -> TitleProgress {
        try await run { try await WatchlistControllerAPI.getTitleProgress(titleId: titleId, apiConfiguration: $0) }
    }

    // MARK: - History

    func markEpisodeWatched(episodeId: Int64) async throws(APIFailure) -> WatchEventResponse {
        try await run {
            try await WatchHistoryControllerAPI.logEpisodeWatched(
                logEpisodeWatched: LogEpisodeWatched(episodeId: episodeId),
                apiConfiguration: $0
            )
        }
    }

    func stats(months: Int = 12) async throws(APIFailure) -> WatchStats {
        try await run { try await WatchHistoryControllerAPI.getStats(months: months, apiConfiguration: $0) }
    }

    // MARK: - Failure mapping

    private func run<T>(
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

    private func runVoid(
        _ operation: (WatchGuruAPIAPIConfiguration) async throws -> Void
    ) async throws(APIFailure) {
        applyAuthorization()
        do {
            try await operation(configuration)
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
