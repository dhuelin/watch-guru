import SwiftUI
import WidgetKit

/// The widget, at whatever size the user gave it.
///
/// One view that branches on family rather than four views: the content is the
/// same three facts — series, episode, progress — and what changes is how many
/// rows fit and whether there is room for a button. Four separate views would be
/// four places to fix the same wording.
struct UpNextWidgetView: View {

    @Environment(\.widgetFamily) private var family

    let entry: UpNextEntry

    var body: some View {
        if !entry.signedIn {
            // A prompt, not an error: nothing has gone wrong, and a red
            // exclamation mark on someone's home screen for "you are signed
            // out" is a small lie.
            Message("Sign in to see what's next")
        } else if let feed = entry.feed, !feed.entries.isEmpty {
            switch family {
            case .accessoryRectangular: LockScreen(episode: feed.entries[0])
            case .systemLarge: Stack(feed: feed)
            case .systemMedium: Single(episode: feed.entries[0], markable: true, feed: feed)
            default: Single(episode: feed.entries[0], markable: false, feed: feed)
            }
        } else if entry.feed == nil {
            // No feed at all: the app has never fetched Up Next on this device.
            Message("Open Watch Guru to get started")
        } else {
            Message("Nothing waiting")
        }
    }
}

/// Small, and the lock-screen fallback for a single episode.
private struct Single: View {

    let episode: WidgetFeed.Episode
    let markable: Bool
    let feed: WidgetFeed

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Up Next")
                .font(.caption2)
                .foregroundStyle(.secondary)

            Text(episode.title)
                .font(.headline)
                .lineLimit(2)

            Text(episode.subtitle)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(markable ? 2 : 1)

            Text("\(episode.watchedEpisodes) of \(episode.airedEpisodes) episodes watched")
                .font(.caption2)
                .foregroundStyle(.tertiary)

            Spacer(minLength: 0)

            if markable {
                MarkButton(episode: episode)
            }

            StaleNote(storedAt: feed.storedAt)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Large: several series, each markable.
private struct Stack: View {

    let feed: WidgetFeed

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Up Next")
                .font(.caption)
                .foregroundStyle(.secondary)

            // Four is what fits; a fifth row would be drawn clipped, which looks
            // like a rendering fault rather than a list that continues.
            ForEach(feed.entries.prefix(4)) { episode in
                HStack(alignment: .center, spacing: 8) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(episode.title)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Text(episode.subtitle)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    MarkButton(episode: episode, compact: true)
                }
            }

            Spacer(minLength: 0)
            StaleNote(storedAt: feed.storedAt)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Lock screen and StandBy: one line of each, tinted by the system.
///
/// No button. An accessory widget is rendered in a single accent colour and sits
/// where a glance is all anyone gives it; a tap target there competes with
/// unlocking the phone.
private struct LockScreen: View {

    let episode: WidgetFeed.Episode

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(episode.title)
                .font(.headline)
                .lineLimit(1)
                .widgetAccentable()
            Text(episode.subtitle)
                .font(.caption)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The one interactive control.
///
/// `Button(intent:)` rather than a link: the intent runs in the widget's process
/// and the app never appears. That is the difference between this widget and a
/// shortcut to the app.
private struct MarkButton: View {

    let episode: WidgetFeed.Episode
    var compact = false

    var body: some View {
        Button(intent: MarkWatchedIntent(episodeId: episode.episodeId, titleId: episode.titleId)) {
            if compact {
                Image(systemName: "checkmark")
                    .font(.caption.weight(.semibold))
            } else {
                Label("Watched", systemImage: "checkmark")
                    .font(.caption.weight(.semibold))
            }
        }
        .buttonStyle(.borderedProminent)
        .accessibilityLabel("Mark \(episode.episodeCode) of \(episode.title) watched")
    }
}

/// Says so when the feed is old.
///
/// A home screen offers no other clue. Silent below a day, because "as of four
/// minutes ago" is noise on a widget that is almost always current.
private struct StaleNote: View {

    let storedAt: Date

    var body: some View {
        if Date.now.timeIntervalSince(storedAt) > 24 * 60 * 60 {
            Text("As of \(storedAt.formatted(date: .abbreviated, time: .omitted))")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
    }
}

/// Every state that is not a list of episodes.
private struct Message: View {

    private let text: LocalizedStringKey

    init(_ text: LocalizedStringKey) { self.text = text }

    var body: some View {
        VStack {
            Text(text)
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
