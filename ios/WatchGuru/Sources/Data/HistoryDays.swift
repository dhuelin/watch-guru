import Foundation
import WatchGuruAPI

/// The history as days rather than as a list of rows.
///
/// "What did I watch last October" is a question about days, and a flat list of
/// timestamps does not answer it. Here rather than in the view because the
/// grouping is the part that can be wrong: a day boundary is local, and the
/// calendar the user reads has to be the one that decides which day a late
/// viewing belongs to.
enum HistoryDays {

    /// One day's viewing, newest first within the day.
    struct Day: Identifiable, Equatable {
        let date: Date
        let events: [WatchEventResponse]

        /// The day itself identifies the section; two never share one.
        var id: Date { date }
    }

    /// Groups events into days, preserving the order the server sent.
    ///
    /// The server orders by date and then id, which matters for the dozens of
    /// events a "mark watched up to here" writes at the same instant: without a
    /// tiebreak they would shuffle between refreshes, and a list that reorders
    /// itself looks broken.
    static func group(
        _ events: [WatchEventResponse],
        calendar: Calendar = .current
    ) -> [Day] {
        var order: [Date] = []
        var byDay: [Date: [WatchEventResponse]] = [:]

        for event in events {
            let day = calendar.startOfDay(for: event.watchedAt)
            if byDay[day] == nil {
                order.append(day)
            }
            byDay[day, default: []].append(event)
        }

        return order
            .map { Day(date: $0, events: byDay[$0] ?? []) }
            .sorted { $0.date > $1.date }
    }

    /// How many separate viewings a day holds, said in one line.
    ///
    /// A count rather than a list of titles: the titles are directly
    /// underneath, and repeating them in the header would be noise a screen
    /// reader has to hear twice.
    static func spokenSummary(_ day: Day) -> String {
        day.events.count == 1 ? "1 viewing" : "\(day.events.count) viewings"
    }
}
