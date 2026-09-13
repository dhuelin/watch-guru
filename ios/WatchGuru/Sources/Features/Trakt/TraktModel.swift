import Foundation
import Observation
import WatchGuruAPI

/// Connecting Trakt.
///
/// Authorising happens in a browser, so this screen cannot watch it happen: the
/// user leaves, approves, and comes back. The view reloads on becoming active
/// again rather than only on appear — coming back to a screen that still says
/// "not connected" after connecting is the failure worth designing against.
@Observable
@MainActor
final class TraktModel {

    private let client: WatchGuruClient

    var state: ViewState<TraktStatusResponse> = .loading
    /// What the sync the user just asked for did, until they leave.
    var lastSync: SyncResultResponse?
    var isBusy = false
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
            return URL(string: authorization.authorizeUrl)
        } catch {
            // A 409 here is the honest case: this server has no Trakt
            // application configured, and retrying changes nothing.
            failureMessage = "Trakt cannot be connected from this server right now."
            return nil
        }
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
