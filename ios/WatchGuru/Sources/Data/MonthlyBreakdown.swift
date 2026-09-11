import Foundation
import WatchGuruAPI

/// How the monthly chart is named and read aloud.
///
/// Kept out of the view because the spoken summary is the whole chart as far
/// as a VoiceOver user is concerned — it is the one part of a drawing that can
/// be tested, and the one part that would otherwise be written once and never
/// checked again.
enum MonthlyBreakdown {

    /// "Mar 2026" — short, and in the reader's own language.
    static func label(_ bucket: MonthBucket, locale: Locale = .current) -> String {
        var components = DateComponents()
        components.year = bucket.year
        components.month = bucket.month
        guard let date = Calendar(identifier: .gregorian).date(from: components) else {
            return "\(bucket.month)/\(bucket.year)"
        }
        return date.formatted(.dateTime.month(.abbreviated).year().locale(locale))
    }

    /// Every value, in order, for a reader who cannot see the bars.
    ///
    /// A chart owes its readers a table; on a phone this sentence is it.
    static func spokenSummary(_ months: [MonthBucket], locale: Locale = .current) -> String {
        guard !months.isEmpty else {
            return "No viewing recorded in this period."
        }
        let parts = months.map { "\(label($0, locale: locale)), \($0.minutes) minutes" }
        return "Minutes watched by month: " + parts.joined(separator: ", ")
    }

    /// The month with the most watched, or nil when nothing was.
    ///
    /// Nil rather than the first of several zeroes: announcing a "busiest
    /// month" of no hours is a sentence about nothing.
    static func busiest(_ months: [MonthBucket]) -> MonthBucket? {
        months.filter { $0.minutes > 0 }.max { $0.minutes < $1.minutes }
    }
}
