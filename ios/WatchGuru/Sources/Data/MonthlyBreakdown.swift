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
            return String(localized: "No viewing recorded in this period.")
        }
        // One key per month rather than per word: "Oktober, 120 Minuten" is a
        // sentence, and a language that puts the unit elsewhere needs to be
        // able to move it.
        let parts = months.map {
            String(localized: "\(label($0, locale: locale)), \($0.minutes) minutes")
        }
        return String(localized: "Minutes watched by month: ")
            + parts.joined(separator: ", ")
    }

    /// The months from the first to the last, with the empty ones put back.
    ///
    /// The server reports only months that have viewing in them, so a quiet
    /// July simply was not there, and the axis ran Jun, Aug as though they were
    /// neighbours. A month with nothing in it is still a month: time on this
    /// axis has to be continuous, or the gaps that matter most are the ones
    /// the chart hides.
    static func filled(_ months: [MonthBucket]) -> [MonthBucket] {
        let sorted = months.sorted { ($0.year, $0.month) < ($1.year, $1.month) }
        guard let first = sorted.first, let last = sorted.last else { return [] }

        var byMonth: [Int: MonthBucket] = [:]
        for bucket in sorted { byMonth[bucket.year * 12 + bucket.month - 1] = bucket }

        let start = first.year * 12 + first.month - 1
        let end = last.year * 12 + last.month - 1
        return (start...end).map { index in
            byMonth[index] ?? MonthBucket(minutes: 0, month: index % 12 + 1, viewings: 0, year: index / 12)
        }
    }

    /// Hours, for the chart's bars.
    ///
    /// The headline and the busiest month are both in hours, so an axis in
    /// minutes read as a different quantity: "9 h" above a bar that reached
    /// 540. Fractional, so a month of forty minutes is a short bar rather than
    /// no bar.
    static func hours(_ bucket: MonthBucket) -> Double {
        Double(bucket.minutes) / 60
    }

    /// The month with the most watched, or nil when nothing was.
    ///
    /// Nil rather than the first of several zeroes: announcing a "busiest
    /// month" of no hours is a sentence about nothing.
    static func busiest(_ months: [MonthBucket]) -> MonthBucket? {
        months.filter { $0.minutes > 0 }.max { $0.minutes < $1.minutes }
    }
}
