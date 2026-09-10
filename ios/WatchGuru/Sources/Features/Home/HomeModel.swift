import Foundation
import Observation
import WatchGuruAPI

@Observable
@MainActor
final class HomeModel {

    private(set) var state: ViewState<[UpNextResponse]> = .loading
    /// Title ids with a mark in flight, so one card can be busy without the rest.
    private(set) var marking: Set<Int64> = []

    private let client: WatchGuruClient
    private let offline: OfflineClient

    init(client: WatchGuruClient, offline: OfflineClient) {
        self.client = client
        self.offline = offline
    }

    func load() async {
        if let current = state.value {
            state = .refreshing(current)
        } else {
            state = .loading
        }

        do {
            let entries = try await offline.upNext()
            state = entries.isEmpty ? .empty : .content(entries)
        } catch {
            state = .failed(error)
        }
    }

    /// Marks the shown episode watched and advances the card.
    ///
    /// The point of this screen: record an episode without navigating anywhere.
    /// Progress is re-read afterwards rather than guessed, because the server
    /// decides what "next" is and the card must not claim otherwise.
    func markWatched(_ entry: UpNextResponse) async {
        guard !marking.contains(entry.titleId) else { return }

        marking.insert(entry.titleId)
        defer { marking.remove(entry.titleId) }

        if await offline.markEpisodeWatched(episodeId: entry.nextEpisodeId, titleId: entry.titleId) == .sent {
            await load()
        }
    }
}
