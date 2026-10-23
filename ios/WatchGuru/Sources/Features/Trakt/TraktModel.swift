import Foundation
import Observation
import WatchGuruAPI

/// Connecting Trakt.
///
/// Authorising happens in a browser session. Trakt sends the browser to the
/// backend's callback, which redirects to ``callbackScheme``; the session closes
/// on that and hands the URL back here, and ``finish(callback:)`` completes the
/// flow with this app's own session. The server will only connect Trakt for the
/// account that started the flow, so an authorisation link forwarded to someone
/// else connects nothing.
@Observable
@MainActor
final class TraktModel {

    private let client: WatchGuruClient

    var state: ViewState<TraktStatusResponse> = .loading
    /// What the sync the user just asked for did, until they leave.
    var lastSync: SyncResultResponse?
    var isBusy = false
    /// From asking for an authorisation URL until the browser hands back.
    var isConnecting = false
    var failureMessage: String?

    init(client: WatchGuruClient) {
        self.client = client
    }

    func load() async {
        if let current = state.value {
            state = .refreshing(current)
        } else {
            state = .loading
        }
        do {
            let status = try await client.traktStatus()
            state = .content(status)
        } catch {
            state = .failed(error)
        }
    }

    /// - Returns: where to send the user to approve, or nil if it could not start.
    func authorizationURL() async -> URL? {
        guard !isBusy else { return nil }
        isBusy = true
        defer { isBusy = false }

        do {
            let authorization = try await client.authorizeTrakt()
            guard let url = URL(string: authorization.authorizeUrl),
                  Self.isTraktAuthorizationURL(url) else {
                // Whatever the server sent, the app only ever sends the user
                // to Trakt: a sign-in page anywhere else is not one to trust.
                failureMessage = "Trakt cannot be connected from this server right now."
                return nil
            }
            return url
        } catch {
            // A 409 here is the honest case: this server has no Trakt
            // application configured, and retrying changes nothing.
            failureMessage = "Trakt cannot be connected from this server right now."
            return nil
        }
    }

    /// The scheme the backend's callback redirects to. The browser session
    /// watches for it; nothing else in the app registers it.
    nonisolated static let callbackScheme = "watchguru"

    /// Completes an authorisation from the URL the browser session returned.
    func finish(callback: URL) async {
        guard let parameters = Self.callbackParameters(callback) else {
            failureMessage = "Trakt did not finish connecting. Try again."
            return
        }
        isBusy = true
        defer { isBusy = false }

        do {
            // Hoisted for the region based isolation checker; see syncNow.
            let status = try await client.completeTrakt(code: parameters.code, state: parameters.state)
            state = .content(status)
        } catch {
            failureMessage = "Trakt did not finish connecting. Start again from here."
        }
    }

    /// The code and state from `watchguru://trakt/callback?code=…&state=…`, or
    /// nil for anything else.
    nonisolated static func callbackParameters(_ url: URL) -> (code: String, state: String)? {
        guard url.scheme?.lowercased() == callbackScheme,
              url.host?.lowercased() == "trakt",
              url.path == "/callback",
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
              let code = items.first(where: { $0.name == "code" })?.value, !code.isEmpty,
              let state = items.first(where: { $0.name == "state" })?.value, !state.isEmpty
        else {
            return nil
        }
        return (code, state)
    }

    /// Only an https page on trakt.tv is somewhere to send the user to approve.
    nonisolated static func isTraktAuthorizationURL(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "https", let host = url.host?.lowercased() else {
            return false
        }
        return host == "trakt.tv" || host.hasSuffix(".trakt.tv")
    }

    func syncNow() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            // Hoisted, as everywhere else here: an await inlined into an
            // assignment the region based isolation checker cannot follow is
            // what broke the iOS build last time. TitleDetailModel has the
            // long version of this note.
            let result = try await client.syncTrakt()
            lastSync = result
            await load()
        } catch {
            failureMessage = "That sync did not work. Try again."
        }
    }

    func disconnect() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            try await client.disconnectTrakt()
            lastSync = nil
            await load()
        } catch {
            failureMessage = "That did not work. Try again."
        }
    }
}
