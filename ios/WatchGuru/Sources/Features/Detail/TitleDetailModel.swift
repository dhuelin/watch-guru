import Foundation
import Observation
import WatchGuruAPI

@Observable
@MainActor
final class TitleDetailModel {

    private(set) var state: ViewState<TitleResponse> = .loading
    /// Nil for films, and for series with no aired episodes.
    private(set) var progress: TitleProgress?
    private(set) var isMarking = false

    private let client: WatchGuruClient
    private let titleId: Int64

    init(client: WatchGuruClient, titleId: Int64) {
        self.client = client
        self.titleId = titleId
    }

    func load() async {
        do {
            state = .content(try await client.title(titleId))
        } catch {
            state = .failed(error)
        }
        await refreshProgress()
    }

    /// Records the next unwatched episode as watched.
    ///
    /// This is the interaction the product is judged on, so it has to feel
    /// certain: the button reports itself busy immediately, and progress is
    /// re-read from the server rather than guessed at — the server owns what
    /// "next" means, and the two must not disagree.
    func markNextEpisodeWatched() async {
        guard let next = progress?.nextEpisodeId, !isMarking else { return }

        isMarking = true
        defer { isMarking = false }

        if (try? await client.markEpisodeWatched(episodeId: next)) != nil {
            await refreshProgress()
        }
    }

    private func refreshProgress() async {
        // A film has no episode progress, and the endpoint says so with a 404
        // rather than an error worth showing anyone.
        progress = try? await client.progress(titleId: titleId)
    }
}
