import SwiftUI
import WatchGuruAPI

struct TitleDetailView: View {

    let titleId: Int64

    @Environment(Session.self) private var session
    @State private var model: TitleDetailModel?

    var body: some View {
        Group {
            if let model {
                switch model.state {
                case .loading, .refreshing:
                    ProgressView()
                case .failed(let failure):
                    FailureView(failure: failure) { Task { await model.load() } }
                case .content(let title):
                    detail(title: title, model: model)
                case .empty:
                    EmptyView()
                }
            } else {
                ProgressView()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil {
                let created = TitleDetailModel(client: session.client, offline: session.offline, titleId: titleId)
                model = created
                await created.load()
            }
        }
        // The offers on this screen are for one country. Changing it in
        // Profile has to reach the screens already loaded, not only the next
        // one opened.
        .onChange(of: session.profileRevision) {
            Task { await model?.load() }
        }
    }

    private func detail(title: TitleResponse, model: TitleDetailModel) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                HStack(alignment: .top, spacing: 16) {
                    PosterView(
                        url: title.posterUrl.flatMap(URL.init(string:)),
                        title: title.primaryTitle
                    )
                    .frame(width: 120)

                    VStack(alignment: .leading, spacing: 6) {
                        Text(title.primaryTitle).font(.title2.bold())

                        if let date = title.releaseDate {
                            Text(date.formatted(.dateTime.year()))
                                .foregroundStyle(.secondary)
                        }
                        if let runtime = title.runtimeMinutes {
                            Text("\(runtime) min").foregroundStyle(.secondary)
                        }

                        // Two rating sources, always labelled so they cannot be
                        // confused, and each omitted entirely when absent
                        // rather than rendered as 0.0.
                        if let tmdb = title.providerRating {
                            Text("TMDB \(tmdb.formatted(.number.precision(.fractionLength(1))))")
                                .font(.subheadline)
                        }
                        if let imdb = title.imdbRating {
                            Text("IMDb \(imdb.formatted(.number.precision(.fractionLength(1))))")
                                .font(.subheadline)
                        }
                    }
                }

                if let progress = model.progress, progress.airedEpisodes > 0 {
                    EpisodeProgressView(progress: progress, isMarking: model.isMarking) {
                        Task { await model.markNextEpisodeWatched() }
                    }
                }

                if let overview = title.overview {
                    Text(overview).font(.body)
                }

                // Above the episode list: for a film this is the only action
                // the screen can offer, and for a series it is what someone
                // does before they start rather than after.
                WhereToWatchView(offers: title.availability, checked: title.availabilityChecked)

                // The episode list. For a series this is the screen's real
                // content; everything above it is context.
                if let seasons = model.seasons, !seasons.seasons.isEmpty {
                    Text("Episodes").font(.headline)
                    ForEach(seasons.seasons, id: \.seasonNumber) { season in
                        SeasonSection(
                            season: season,
                            isExpanded: model.expandedSeason == season.seasonNumber,
                            isMarking: model.isMarking,
                            toggle: {
                                model.expandedSeason =
                                    model.expandedSeason == season.seasonNumber ? nil : season.seasonNumber
                            },
                            markWatched: { episode in
                                Task { await model.toggleEpisode(episode) }
                            },
                            markUpTo: { episode in
                                Task { await model.markUpTo(episode) }
                            }
                        )
                    }
                }

                // Required by TMDB's terms of use wherever their data is shown.
                Text("This product uses the TMDB API but is not endorsed or certified by TMDB.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .padding()
        }
        .navigationTitle(title.primaryTitle)
    }
}

/// Where the user is, and the single action that moves them forward.
///
/// The whole product is judged on this button, so it is full width and
/// reachable without scrolling: people press it repeatedly, on a sofa,
/// one-handed.
private struct EpisodeProgressView: View {
    let progress: TitleProgress
    let isMarking: Bool
    let markNext: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(progress.watchedEpisodes) of \(progress.airedEpisodes) episodes")
                .font(.headline)
                .monospacedDigit()

            ProgressView(value: Double(progress.percentComplete), total: 100)
                .accessibilityLabel(
                    "\(progress.watchedEpisodes) of \(progress.airedEpisodes) episodes watched"
                )

            if let code = progress.nextEpisodeCode {
                if let name = progress.nextEpisodeName {
                    Text("Next: \(code) · \(name)").font(.subheadline)
                } else {
                    Text("Next: \(code)").font(.subheadline)
                }

                Button(action: markNext) {
                    Label("Mark watched", systemImage: "play.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(isMarking)
                // Names the episode, so VoiceOver says which one is about to be
                // marked rather than just "button".
                .accessibilityLabel("Mark \(code) watched")
                .sensoryFeedback(.success, trigger: progress.watchedEpisodes)
            } else {
                // Caught up is a real state and deserves saying, not a gap.
                Text("You're all caught up.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
