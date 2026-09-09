import SwiftUI
import WatchGuruAPI

/// A season, collapsed to a header until opened.
///
/// Long-running series have twenty seasons; rendering every episode of every
/// one at once is both slow and unreadable.
struct SeasonSection: View {
    let season: SeasonResponse
    let isExpanded: Bool
    let isMarking: Bool
    let toggle: () -> Void
    let markWatched: (EpisodeResponse) -> Void
    let markUpTo: (EpisodeResponse) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: toggle) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.headline)
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundStyle(.secondary)
                }
            }
            .buttonStyle(.plain)
            .padding(.vertical, 10)
            .accessibilityLabel(isExpanded ? "Collapse \(title)" : "Expand \(title)")

            if isExpanded {
                ForEach(season.episodes, id: \.id) { episode in
                    EpisodeRow(episode: episode, isMarking: isMarking) {
                        markWatched(episode)
                    } markUpTo: {
                        markUpTo(episode)
                    }
                }
            }

            Divider()
        }
    }

    private var title: String {
        if season.seasonNumber == 0 { return "Specials" }
        return season.name ?? "Season \(season.seasonNumber)"
    }

    private var subtitle: String {
        let counts = "\(season.watchedEpisodes) of \(season.airedEpisodes) watched"
        // Specials are shown but excluded from series progress; saying so keeps
        // the numbers from looking wrong.
        return season.seasonNumber == 0 ? counts + " · not counted towards progress" : counts
    }
}

/// One episode.
///
/// The watched control is the highest-traffic thing in the app, so it gets a
/// 44pt target and an accessibility label naming the episode rather than
/// announcing "button".
private struct EpisodeRow: View {
    let episode: EpisodeResponse
    let isMarking: Bool
    let markWatched: () -> Void
    let markUpTo: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: markWatched) {
                Image(systemName: episode.watched ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(episode.watched ? Color.accentColor : Color.secondary)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            // An unaired episode cannot be watched, so the control is disabled
            // rather than offering an action the server would reject.
            .disabled(!episode.aired || episode.watched || isMarking)
            .accessibilityLabel(
                episode.watched ? "\(episode.code) watched" : "Mark \(episode.code) watched"
            )

            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.subheadline)
                    .lineLimit(1)
                    .foregroundStyle(episode.aired ? .primary : .secondary)

                if !episode.aired {
                    Text(episode.airDate.map { "Airs \($0.formatted(.dateTime.day().month().year()))" }
                         ?? "Not yet aired")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                } else if episode.watchCount > 1 {
                    Text("Watched \(episode.watchCount) times")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()
        }
        .contentShape(Rectangle())
        // Swipe rather than an overflow button: it is the gesture iOS users
        // already have, and it keeps the row uncluttered.
        .swipeActions(edge: .leading, allowsFullSwipe: false) {
            if episode.aired {
                Button(action: markUpTo) {
                    Label("Mark all up to here", systemImage: "checkmark.circle.badge.checkmark")
                }
                .tint(.accentColor)
            }
        }
    }

    private var label: String {
        if let name = episode.name { return "\(episode.code) · \(name)" }
        return episode.code
    }
}
