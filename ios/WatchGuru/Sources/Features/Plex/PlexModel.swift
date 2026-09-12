import Foundation
import Observation
import WatchGuruAPI

/// Connecting a Plex server.
///
/// The webhook URL is held here, in memory, for exactly as long as the screen
/// lives. It is deliberately not saved anywhere: the server keeps only a hash
/// of it, so anything this app wrote down would be the only copy in existence
/// and a copy nobody asked it to keep. Losing it costs one tap, which issues a
/// new URL and retires the old.
@Observable
@MainActor
final class PlexModel {

    private let client: WatchGuruClient

    var state: ViewState<PlexStatusResponse> = .loading
    /// The URL just issued, shown until the user dismisses it or leaves.
    var issuedURL: String?
    var isBusy = false
    var actionFailed = false

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
            state = .content(try await client.plexStatus())
        } catch let failure {
            state = .failed(failure)
        }
    }

    func connect(plexUsername: String?) async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            let connection = try await client.connectPlex(plexUsername: plexUsername)
            issuedURL = connection.webhookUrl
            // The connect response is authoritative about the link but knows
            // nothing about past deliveries, so the full status is fetched
            // behind the URL the user is already reading.
            await load()
        } catch {
            actionFailed = true
        }
    }

    func disconnect() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            try await client.disconnectPlex()
            issuedURL = nil
            await load()
        } catch {
            actionFailed = true
        }
    }
}
