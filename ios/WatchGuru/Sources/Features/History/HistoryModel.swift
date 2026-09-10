import Foundation
import Observation
import WatchGuruAPI

/// The user's viewing history, and the place mistakes get corrected.
///
/// Backed by the append-only watch-event log, which is the real record of what
/// happened — the library and per-episode state are derived from it.
@Observable
@MainActor
final class HistoryModel {

    private(set) var state: ViewState<[WatchEventResponse]> = .loading

    private let client: WatchGuruClient

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
            let events = try await client.history()
            state = events.isEmpty ? .empty : .content(events)
        } catch {
            state = .failed(error)
        }
    }

    /// Deletes one entry, optimistically.
    ///
    /// The row goes immediately and comes back if the call fails. Deleting the
    /// last entry for an episode also makes that episode unwatched again —
    /// handled server-side, so the reload is what surfaces it rather than
    /// anything guessed here.
    func delete(_ event: WatchEventResponse) async {
        guard let before = state.value else { return }
        state = .content(before.filter { $0.id != event.id })

        do {
            try await client.deleteWatchEvent(eventId: event.id)
            // Re-read: removing an event can change derived state this list
            // does not show, and a stale list would disagree with the library.
            await load()
        } catch {
            state = .content(before)
        }
    }

    /// Grouped by day, newest first — "what did I watch last October" is the
    /// question this screen exists to answer.
    var groupedByDay: [(day: Date, events: [WatchEventResponse])] {
        guard let events = state.value else { return [] }
        let calendar = Calendar.current
        let groups = Dictionary(grouping: events) { calendar.startOfDay(for: $0.watchedAt) }
        return groups
            .sorted { $0.key > $1.key }
            .map { (day: $0.key, events: $0.value) }
    }
}
