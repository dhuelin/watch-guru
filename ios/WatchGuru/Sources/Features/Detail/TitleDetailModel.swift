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
    /// Nil for films, which have no seasons.
    private(set) var seasons: SeasonsResponse?
    /// Which season is open. Defaults to the one holding the next episode.
    var expandedSeason: Int?

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
        await refreshSeasons()
    }

    /// Marks one episode watched.
    ///
    /// Unmarking has no endpoint yet, so only this direction acts; the view
    /// disables the control rather than showing one that silently does nothing.
    func markEpisodeWatched(_ episode: EpisodeResponse) async {
        guard episode.aired, !episode.watched, !isMarking else { return }

        isMarking = true
        defer { isMarking = false }

        if (try? await client.markEpisodeWatched(episodeId: episode.id)) != nil {
            await refreshProgress()
            await refreshSeasons()
        }
    }

    /// Marks everything up to and including one episode.
    ///
    /// What people reach for when logging a series they finished years ago.
    /// One request, idempotent on the server, so a mis-tap costs nothing.
    func markUpTo(_ episode: EpisodeResponse) async {
        guard episode.aired, !isMarking else { return }

        isMarking = true
        defer { isMarking = false }

        if (try? await client.markWatchedUpTo(episodeId: episode.id)) != nil {
            await refreshProgress()
            await refreshSeasons()
        }
    }

    private func refreshSeasons() async {
        seasons = try? await client.seasons(titleId: titleId)

        if expandedSeason == nil, let seasons {
            // Open the season holding the next episode, so the thing the user
            // came to do is already on screen.
            let next = progress?.nextEpisodeId
            expandedSeason = seasons.seasons
                .first { season in season.episodes.contains { $0.id == next } }?.seasonNumber
                ?? seasons.seasons.first { $0.seasonNumber > 0 }?.seasonNumber
        }
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
