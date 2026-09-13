import Foundation
import Observation
import WatchGuruAPI

/// Connecting a media server: Plex, Jellyfin or Emby.
///
/// One model for all three, because the connection is identical in every
/// respect the screen can see. Which server this is comes in from the view, and
/// everything that differs between them — what it is called, where to paste the
/// URL, whether a username is required — comes back from the server rather than
/// being three copies of a screen here.
///
/// The webhook URL is held in memory for exactly as long as the screen lives.
/// It is deliberately not saved: the server keeps only a hash of it, so
/// anything this app wrote down would be the only copy in existence and a copy
/// nobody asked it to keep.
@Observable
@MainActor
final class MediaServerModel {

    private let client: WatchGuruClient
    let service: String

    var state: ViewState<MediaServerStatusResponse> = .loading
    /// The URL just issued, and what to do with it, until dismissed.
    var issuedURL: String?
    var setUpHint: String?
    var isBusy = false
    var actionFailed = false

    init(client: WatchGuruClient, service: String) {
        self.client = client
        self.service = service
    }

    func load() async {
        if let current = state.value {
            state = .refreshing(current)
        } else {
            state = .loading
        }
        do {
            // Hoisted rather than inlined into the case, as everywhere else
            // here; TitleDetailModel carries the long version of this note.
            let status = try await client.mediaServerStatus(service: service)
            state = .content(status)
        } catch {
            state = .failed(error)
        }
    }

    func connect(accountName: String?) async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            let connection = try await client.connectMediaServer(
                service: service, accountName: accountName)
            issuedURL = connection.webhookUrl
            setUpHint = connection.setUpHint
            await load()
        } catch {
            // A 409 here is the honest case: Jellyfin and Emby refuse to
            // connect without the username whose viewing counts.
            actionFailed = true
        }
    }

    func disconnect() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }

        do {
            try await client.disconnectMediaServer(service: service)
            issuedURL = nil
            setUpHint = nil
            await load()
        } catch {
            actionFailed = true
        }
    }
}
