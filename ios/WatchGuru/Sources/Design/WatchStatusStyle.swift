import SwiftUI
import WatchGuruAPI

/// How each watch status is drawn.
///
/// One place, because the status appears on every library row, every search
/// result already in the library, and every detail screen. Colour is never the
/// only signal — each status carries an SF Symbol and a label too, for
/// colour-blind users and because a coloured dot with no legend is a puzzle.
struct WatchStatusStyle {
    let color: Color
    let symbol: String
    let label: LocalizedStringKey
}

extension WatchlistItemResponse.Status {

    /// `WATCHING` is the only status wearing the accent colour. A library where
    /// five statuses compete for attention answers nothing; the question the
    /// screen exists to answer is "what am I in the middle of".
    var style: WatchStatusStyle {
        switch self {
        case .watchlist:
            WatchStatusStyle(color: .secondary, symbol: "bookmark", label: "Watchlist")
        case .watching:
            WatchStatusStyle(color: .accentColor, symbol: "play.circle.fill", label: "Watching")
        case .completed:
            WatchStatusStyle(color: .green, symbol: "checkmark.circle.fill", label: "Completed")
        case .onHold:
            WatchStatusStyle(color: .orange, symbol: "pause.circle", label: "On hold")
        case .dropped:
            WatchStatusStyle(color: .secondary, symbol: "xmark.circle", label: "Dropped")
        }
    }
}
