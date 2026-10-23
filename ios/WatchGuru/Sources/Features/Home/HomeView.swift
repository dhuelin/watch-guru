import SwiftUI
import WatchGuruAPI

/// Up Next: what to put on now.
///
/// One row per series in progress, most recently watched first, each with the
/// episode to play and the action that records it.
struct HomeView: View {

    @Environment(Session.self) private var session
    @Environment(\.scenePhase) private var scenePhase
    @State private var model: HomeModel?

    var body: some View {
        Group {
            if let model {
                content(model)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Up Next")
        .task {
            if model == nil {
                let created = HomeModel(client: session.client, offline: session.offline)
                model = created
                await created.load()
            }
        }
        // Up Next goes stale while the app is in the background: an episode
        // marked on another device, or from the widget, is still offered here,
        // and marking it again records a rewatch nobody meant. Coming back to
        // the foreground is when that row is most likely to be wrong and about
        // to be tapped. onChange rather than .task(id:), so the first
        // appearance is not loaded twice.
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active, let model else { return }
            Task { await model.load() }
        }
    }

    @ViewBuilder
    private func content(_ model: HomeModel) -> some View {
        if let entries = model.state.value {
            List(entries, id: \.titleId) { entry in
                NavigationLink(value: entry.titleId) {
                    UpNextRow(
                        entry: entry,
                        busy: model.marking.contains(entry.titleId)
                    ) {
                        Task { await model.markWatched(entry) }
                    }
                }
                // The same call as the row's button, through the offline
                // queue; the button stays, since a swipe is never found by
                // someone who was not shown it.
                .swipeActions(edge: .leading, allowsFullSwipe: true) {
                    if !model.marking.contains(entry.titleId) {
                        Button {
                            Task { await model.markWatched(entry) }
                        } label: {
                            Label("Mark watched", systemImage: "checkmark.circle")
                        }
                        .tint(.accentColor)
                    }
                }
            }
            .listStyle(.plain)
            .refreshable { await model.load() }
            .navigationDestination(for: Int64.self) { TitleDetailView(titleId: $0) }
        } else {
            switch model.state {
            case .loading:
                SkeletonList()
            case .failed(let failure):
                FailureView(failure: failure) { Task { await model.load() } }
            default:
                ContentUnavailableView(
                    "Nothing on the go",
                    systemImage: "play.tv",
                    description: Text("Start a series and it will show up here.")
                )
            }
        }
    }
}

private struct UpNextRow: View {
    let entry: UpNextResponse
    let busy: Bool
    let markWatched: () -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        // At accessibility sizes the poster goes above the text rather than
        // beside it, and the lines may wrap: beside it, a title was cut to
        // "The Clockm…" with half the row taken by artwork.
        let large = typeSize.isAccessibilitySize
        let layout = large
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(spacing: 12))

        layout {
            PosterView(url: entry.posterUrl.flatMap(URL.init(string:)), title: entry.primaryTitle)
                .frame(width: large ? 96 : 56)

            VStack(alignment: .leading, spacing: 4) {
                Text(entry.primaryTitle).font(.headline).lineLimit(large ? 3 : 1)

                Text(episodeLabel)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(large ? 3 : 1)

                if entry.airedEpisodes > 0 {
                    ProgressView(
                        value: Double(entry.watchedEpisodes),
                        total: Double(entry.airedEpisodes)
                    )
                    .accessibilityLabel(
                        "\(entry.watchedEpisodes) of \(entry.airedEpisodes) episodes watched"
                    )
                }

                Button(action: markWatched) {
                    // A check, as on the title screen: this records the
                    // episode, and play is the WATCHING status's symbol.
                    Label("Mark watched", systemImage: "checkmark.circle")
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .disabled(busy)
                // Names the episode and the series, so VoiceOver says which one
                // is about to be marked rather than just "button".
                .accessibilityLabel("Mark \(entry.nextEpisodeCode) of \(entry.primaryTitle) watched")
                .sensoryFeedback(.success, trigger: entry.watchedEpisodes)
            }
        }
        .padding(.vertical, 4)
    }

    private var episodeLabel: String {
        if let name = entry.nextEpisodeName {
            "\(entry.nextEpisodeCode) · \(name)"
        } else {
            entry.nextEpisodeCode
        }
    }
}
