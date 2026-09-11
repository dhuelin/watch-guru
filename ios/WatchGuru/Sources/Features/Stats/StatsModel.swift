import Foundation
import Observation
import WatchGuruAPI

/// Which slice of the history the screen is showing.
enum StatsPeriod: String, CaseIterable, Identifiable {
    case month
    case year
    case allTime

    var id: String { rawValue }

    var label: String {
        switch self {
        case .month: "This month"
        case .year: "This year"
        case .allTime: "All time"
        }
    }

    var api: WatchHistoryControllerAPI.Period_getStats {
        switch self {
        case .month: .month
        case .year: .year
        case .allTime: .allTime
        }
    }
}

/// Everything the server already knows about what this person has watched.
///
/// Each period is a fresh request rather than a slice of a cached answer: the
/// figures are computed in SQL over the append-only event log, and filtering a
/// trimmed payload on the phone would produce totals that disagree with the
/// ones the server would give.
@Observable
@MainActor
final class StatsModel {

    private let client: WatchGuruClient

    var state: ViewState<WatchStats> = .loading
    var period: StatsPeriod = .allTime

    init(client: WatchGuruClient) {
        self.client = client
    }

    func select(_ period: StatsPeriod) async {
        guard period != self.period else { return }
        self.period = period
        await load()
    }

    func load() async {
        // Keep the numbers on screen while the new ones arrive: replacing a
        // populated screen with a spinner on every tap of the period selector
        // is worse than briefly stale figures.
        if let current = state.value {
            state = .refreshing(current)
        }
        do {
            // Hoisted rather than inlined into the case; see TitleDetailModel.
            let stats = try await client.stats(period: period.api)
            state = .content(stats)
        } catch {
            state = .failed(error)
        }
    }
}
