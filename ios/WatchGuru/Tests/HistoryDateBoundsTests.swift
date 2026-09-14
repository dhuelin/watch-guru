import Foundation
import Testing
@testable import WatchGuru

/// How a chosen day survives the trip to the server.
///
/// The API asks for days; the generated Swift client can only send instants,
/// because it maps `format: date` onto `Date`. So the day the user tapped has
/// to be encoded as an instant whose *UTC* date is that same day -- the server
/// reads it back in UTC. Get this wrong and the range silently covers the wrong
/// day, for some users only, depending where they are.
struct HistoryDateBoundsTests {

    private func calendar(_ zone: String) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: zone)!
        return calendar
    }

    private func utcDay(of date: Date) -> DateComponents {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(secondsFromGMT: 0)!
        return utc.dateComponents([.year, .month, .day], from: date)
    }

    @Test("an evening in Zurich is sent as the day it was, not the next one")
    func eveningInZurich() {
        // 21:00 on the 13th in Zurich is 19:00 UTC the same day, so this one is
        // easy -- it is the control for the two that follow.
        let zurich = calendar("Europe/Zurich")
        let evening = zurich.date(from: DateComponents(year: 2026, month: 9, day: 13, hour: 21))!

        let sent = utcDay(of: WatchGuruClient.asDayInUTC(evening, calendar: zurich))

        #expect(sent.year == 2026)
        #expect(sent.month == 9)
        #expect(sent.day == 13)
    }

    @Test("a morning in Auckland is not sent as the day before")
    func morningInAuckland() {
        // 09:00 on the 14th in Auckland is 21:00 on the 13th in UTC. Sending
        // the moment itself would bound the range on the 13th and hide
        // everything the user watched on the day they are looking at.
        let auckland = calendar("Pacific/Auckland")
        let morning = auckland.date(from: DateComponents(year: 2026, month: 9, day: 14, hour: 9))!

        let sent = utcDay(of: WatchGuruClient.asDayInUTC(morning, calendar: auckland))

        #expect(sent.day == 14)
        #expect(sent.month == 9)
    }

    @Test("a late evening in Los Angeles is not sent as the day after")
    func eveningInLosAngeles() {
        // 23:00 on the 13th in Los Angeles is already the 14th in UTC -- the
        // same bug in the other direction.
        let la = calendar("America/Los_Angeles")
        let night = la.date(from: DateComponents(year: 2026, month: 9, day: 13, hour: 23))!

        let sent = utcDay(of: WatchGuruClient.asDayInUTC(night, calendar: la))

        #expect(sent.day == 13)
        #expect(sent.month == 9)
    }
}
