import SwiftUI
import WatchGuruAPI

struct SearchView: View {

    @Environment(Session.self) private var session
    @State private var model: SearchModel?

    var body: some View {
        Group {
            if let model {
                content(model)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Search")
        .task {
            if model == nil {
                model = SearchModel(client: session.client, offline: session.offline)
            }
        }
    }

    @ViewBuilder
    private func content(_ model: SearchModel) -> some View {
        @Bindable var model = model

        Group {
            if let hits = model.state.value {
                List(hits, id: \.providerId) { hit in
                    NavigationLink(value: hit.providerId) {
                        SearchResultRow(
                            hit: hit,
                            alreadyAdded: model.added.contains(hit.providerId)
                        )
                    }
                    // A swipe to add, because reaching a button on a row is
                    // slower than the gesture iOS users already have.
                    .swipeActions(edge: .trailing) {
                        Button {
                            Task { await model.addToLibrary(hit) }
                        } label: {
                            Label("Add", systemImage: "plus")
                        }
                        .tint(.accentColor)
                    }
                }
                .listStyle(.plain)
            } else {
                switch model.state {
                case .loading:
                    ProgressView()
                case .failed(let failure):
                    FailureView(failure: failure) { model.retry() }
                case .empty where model.query.isEmpty:
                    ContentUnavailableView(
                        "Search for something to watch",
                        systemImage: "magnifyingglass"
                    )
                case .empty:
                    ContentUnavailableView.search(text: model.query)
                default:
                    EmptyView()
                }
            }
        }
        .searchable(text: $model.query, prompt: "Search films and series")
        .navigationDestination(for: Int64.self) { providerId in
            TitleDetailView(titleId: providerId)
        }
    }
}

private struct SearchResultRow: View {
    let hit: SearchHit
    let alreadyAdded: Bool

    var body: some View {
        HStack(spacing: 12) {
            PosterView(url: hit.posterUrl.flatMap(URL.init(string:)), title: hit.title)
                .frame(width: 48)

            VStack(alignment: .leading, spacing: 2) {
                Text(hit.title)
                    .font(.headline)
                    .lineLimit(2)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if alreadyAdded {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.secondary)
                    .accessibilityLabel("Already in your library")
            }
        }
    }

    private var subtitle: String {
        var parts: [String] = [hit.titleType == .movie ? "Film" : "Series"]
        if let date = hit.releaseDate {
            parts.append(date.formatted(.dateTime.year()))
        }
        // A null rating is omitted rather than shown as 0.0: most titles
        // genuinely have no rating yet.
        if let rating = hit.providerRating {
            parts.append(rating.formatted(.number.precision(.fractionLength(1))))
        }
        return parts.joined(separator: " · ")
    }
}
