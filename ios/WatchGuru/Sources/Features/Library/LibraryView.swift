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
                let created = LibraryModel(client: session.client)
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
                        Text("All").tag(WatchlistControllerAPI.StatusListWatchlist?.none)
                        ForEach(WatchlistControllerAPI.StatusListWatchlist.allCases, id: \.self) { status in
                            Text(label(for: status))
                                .tag(WatchlistControllerAPI.StatusListWatchlist?.some(status))
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

    private func label(for status: WatchlistControllerAPI.StatusListWatchlist) -> String {
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

                // No progress bar here yet, deliberately. GET /api/v1/me/watchlist
                // does not return per-title progress, and the only way to get it
                // today is one /progress call per row — an N+1 for a screen that
                // scrolls. A bar hardcoded to zero would read as "you have
                // watched nothing" for a series someone is halfway through. The
                // fix belongs on the server; see issue #13.
                if let episodes = item.title.numberOfEpisodes, episodes > 0 {
                    Text("\(episodes) episodes")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}
