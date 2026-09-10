import SwiftUI
import WatchGuruAPI

struct LibraryView: View {

    @Environment(Session.self) private var session
    @State private var model: LibraryModel?

    var body: some View {
        Group {
            if let model {
                content(model)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Library")
        .task {
            if model == nil {
                let created = LibraryModel(client: session.client, offline: session.offline)
                model = created
                await created.load()
            }
        }
    }

    @ViewBuilder
    private func content(_ model: LibraryModel) -> some View {
        @Bindable var model = model

        Group {
            if let items = model.state.value {
                List {
                    ForEach(items, id: \.id) { item in
                        NavigationLink(value: item.title.id) {
                            LibraryRow(item: item)
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                Task { await model.remove(item) }
                            } label: {
                                Label("Remove", systemImage: "trash")
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .refreshable { await model.load() }
            } else {
                switch model.state {
                case .loading:
                    ProgressView()
                case .failed(let failure):
                    FailureView(failure: failure) { Task { await model.load() } }
                default:
                    ContentUnavailableView(
                        "Nothing tracked yet",
                        systemImage: "books.vertical",
                        description: Text("Search for a film or series to get started.")
                    )
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("Status", selection: $model.filter) {
                        Text("All").tag(WatchlistControllerAPI.Status_listWatchlist?.none)
                        ForEach(WatchlistControllerAPI.Status_listWatchlist.allCases, id: \.self) { status in
                            Text(label(for: status))
                                .tag(WatchlistControllerAPI.Status_listWatchlist?.some(status))
                        }
                    }
                } label: {
                    Label("Filter", systemImage: "line.3.horizontal.decrease.circle")
                }
            }
        }
        .navigationDestination(for: Int64.self) { titleId in
            TitleDetailView(titleId: titleId)
        }
    }

    private func label(for status: WatchlistControllerAPI.Status_listWatchlist) -> String {
        switch status {
        case .watchlist: "Watchlist"
        case .watching: "Watching"
        case .completed: "Completed"
        case .onHold: "On hold"
        case .dropped: "Dropped"
        }
    }
}

private struct LibraryRow: View {
    let item: WatchlistItemResponse

    var body: some View {
        HStack(spacing: 12) {
            PosterView(
                url: item.title.posterUrl.flatMap(URL.init(string:)),
                title: item.title.primaryTitle
            )
            .frame(width: 48)

            VStack(alignment: .leading, spacing: 4) {
                Text(item.title.primaryTitle)
                    .font(.headline)
                    .lineLimit(2)

                // Icon and label alongside the colour: status is never conveyed
                // by colour alone.
                Label {
                    Text(item.status.style.label)
                } icon: {
                    Image(systemName: item.status.style.symbol)
                }
                .font(.caption)
                .foregroundStyle(item.status.style.color)

                // Progress now arrives with the list itself, gathered for the
                // whole page in one query, so it costs no extra request. Nil
                // for films, which have no episode progress — a bar there would
                // be meaningless rather than merely empty.
                if let progress = item.progress {
                    ProgressView(
                        value: Double(progress.watchedEpisodes),
                        total: Double(max(progress.airedEpisodes, 1))
                    )
                    .accessibilityLabel(
                        "\(progress.watchedEpisodes) of \(progress.airedEpisodes) episodes watched"
                    )
                    Text("\(progress.watchedEpisodes) / \(progress.airedEpisodes)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }
        }
    }
}
