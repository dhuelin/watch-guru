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

    /// What people are watching this week, shown while the box is empty.
    ///
    /// Held apart from `state` rather than loaded into it: returning to an
    /// empty box should not cost a request, and a search that found nothing
    /// must still read as "nothing found" rather than silently becoming a
    /// chart.
    private(set) var trending: ViewState<[SearchHit]> = .loading

    /// Whether the box holds enough to have searched for anything.
    var isSearching: Bool {
        query.trimmingCharacters(in: .whitespacesAndNewlines).count >= minimumQueryLength
    }

    /// The list on screen, chosen by the box rather than by whichever load
    /// finished last.
    var visible: ViewState<[SearchHit]> { isSearching ? state : trending }

    var query: String = "" {
        didSet { scheduleSearch() }
    }

    private let client: WatchGuruClient
    private let offline: OfflineClient
    private var searchTask: Task<Void, Never>?

    init(client: WatchGuruClient, offline: OfflineClient) {
        self.client = client
        self.offline = offline
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
        if isSearching {
            scheduleSearch()
        } else {
            Task { await loadTrending() }
        }
    }

    /// Loads the shelf once, when the screen first appears.
    func loadTrending() async {
        trending = .loading
        do {
            let page = try await client.trending()
            trending = page.results.isEmpty ? .empty : .content(page.results)
        } catch {
            // Shown as an error rather than as an empty shelf: the screen has
            // nothing else on it, and "nothing is trending this week" is a
            // claim about the world rather than about the network.
            trending = .failed(error)
        }
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
        // Queued counts as added: it is stored and it will be sent. Only a
        // refusal from the server leaves the row untouched.
        if case .failed = await offline.addToLibrary(request) { return }
        added.insert(hit.providerId)
    }
}
