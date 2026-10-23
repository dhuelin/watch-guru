import SwiftUI
import WatchGuruAPI

/// A season, collapsed to a header until opened.
///
/// Long-running series have twenty seasons; rendering every episode of every
/// one at once is both slow and unreadable.
///
/// Meant for a List: the header, each episode and the closing divider are
/// separate rows, so each episode can have swipe actions of its own.
struct SeasonSection: View {
    let season: SeasonResponse
    let isExpanded: Bool
    let isMarking: Bool
    let toggle: () -> Void
    let markWatched: (EpisodeResponse) -> Void
    let markUpTo: (EpisodeResponse) -> Void

    var body: some View {
        // A Section, so the List lays out the header, each episode and the
        // divider as rows of their own.
        Section {
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
            .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))

            if isExpanded {
                ForEach(season.episodes, id: \.id) { episode in
                    EpisodeRow(episode: episode, isMarking: isMarking) {
                        markWatched(episode)
                    } markUpTo: {
                        markUpTo(episode)
                    }
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                }
            }

            // In a VStack: a List row lays out horizontally, and a bare
            // Divider there is drawn as a vertical rule.
            VStack { Divider() }
                .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
        }
    }

    private var title: String {
        if season.seasonNumber == 0 { return String(localized: "Specials") }
        // The provider's own season name where it has one; it arrives in
        // whatever language the catalogue was requested in.
        return season.name ?? String(localized: "Season \(season.seasonNumber)")
    }

    private var subtitle: String {
        let counts = String(
            localized: "\(season.watchedEpisodes) of \(season.airedEpisodes) watched")
        // Specials are shown but excluded from series progress; saying so keeps
        // the numbers from looking wrong.
        //
        // One key for the whole line rather than a suffix concatenated on: a
        // translation needs to be able to put the clause where its language
        // wants it, and half a sentence cannot be moved.
        return season.seasonNumber == 0
            ? String(localized: "\(counts) · not counted towards progress")
            : counts
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
            // rather than offering an action the server would reject. A watched
            // one now toggles back, which is the common correction.
            .disabled(!episode.aired || isMarking)
            .accessibilityLabel(
                episode.watched
                    ? "Mark \(episode.code) unwatched"
                    : "Mark \(episode.code) watched"
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
        // already have, and it keeps the row uncluttered. A full swipe runs
        // the first action, the same toggle as the circle, so a run of
        // episodes can be ticked off without aiming at each one; a partial
        // swipe also offers marking everything up to here.
        //
        // From the trailing edge, not the leading one where Up Next has it:
        // this screen is pushed, and since iOS 26 a rightward swipe anywhere
        // on a pushed screen goes back, before a row's actions ever see it.
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            if episode.aired && !isMarking {
                Button(action: markWatched) {
                    if episode.watched {
                        Label("Mark unwatched", systemImage: "circle")
                    } else {
                        Label("Mark watched", systemImage: "checkmark.circle")
                    }
                }
                .tint(episode.watched ? .gray : .accentColor)

                Button(action: markUpTo) {
                    Label("Mark all up to here", systemImage: "checkmark.circle.badge.checkmark")
                }
                .tint(.indigo)
            }
        }
        .sensoryFeedback(.success, trigger: episode.watched) { _, watched in watched }
    }

    private var label: String {
        if let name = episode.name { return "\(episode.code) · \(name)" }
        return episode.code
    }
}
