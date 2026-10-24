import SwiftUI
import WatchGuruAPI

struct SearchView: View {

    @Environment(Session.self) private var session
    @Environment(\.horizontalSizeClass) private var sizeClass
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
                let fresh = SearchModel(client: session.client, offline: session.offline)
                model = fresh
                await fresh.loadTrending()
            }
        }
    }

    @ViewBuilder
    private func content(_ model: SearchModel) -> some View {
        @Bindable var model = model

        Group {
            if let hits = model.visible.value, sizeClass == .regular {
                grid(hits, model: model)
            } else if let hits = model.visible.value {
                List {
                    if !model.isSearching {
                        // Named, because an unlabelled list of films on a
                        // search screen reads as results for something the user
                        // did not type. A header with no rows of its own: each
                        // result is a card, and a card is a section, which
                        // cannot sit inside another one.
                        Section {} header: {
                            Text("Trending this week")
                        }
                    }
                    rows(hits, model: model)
                }
                .cardList()
            } else {
                switch model.visible {
                case .loading:
                    SkeletonList()
                case .failed(let failure):
                    FailureView(failure: failure) { model.retry() }
                case .empty where !model.isSearching:
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
        // A provider title, never a bare number: a hit's id is TMDB's, and the
        // detail screen takes ours. ProviderTitleView turns one into the other.
        .navigationDestination(for: ProviderTitle.self) { title in
            ProviderTitleView(title: title)
        }
    }

    /// Regular width: results and the trending shelf as a grid of posters,
    /// each with the same add control the rows have, under the poster rather
    /// than inside the link so a tap on it adds instead of opening.
    private func grid(_ hits: [SearchHit], model: SearchModel) -> some View {
        ScrollView {
            if !model.isSearching {
                Text("Trending this week")
                    .font(.title3.bold())
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal)
                    .accessibilityAddTraits(.isHeader)
            }
            LazyVGrid(columns: PosterGrid.columns, spacing: PosterGrid.spacing) {
                ForEach(hits, id: \.providerId) { hit in
                    let alreadyAdded = hit.library != nil || model.added.contains(hit.providerId)
                    VStack(alignment: .leading, spacing: 4) {
                        NavigationLink(value: ProviderTitle(hit)) {
                            PosterCell(url: hit.posterUrl.flatMap(URL.init(string:)), title: hit.title) {
                                Text(hit.subtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .buttonStyle(.plain)

                        if alreadyAdded {
                            Label("In your library", systemImage: "checkmark.circle.fill")
                                .font(.caption)
                                .foregroundStyle(.tint)
                        } else if model.adding.contains(hit.providerId) {
                            // Said while it happens, so a second tap is not
                            // made on a button that seems to have done nothing.
                            ProgressView()
                                .controlSize(.small)
                                .frame(minHeight: 28)
                        } else {
                            Button {
                                Task { await model.addToLibrary(hit) }
                            } label: {
                                Label("Add", systemImage: "plus.circle")
                                    .font(.caption.weight(.semibold))
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                            .accessibilityLabel("Add \(hit.title) to your library")
                        }
                    }
                }
            }
            .padding()
        }
        .background(Color.screenBackground)
    }

    /// One row builder for both lists, so a result and a trending row are the
    /// same thing -- including the swipe that adds it.
    @ViewBuilder
    private func rows(_ hits: [SearchHit], model: SearchModel) -> some View {
        ForEach(hits, id: \.providerId) { hit in
            CardRow {
                // The server's entry covers anything added before this screen
                // opened, on this device or another; `added` covers what was added
                // since, without waiting for a reload.
                let alreadyAdded = hit.library != nil || model.added.contains(hit.providerId)
                NavigationLink(value: ProviderTitle(hit)) {
                    SearchResultRow(
                        hit: hit,
                        alreadyAdded: alreadyAdded,
                        isAdding: model.adding.contains(hit.providerId),
                        add: { Task { await model.addToLibrary(hit) } }
                    )
                }
                // Kept as a shortcut beside the visible button. On its own it was
                // the only way to add from here, and nothing on screen said so.
                .swipeActions(edge: .trailing) {
                    if !alreadyAdded {
                        Button {
                            Task { await model.addToLibrary(hit) }
                        } label: {
                            Label("Add", systemImage: "plus")
                        }
                        .tint(.accentColor)
                    }
                }
            }
        }
    }
}

private struct SearchResultRow: View {
    let hit: SearchHit
    let alreadyAdded: Bool
    let isAdding: Bool
    let add: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            PosterView(url: hit.posterUrl.flatMap(URL.init(string:)), title: hit.title)
                .frame(width: 56)

            VStack(alignment: .leading, spacing: 2) {
                Text(hit.title)
                    .font(.headline)
                    .lineLimit(2)
                Text(hit.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                // Words as well as the check, which on its own reads as
                // "selected" as easily as "tracked".
                if alreadyAdded {
                    Text("In your library")
                        .font(.caption)
                        .foregroundStyle(.tint)
                }
            }

            Spacer()

            // The button becomes the confirmation in the same place, so the
            // row says what the tap did without moving anything under the
            // finger.
            if alreadyAdded {
                Image(systemName: "checkmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.tint)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityLabel("Already in your library")
            } else {
                Button(action: add) {
                    Image(systemName: "plus.circle")
                        .font(.title2)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                // Borderless, or a tap anywhere on the row would press it as
                // well as opening the title: a List hands the whole row to a
                // default-styled button.
                .buttonStyle(.borderless)
                .disabled(isAdding)
                .accessibilityLabel("Add \(hit.title) to your library")
            }
        }
    }
}

private extension SearchHit {
    /// Type, year and TMDB rating, the line under a result's name.
    var subtitle: String {
        // Looked up, not a literal: this is joined into a String and handed
        // to Text in a variable, which does not localise. "TV series" rather
        // than "Series" so the key cannot collide with the history filter's
        // plural category — one title is a Serie, the category is Serien.
        var parts: [String] = [
            titleType == .movie
                ? String(localized: "Film")
                : String(localized: "TV series")
        ]
        if let date = releaseDate {
            parts.append(date.formatted(.dateTime.year()))
        }
        // A null rating is omitted rather than shown as 0.0: most titles
        // genuinely have no rating yet.
        // Named as TMDB's, as on the detail screen: a bare number beside a
        // title could be anyone's opinion, including the user's own.
        if let rating = providerRating {
            let figure = rating.formatted(.number.precision(.fractionLength(1)))
            parts.append(String(localized: "TMDB \(figure)"))
        }
        return parts.joined(separator: " · ")
    }
}
