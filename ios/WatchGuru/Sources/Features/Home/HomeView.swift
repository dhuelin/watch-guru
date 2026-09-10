import SwiftUI
import WatchGuruAPI

/// Up Next: what to put on now.
///
/// One row per series in progress, most recently watched first, each with the
/// episode to play and the action that records it.
struct HomeView: View {

    @Environment(Session.self) private var session
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
            }
            .listStyle(.plain)
            .refreshable { await model.load() }
            .navigationDestination(for: Int64.self) { TitleDetailView(titleId: $0) }
        } else {
            switch model.state {
            case .loading:
                ProgressView()
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

    var body: some View {
        HStack(spacing: 12) {
            PosterView(url: entry.posterUrl.flatMap(URL.init(string:)), title: entry.primaryTitle)
                .frame(width: 56)

            VStack(alignment: .leading, spacing: 4) {
                Text(entry.primaryTitle).font(.headline).lineLimit(1)

                Text(episodeLabel)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

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
                    Label("Mark watched", systemImage: "play.circle.fill")
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
