import WidgetKit
import SwiftUI

/// What the home screen shows, and when it is asked again.
///
/// The provider reads a file and nothing else. No request, no token, no
/// async work: the app writes the feed whenever it fetches Up Next and calls
/// `reloadAllTimelines`, so the widget is current within a moment of any change
/// made in the app — which is what #20 asks for, and it costs no network at all.
///
/// A widget that fetched for itself would burn the system's refresh budget on
/// requests duplicating ones the app had just made, and would need the session
/// refresh the app already owns.
struct UpNextProvider: TimelineProvider {

    func placeholder(in context: Context) -> UpNextEntry {
        UpNextEntry(date: .now, feed: .placeholder, signedIn: true)
    }

    func getSnapshot(in context: Context, completion: @escaping (UpNextEntry) -> Void) {
        completion(current())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<UpNextEntry>) -> Void) {
        // One entry, refreshed in a few hours. The app drives the interesting
        // reloads; this exists so the "as of" line cannot claim a feed is fresh
        // for ever when nobody has opened the app in days.
        completion(
            Timeline(
                entries: [current()],
                policy: .after(.now.addingTimeInterval(4 * 60 * 60))
            )
        )
    }

    private func current() -> UpNextEntry {
        UpNextEntry(
            date: .now,
            feed: WidgetFeedStore().read(),
            // Absence of a session, not a rejected request: the widget makes no
            // request to be rejected. #20 asks for a prompt when signed out
            // rather than an error, and this is how it tells the difference.
            signedIn: KeychainTokenStore().tokens() != nil
        )
    }
}

struct UpNextEntry: TimelineEntry {
    let date: Date
    let feed: WidgetFeed?
    let signedIn: Bool

    /// What the first row is, if any.
    var first: WidgetFeed.Episode? { feed?.entries.first }
}

extension WidgetFeed {
    /// For the placeholder and previews. Never shown as real data.
    static var placeholder: WidgetFeed {
        WidgetFeed(
            entries: [
                Episode(
                    titleId: 1, episodeId: 1, title: "Severance",
                    episodeCode: "S2E4", episodeName: "Woe's Hollow",
                    watchedEpisodes: 13, airedEpisodes: 19
                ),
                Episode(
                    titleId: 2, episodeId: 2, title: "Andor",
                    episodeCode: "S1E9", episodeName: "Nobody's Listening!",
                    watchedEpisodes: 8, airedEpisodes: 12
                ),
            ],
            storedAt: .now
        )
    }
}

/// The home-screen and lock-screen widget.
///
/// One `Widget` covering every family rather than one per size: they draw the
/// same thing at different densities, and splitting them would mean four
/// configurations for the user to choose between that all say "Up Next".
struct UpNextWidget: Widget {

    private let kind = "UpNextWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: UpNextProvider()) { entry in
            UpNextWidgetView(entry: entry)
                // The modifier, not a coloured Rectangle: StandBy and the lock
                // screen remove the background entirely, and a hard-coded one
                // would leave a floating slab on the nightstand.
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("Up Next")
        .description("The next episode of what you are watching, markable from here.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .systemLarge,
            // Lock screen, and the same view StandBy uses on a charging phone.
            .accessoryRectangular,
        ])
    }
}
