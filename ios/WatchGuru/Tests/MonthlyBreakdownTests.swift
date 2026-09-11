import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// The parts of the monthly chart that can be checked — the spoken summary
/// above all, since nothing else in the app would notice if it were wrong.
struct MonthlyBreakdownTests {

    private let english = Locale(identifier: "en_US")

    private func month(_ year: Int, _ month: Int, _ minutes: Int64) -> MonthBucket {
        MonthBucket(minutes: minutes, month: month, viewings: 1, year: year)
    }

    @Test("the summary reads every month in order, with its value")
    func summaryReadsEveryMonth() {
        let spoken = MonthlyBreakdown.spokenSummary(
            [month(2026, 1, 120), month(2026, 2, 45)], locale: english)

        #expect(spoken.hasPrefix("Minutes watched by month: "))
        #expect(spoken.contains("120 minutes"))
        #expect(spoken.contains("45 minutes"))
    }

    @Test("an empty period says so rather than reading an empty list")
    func emptySummary() {
        #expect(MonthlyBreakdown.spokenSummary([], locale: english).contains("No viewing"))
    }

    @Test("the busiest month is the one with the most minutes")
    func busiestMonth() {
        let busiest = MonthlyBreakdown.busiest(
            [month(2026, 1, 120), month(2026, 2, 300), month(2026, 3, 45)])

        #expect(busiest?.month == 2)
    }

    @Test("a period where nothing was watched has no busiest month")
    func noBusiestMonth() {
        #expect(MonthlyBreakdown.busiest([month(2026, 1, 0), month(2026, 2, 0)]) == nil)
    }

    @Test("labels carry the year, so December and January do not blur together")
    func labelsCarryTheYear() {
        #expect(MonthlyBreakdown.label(month(2025, 12, 10), locale: english).contains("2025"))
        #expect(MonthlyBreakdown.label(month(2026, 1, 10), locale: english).contains("2026"))
    }
}
