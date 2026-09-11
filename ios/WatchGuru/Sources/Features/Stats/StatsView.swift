import Charts
import SwiftUI
import WatchGuruAPI

/// What a person has watched, as figures rather than a list.
///
/// Everything here is computed server-side over the append-only event log, so
/// this screen renders one answer rather than doing arithmetic of its own —
/// which is what keeps the hero figure and the breakdowns from disagreeing.
struct StatsView: View {

    @Environment(Session.self) private var session
    @State private var model: StatsModel?

    var body: some View {
        Group {
            if let model {
                switch model.state {
                case .loading:
                    ProgressView()
                case .failed(let failure):
                    FailureView(failure: failure) { Task { await model.load() } }
                case .content(let stats), .refreshing(let stats):
                    content(stats: stats, model: model)
                case .empty:
                    ContentUnavailableView(
                        "Nothing watched yet",
                        systemImage: "chart.bar",
                        description: Text("Mark something watched and this fills in.")
                    )
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Statistics")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil {
                let created = StatsModel(client: session.client)
                model = created
                await created.load()
            }
        }
    }

    private func content(stats: WatchStats, model: StatsModel) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // One filter row above everything it scopes, rather than a
                // control per section: every figure below moves together.
                Picker("Period", selection: Binding(
                    get: { model.period },
                    set: { period in Task { await model.select(period) } }
                )) {
                    ForEach(StatsPeriod.allCases) { period in
                        Text(period.label).tag(period)
                    }
                }
                .pickerStyle(.segmented)

                if stats.totalMinutes == 0 {
                    // A period with nothing in it is a real answer, not an
                    // error, and must not look like a screen full of zeroes.
                    Text("Nothing watched in this period.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                    StreakTiles(stats: stats)
                } else {
                    HeroTime(stats: stats)
                    StreakTiles(stats: stats)

                    // One month is not a shape, and a single bar is a chart
                    // pretending to be one.
                    if stats.byMonth.count > 1 {
                        SectionHeading("By month")
                        MonthlyChart(months: stats.byMonth)
                    }

                    Breakdown(heading: "Most watched", buckets: stats.topTitles)
                    Breakdown(heading: "By genre", buckets: stats.byGenre)
                    Breakdown(heading: "By service", buckets: stats.byService)
                }
            }
            .padding()
        }
    }
}

/// The headline: how much of their life this is.
///
/// Hours past a day's worth, because "12,480 minutes" is a number nobody can
/// feel. No `monospacedDigit` — equal-width digits make a large standalone
/// figure look loose.
private struct HeroTime: View {
    let stats: WatchStats

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(stats.totalMinutes >= 60
                 ? "\(stats.totalMinutes / 60) h"
                 : "\(stats.totalMinutes) min")
                .font(.system(.largeTitle, design: .default).weight(.semibold))

            Text("\(stats.distinctTitles) titles · \(stats.totalMovieViewings) films · "
                 + "\(stats.totalEpisodeViewings) episodes")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

private struct StreakTiles: View {
    let stats: WatchStats

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                StatTile(value: "\(stats.currentStreakDays)", label: "Day streak")
                StatTile(value: "\(stats.longestStreakDays)", label: "Longest streak")
            }
            // The rule, stated. A streak whose arithmetic is a mystery feels
            // arbitrary, and an arbitrary streak is worse than none.
            Text("A streak counts consecutive days with at least one viewing. "
                 + "Yesterday still counts, so it does not break before the day is out.")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }
}

/// A number is the chart, where there is only one number.
private struct StatTile: View {
    let value: String
    let label: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.title2.weight(.semibold))
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(value)")
    }
}

/// Minutes watched per calendar month.
///
/// One hue for every bar, deliberately: colouring bars darker-where-bigger
/// double-encodes what the bar length already says, and months are a sequence
/// rather than categories that need telling apart. No number above every bar
/// either — the busiest is named, and the whole chart carries a spoken summary
/// so every value is reachable without seeing it.
private struct MonthlyChart: View {
    let months: [MonthBucket]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let busiest = MonthlyBreakdown.busiest(months) {
                Text("Busiest month: \(MonthlyBreakdown.label(busiest)) · \(busiest.minutes / 60) h")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Chart(months, id: \.self) { bucket in
                BarMark(
                    x: .value("Month", MonthlyBreakdown.label(bucket)),
                    y: .value("Minutes", bucket.minutes)
                )
                .cornerRadius(4)
                .foregroundStyle(Color.accentColor)
            }
            .chartYAxis {
                // Hairline grid one shade off the surface; a heavy rule would
                // outshout the data it is there to support.
                AxisMarks { _ in
                    AxisGridLine().foregroundStyle(.quaternary)
                    AxisValueLabel()
                }
            }
            .frame(height: 160)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(MonthlyBreakdown.spokenSummary(months))
        }
    }
}

/// A ranked list with a proportion bar, not another palette.
///
/// Genres and services are identities; giving each a colour would spend eight
/// hues saying what the order already says.
private struct Breakdown: View {
    let heading: String
    let buckets: [Bucket]

    var body: some View {
        if !buckets.isEmpty {
            let peak = max(buckets.map(\.minutes).max() ?? 1, 1)

            VStack(alignment: .leading, spacing: 12) {
                SectionHeading(heading)
                ForEach(buckets, id: \.label) { bucket in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(bucket.label).font(.subheadline).lineLimit(1)
                            Spacer()
                            Text("\(bucket.minutes / 60) h")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        ProgressView(value: Double(bucket.minutes), total: Double(peak))
                            .tint(Color.accentColor)
                    }
                    // The bar is a picture of the figure beside it, so it says
                    // nothing extra to VoiceOver.
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("\(bucket.label), \(bucket.minutes / 60) hours")
                }
            }
        }
    }
}

private struct SectionHeading: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        Text(text).font(.headline)
    }
}
