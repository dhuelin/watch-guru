import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// Grouping the history into days.
///
/// The interesting case is not the middle of an afternoon: it is the viewing
/// just after midnight, which belongs to a different day depending on whose
/// clock you ask. The user's own is the answer, because that is the calendar
/// they are reading the screen against.
struct HistoryDaysTests {

    private var zurich: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Zurich")!
        return calendar
    }

    @Test("viewings on the same local day are one section")
    func sameDayIsOneSection() {
        let days = HistoryDays.group(
            [event(id: 1, at: "2026-09-13T21:00:00Z"), event(id: 2, at: "2026-09-13T19:00:00Z")],
            calendar: zurich)

        #expect(days.count == 1)
        #expect(days.first?.events.count == 2)
    }

    @Test("a late viewing is filed under the day the user was living in")
    func lateViewingBelongsToTheLocalDay() {
        // 23:30 UTC on the 13th is 01:30 on the 14th in Zurich.
        let days = HistoryDays.group([event(id: 1, at: "2026-09-13T23:30:00Z")], calendar: zurich)
        let components = zurich.dateComponents([.year, .month, .day], from: days.first!.date)

        #expect(components.day == 14)
        #expect(components.month == 9)
    }

    @Test("days come back newest first")
    func newestFirst() {
        let days = HistoryDays.group(
            [event(id: 1, at: "2026-09-10T19:00:00Z"), event(id: 2, at: "2026-09-13T19:00:00Z")],
            calendar: zurich)

        #expect(days.count == 2)
        #expect(days.first!.date > days.last!.date)
    }

    @Test("the order within a day is the order the server sent")
    func keepsServerOrderWithinADay() {
        // Dozens of events share one instant after a "mark watched up to here",
        // and the server breaks the tie by id. Re-sorting here would undo that
        // and make the list shuffle between refreshes.
        let days = HistoryDays.group(
            [event(id: 9, at: "2026-09-13T19:00:00Z"),
             event(id: 3, at: "2026-09-13T19:00:00Z"),
             event(id: 7, at: "2026-09-13T19:00:00Z")],
            calendar: zurich)

        #expect(days.first!.events.map(\.id) == [9, 3, 7])
    }

    @Test("nothing watched is no days")
    func emptyIsEmpty() {
        #expect(HistoryDays.group([], calendar: zurich).isEmpty)
    }

    @Test("a day says how much viewing it holds")
    func summarisesADay() {
        let one = HistoryDays.Day(date: Date(), events: [event(id: 1, at: "2026-09-13T19:00:00Z")])
        let several = HistoryDays.Day(
            date: Date(),
            events: [event(id: 1, at: "2026-09-13T19:00:00Z"),
                     event(id: 2, at: "2026-09-13T20:00:00Z")])

        #expect(HistoryDays.spokenSummary(one) == "1 viewing")
        #expect(HistoryDays.spokenSummary(several) == "2 viewings")
    }

    private func event(id: Int64, at iso: String) -> WatchEventResponse {
        WatchEventResponse(
            id: id,
            primaryTitle: "Heat",
            rewatch: false,
            titleId: 1,
            watchedAt: ISO8601DateFormatter().date(from: iso)!)
    }
}
