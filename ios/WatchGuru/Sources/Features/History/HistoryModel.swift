import Foundation
import Observation
import WatchGuruAPI

/// Which kinds of viewing the timeline is showing.
enum HistoryType: String, CaseIterable, Identifiable {
    case all
    case films
    case series

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: "Everything"
        case .films: "Films"
        case .series: "Series"
        }
    }

    var api: WatchHistoryControllerAPI.ModelType_getHistory? {
        switch self {
        case .all: nil
        case .films: .movie
        case .series: .tvSeries
        }
    }
}

/// The user's viewing history, and the place mistakes get corrected.
///
/// Backed by the append-only watch-event log, which is the real record of what
/// happened — the library and per-episode state are derived from it. That is
/// also why editing goes to the server rather than being applied here: moving a
/// date changes what those derived rows should say, and two answers to that
/// question is one too many.
///
/// Filtering is a fresh request rather than a filter over what is already
/// loaded. The list is paged, so filtering a page would search the last fifty
/// viewings and call it a search of the history.
@Observable
@MainActor
final class HistoryModel {

    private(set) var state: ViewState<[WatchEventResponse]> = .loading
    private(set) var services: [StreamingServiceResponse] = []

    var query = ""
    var type: HistoryType = .all
    var serviceId: Int64?

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
            let events = try await client.history(
                type: type.api, serviceId: serviceId, query: query)
            state = events.isEmpty ? .empty : .content(events)
        } catch {
            state = .failed(error)
        }
    }

    /// The services, for the filter row and the editor's picker.
    func loadServices() async {
        guard services.isEmpty else { return }
        do {
            let all = try await client.streamingServices()
            services = all.sorted { $0.name < $1.name }
        } catch {
            // A missing picker is a smaller problem than an error over the
            // history itself, which loaded fine.
            services = []
        }
    }

    /// Corrects one entry.
    ///
    /// Reloads rather than patching the row in place: moving a date can move
    /// the entry into a different day and change which viewing counts as the
    /// rewatch. Both are the server's arithmetic, and guessing at them here
    /// would show something that disagrees with the next refresh.
    func edit(_ event: WatchEventResponse, watchedAt: Date?, serviceId: Int64?) async {
        do {
            _ = try await client.updateWatchEvent(
                eventId: event.id, watchedAt: watchedAt, serviceId: serviceId)
            await load()
        } catch {
            // The list is unchanged, so there is nothing to roll back; the next
            // load will still show the old value, which is the truth.
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
    /// question this screen exists to answer. The grouping itself lives in
    /// `HistoryDays`, where it is tested.
    var days: [HistoryDays.Day] {
        HistoryDays.group(state.value ?? [])
    }
}
