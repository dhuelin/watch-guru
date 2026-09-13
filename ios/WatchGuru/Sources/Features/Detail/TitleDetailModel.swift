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
    /// What happened to the last film viewing logged here, if any.
    private(set) var filmLog: FilmLog?

    private let client: WatchGuruClient
    private let offline: OfflineClient
    private let titleId: Int64

    init(client: WatchGuruClient, offline: OfflineClient, titleId: Int64) {
        self.client = client
        self.offline = offline
        self.titleId = titleId
    }

    func load() async {
        do {
            // Hoisted rather than inlined into the case. Inline, the region
            // based isolation checker gives up with "pattern that the ...
            // checker does not understand how to check. Please file a bug" --
            // a compiler limitation rather than a fault here, and a local is
            // the standard way around it. HomeModel and LibraryModel already
            // did this, which is why they compiled.
            let title = try await client.title(titleId)
            state = .content(title)
        } catch {
            state = .failed(error)
        }
        await refreshProgress()
        await refreshSeasons()
    }

    /// Toggles one episode's watched state.
    ///
    /// Unmarking matters more than it sounds: ticking the row below the one you
    /// meant is the most common mistake in a list of near-identical episodes.
    func toggleEpisode(_ episode: EpisodeResponse) async {
        guard episode.aired, !isMarking else { return }

        isMarking = true
        defer { isMarking = false }

        let succeeded: Bool
        if episode.watched {
            succeeded = await offline.unmarkEpisode(episodeId: episode.id) == .sent
        } else {
            succeeded = await offline.markEpisodeWatched(episodeId: episode.id, titleId: titleId) == .sent
        }

        if succeeded {
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

        if await offline.markWatchedUpTo(episodeId: episode.id) == .sent {
            await refreshProgress()
            await refreshSeasons()
        }
    }

    /// Records a film as watched, on the day the user names.
    ///
    /// The only way a film reaches the history: there is no episode to tick.
    /// A date rather than an instant, with the current time of day attached, so
    /// "I saw this last Tuesday" is expressible without asking anyone what time
    /// it was. Today is a date like any other here.
    ///
    /// The outcome is reported as it happened — sent, or queued for later —
    /// because on a train the honest answer is the second one, and a screen
    /// that says "watched" either way is lying about where the record is.
    func logFilmWatched(on day: Date) async {
        guard !isMarking else { return }

        isMarking = true
        defer { isMarking = false }

        let watchedAt = Self.atTimeOfDay(on: day)
        switch await offline.logFilmWatched(titleId: titleId, watchedAt: watchedAt) {
        case .sent: filmLog = .logged(day)
        case .queued: filmLog = .queued(day)
        case .failed(let failure): filmLog = .failed(failure)
        }
    }

    /// Dismisses the confirmation, so it does not outlive the moment.
    func clearFilmLog() {
        filmLog = nil
    }

    /// The chosen day, at the current time of day.
    ///
    /// Not midnight: a viewing filed at 00:00 sits at the top of its day on a
    /// screen that orders by time, above things genuinely watched that morning.
    private static func atTimeOfDay(on day: Date, now: Date = .now, calendar: Calendar = .current) -> Date {
        let time = calendar.dateComponents([.hour, .minute, .second], from: now)
        return calendar.date(
            bySettingHour: time.hour ?? 12,
            minute: time.minute ?? 0,
            second: time.second ?? 0,
            of: day) ?? day
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

        if await offline.markEpisodeWatched(episodeId: next, titleId: titleId) == .sent {
            await refreshProgress()
        }
    }

    private func refreshProgress() async {
        // A film has no episode progress, and the endpoint says so with a 404
        // rather than an error worth showing anyone.
        progress = try? await client.progress(titleId: titleId)
    }
}

extension TitleDetailModel {

    /// The outcome of logging a film, as it actually went.
    enum FilmLog: Equatable {
        /// The server has it.
        case logged(Date)
        /// Stored locally; it will be sent when there is a network.
        case queued(Date)
        /// The server refused it, and the user needs to know.
        case failed(APIFailure)
    }
}
