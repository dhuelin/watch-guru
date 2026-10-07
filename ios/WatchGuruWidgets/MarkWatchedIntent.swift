import AppIntents
import WidgetKit

/// The widget's button.
///
/// `openAppWhenRun = false` is the whole feature: with it true this would be a
/// shortcut to the app, and #20 asks for marking *without* launching anything.
/// The work happens in the widget's own process, in `perform()`.
///
/// `Int` rather than `Int64` for the parameters because that is what App Intents
/// carries; the ids are narrowed here and widened again for the request, which
/// is safe for anything a database will produce this decade.
struct MarkWatchedIntent: AppIntent {

    // `let`, not `var`: these satisfy get-only protocol requirements, and under
    // Swift 6's complete concurrency checking a mutable static is non-isolated
    // shared state and an error rather than a warning.
    static let title: LocalizedStringResource = "Mark watched"
    static let description = IntentDescription("Marks the next episode of a series as watched.")
    static let openAppWhenRun = false

    @Parameter(title: "Episode") var episodeId: Int
    @Parameter(title: "Series") var titleId: Int

    init() {}

    init(episodeId: Int64, titleId: Int64) {
        self.episodeId = Int(episodeId)
        self.titleId = Int(titleId)
    }

    func perform() async throws -> some IntentResult {
        _ = await WidgetMark.markEpisodeWatched(
            episodeId: Int64(episodeId),
            titleId: Int64(titleId)
        )
        // Redrawn whatever happened, including a refusal: on a refusal the feed
        // is unchanged and the same episode is drawn again, which is the honest
        // answer.
        await MainActor.run { WidgetCenter.shared.reloadAllTimelines() }
        return .result()
    }
}
