import Foundation
import Observation
import WatchGuruAPI

@Observable
@MainActor
final class LibraryModel {

    private(set) var state: ViewState<[WatchlistItemResponse]> = .loading
    var filter: WatchlistControllerAPI.Status_listWatchlist? {
        didSet {
            guard filter != oldValue else { return }
            Task { await load() }
        }
    }

    private let client: WatchGuruClient
    private let offline: OfflineClient

    init(client: WatchGuruClient, offline: OfflineClient) {
        self.client = client
        self.offline = offline
    }

    func load() async {
        // Keep showing what is already on screen while the refresh runs.
        // Replacing a populated library with a spinner every time the user
        // returns to the tab is worse than briefly stale rows.
        if let current = state.value {
            state = .refreshing(current)
        } else {
            state = .loading
        }

        do {
            let items = try await offline.library(status: filter)
            state = items.isEmpty ? .empty : .content(items)
        } catch {
            state = .failed(error)
        }
    }

    /// Removes an item, optimistically.
    ///
    /// The row goes immediately and comes back if the call fails, so the user
    /// sees the outcome they asked for and a failure restores exactly what was
    /// there.
    func remove(_ item: WatchlistItemResponse) async {
        guard let before = state.value else { return }
        state = .content(before.filter { $0.id != item.id })

        // Queued counts as done for the undo snackbar: the row is gone locally
        // and the removal will reach the server. Only a refusal puts it back.
        if case .failed = await offline.removeFromLibrary(itemId: item.id) {
            state = .content(before)
        }
    }
}
