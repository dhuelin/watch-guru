import Foundation
import Observation
import WatchGuruAPI

/// The minimum the backend acts on; shorter queries come back empty.
private let minimumQueryLength = 2
private let debounceInterval = Duration.milliseconds(300)

@Observable
@MainActor
final class SearchModel {

    private(set) var state: ViewState<[SearchHit]> = .empty
    private(set) var added: Set<Int64> = []

    var query: String = "" {
        didSet { scheduleSearch() }
    }

    private let client: WatchGuruClient
    private var searchTask: Task<Void, Never>?

    init(client: WatchGuruClient) {
        self.client = client
    }

    /// Debounces, then searches.
    ///
    /// Cancelling the previous task is what stops a slow response for "brea"
    /// landing after "break" and overwriting newer results — the classic
    /// search-as-you-type bug.
    private func scheduleSearch() {
        searchTask?.cancel()
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)

        guard text.count >= minimumQueryLength else {
            // Not an error: an empty box is the screen's resting state, and one
            // character is on the way to a real query.
            state = .empty
            return
        }

        searchTask = Task {
            try? await Task.sleep(for: debounceInterval)
            guard !Task.isCancelled else { return }
            await search(text)
        }
    }

    func retry() {
        scheduleSearch()
    }

    private func search(_ text: String) async {
        if let current = state.value {
            state = .refreshing(current)
        } else {
            state = .loading
        }

        do {
            let page = try await client.search(text)
            guard !Task.isCancelled else { return }
            state = page.results.isEmpty ? .empty : .content(page.results)
        } catch {
            guard !Task.isCancelled else { return }
            state = .failed(error)
        }
    }

    /// Adds a hit to the library straight from the results list.
    ///
    /// The backend imports the title from the provider as part of this call,
    /// so there is nothing to do first.
    func addToLibrary(_ hit: SearchHit) async {
        let request = AddToWatchlist(
            providerId: hit.providerId,
            titleType: AddToWatchlist.TitleType(rawValue: hit.titleType.rawValue) ?? .movie
        )
        if (try? await client.addToLibrary(request)) != nil {
            added.insert(hit.providerId)
        }
    }
}
