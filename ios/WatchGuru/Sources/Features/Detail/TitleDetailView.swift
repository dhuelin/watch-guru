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
            // The country the offers below are for, so the section can name
            // it. Read once per session rather than per title, and only if
            // Profile has not already put it there.
            if session.region == nil {
                session.region = try? await session.client.profile().region
            }
        }
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
        //
        // .task(id:) rather than .onChange: it cancels the previous load
        // before starting the next, so a request sent before the region
        // changed cannot answer last and put the old country's offers back.
        .task(id: session.profileRevision) {
            guard session.profileRevision > 0, let model else { return }
            await model.load()
        }
    }

    private func detail(title: TitleResponse, model: TitleDetailModel) -> some View {
        // A List rather than a ScrollView, though it does not look like one:
        // only a List's rows take swipe actions, and the episode rows' swipe
        // to mark watched is the fastest way through a season. Every block
        // above the episodes is one borderless row.
        List {
            TitleHeader(title: title)
            .listRowInsets(EdgeInsets())
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)

            Group {
                // Before the tracking actions, because whether this is in the
                // library at all is the first thing somebody arriving from the
                // trending shelf needs to know.
                LibraryEntryView(
                    title: title,
                    isMarking: model.isMarking,
                    add: { Task { await model.addToLibrary(title) } },
                    setStatus: { itemId, status in
                        Task { await model.setStatus(itemId: itemId, status: status) }
                    },
                    setRating: { itemId, rating in
                        Task { await model.setRating(itemId: itemId, rating: rating) }
                    },
                    remove: { itemId in Task { await model.removeFromLibrary(itemId: itemId) } }
                )
                // What a ScrollView gives a button with no style of its own. In
                // a List the default turns the whole row into the button's tap
                // target, which a row holding several controls cannot have.
                // Not set on the List itself: swipe action buttons would take
                // it too, and then never appear.
                .buttonStyle(.borderless)

                // A film is watched or it is not: there is no next episode to
                // offer, so this is the whole of its tracking, and without it a
                // film could reach the library but never the history.
                if title.titleType == .movie {
                    LogFilmWatchedView(
                        isMarking: model.isMarking,
                        outcome: model.filmLog,
                        log: { day in Task { await model.logFilmWatched(on: day) } },
                        dismissOutcome: { model.clearFilmLog() }
                    )
                    .buttonStyle(.borderless)
                }

                if let progress = model.progress, progress.airedEpisodes > 0 {
                    EpisodeProgressView(progress: progress, isMarking: model.isMarking) {
                        Task { await model.markNextEpisodeWatched() }
                    }
                }

                // The only place a series can be muted. The settings screen
                // lists what the server holds a row for, and an un-muted series
                // has no row, so it can only undo what is done here.
                //
                // Nil means no switch: a film has no next episode to be told
                // about, and a setting that could not be read would be a guess.
                if let notifying = model.notifyNewEpisodes {
                    Toggle("Notify me about new episodes", isOn: Binding(
                        get: { notifying },
                        set: { on in Task { await model.setNotifyNewEpisodes(on) } }
                    ))
                    .font(.subheadline)
                }

                if let overview = title.overview {
                    Text(overview).font(.body)
                }

                // Above the episode list: for a film this is the only action
                // the screen can offer, and for a series it is what someone
                // does before they start rather than after.
                WhereToWatchView(
                    offers: title.availability,
                    checkedAt: title.availabilityCheckedAt,
                    region: session.region,
                    mine: session.myServices.selected
                )
                .buttonStyle(.borderless)

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
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 12, leading: 16, bottom: 12, trailing: 16))
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .screenBackground()
        .environment(\.defaultMinListRowHeight, 0)
        .navigationTitle(title.primaryTitle)
    }
}

/// The backdrop across the top, the poster overlapping its faded lower edge,
/// and the title and its details beside the poster on the plain screen.
///
/// Only the poster sits on the artwork. Text on a backdrop is only as legible
/// as the image behind it allows, and with a large text size or a long title
/// it would climb into the part the fade does not cover. At accessibility
/// sizes the details go under the poster, where a column beside it would hold
/// two words a line.
private struct TitleHeader: View {
    let title: TitleResponse

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let stacked = typeSize.isAccessibilitySize
        let layout = stacked
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 12))
            : AnyLayout(HStackLayout(alignment: .top, spacing: 16))

        VStack(alignment: .leading, spacing: 0) {
            BackdropView(url: title.backdropUrl.flatMap(URL.init(string:)))
            layout {
                PosterView(
                    url: title.posterUrl.flatMap(URL.init(string:)),
                    title: title.primaryTitle
                )
                .frame(width: 120)
                // Up over the backdrop's faded edge. Negative padding rather
                // than an offset, so the space it leaves is taken back too.
                .padding(.top, -88)

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
                .padding(.top, stacked ? 0 : 12)
            }
            .padding(.horizontal, 16)
        }
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
                    // A check, not a play symbol: this records the episode, it
                    // does not play it, and play is the WATCHING status's.
                    Label("Mark watched", systemImage: "checkmark.circle")
                        .frame(maxWidth: .infinity)
                }
                .prominentAction()
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
