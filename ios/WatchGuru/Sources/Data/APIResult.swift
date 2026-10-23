import Foundation

/// What went wrong talking to the backend.
///
/// Deliberately not a bare `Error`: these cases are shown differently, and
/// collapsing them pushes the "what do we tell the user" decision into every
/// screen. The distinction that matters most is ``offline`` versus
/// ``upstream`` — the backend keeps serving a user's own library when TMDB is
/// down, so "the catalogue is unavailable" and "you have no connection" are
/// genuinely different, and only one of them means their data is unreachable.
enum APIFailure: Error, Equatable, Sendable {
    /// No usable network. Reads may still be served from cache.
    case offline
    /// Token missing, expired or rejected. Re-authenticate.
    case unauthorised
    /// Absent, or belongs to someone else.
    case notFound
    /// The backend reached us but could not reach TMDB.
    case upstream
    case unexpected(status: Int?, message: String?)
}

extension APIFailure {

    /// The heading over ``message`` on a full-screen failure.
    ///
    /// Never the message again. The generic case used to say "Something went
    /// wrong" as the heading and "Something went wrong." beneath it, which
    /// spends two lines on one thought and leaves the reader with neither what
    /// failed nor what to do.
    var title: String {
        switch self {
        case .offline: String(localized: "Offline")
        case .upstream: String(localized: "Catalogue unavailable")
        case .notFound: String(localized: "Not available")
        case .unauthorised, .unexpected: String(localized: "Couldn't load this")
        }
    }

    /// The one line the user reads. The offline and provider-down sentences are
    /// in the copy table in `docs/DESIGN.md` and shared with Android verbatim.
    var message: String {
        switch self {
        case .offline:
            String(localized: "Offline — changes will sync when you reconnect.")
        case .upstream:
            String(localized: "Couldn't reach the catalogue. Your library is still up to date.")
        case .notFound:
            String(localized: "This may have been removed, or it isn't in the catalogue yet.")
        case .unauthorised, .unexpected:
            String(localized: "Something went wrong.")
        }
    }

    var symbol: String {
        switch self {
        case .offline, .upstream: "icloud.slash"
        // A warning, not a magnifying glass: the glass is this app's symbol
        // for search, and on a failure it read as "no results".
        case .notFound, .unauthorised, .unexpected: "exclamationmark.triangle"
        }
    }
}

/// What a screen is showing right now.
///
/// Four states, all designed. `refreshing` is separate from `loading` because
/// a cached screen must keep its content while updating underneath — replacing
/// a populated library with a spinner every time the user returns to it is
/// worse than briefly stale rows.
enum ViewState<Value> {
    case loading
    case refreshing(Value)
    case content(Value)
    case empty
    case failed(APIFailure)

    var value: Value? {
        switch self {
        case .content(let value), .refreshing(let value): value
        case .loading, .empty, .failed: nil
        }
    }
}
