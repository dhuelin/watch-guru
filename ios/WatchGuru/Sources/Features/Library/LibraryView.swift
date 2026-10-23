import SwiftUI
import WatchGuruAPI

struct LibraryView: View {

    @Environment(Session.self) private var session
    @State private var model: LibraryModel?
    /// The row a swipe asked to remove, held until the user confirms.
    @State private var pendingRemoval: WatchlistItemResponse?
    @Environment(\.horizontalSizeClass) private var sizeClass

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
            if let items = model.state.value, sizeClass == .regular {
                grid(items, model: model)
            } else if let items = model.state.value {
                List {
                    // Said in the list as well as on the toolbar icon: a
                    // filtered library otherwise looks like a library that has
                    // lost most of its rows.
                    if let filter = model.filter {
                        CardRow {
                            HStack {
                                Text(String(localized: "Showing: \(label(for: filter))"))
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                Spacer()
                                Button("Show all") { model.filter = nil }
                                    .buttonStyle(.borderless)
                                    .font(.subheadline)
                            }
                        }
                    }
                    ForEach(items, id: \.id) { item in
                        CardRow {
                            NavigationLink(value: item.title.id) {
                                LibraryRow(item: item)
                            }
                            // Not destructive-roled: that removes the row the
                            // moment the swipe ends, before the dialog has asked
                            // anything.
                            .swipeActions(edge: .trailing) {
                                Button {
                                    pendingRemoval = item
                                } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                                .tint(.red)
                            }
                        }
                    }
                }
                .cardList()
                .refreshable { await model.load() }
            } else {
                switch model.state {
                case .loading:
                    SkeletonList()
                case .failed(let failure):
                    FailureView(failure: failure) { Task { await model.load() } }
                default:
                    if let filter = model.filter {
                        // The library is not empty, the filter is. Saying
                        // "nothing tracked yet" here told someone with ten
                        // titles to go and add one.
                        ContentUnavailableView {
                            Label(emptyTitle(for: filter), systemImage: "line.3.horizontal.decrease.circle")
                        } actions: {
                            Button("Show all") { model.filter = nil }
                        }
                    } else {
                        ContentUnavailableView(
                            "Nothing tracked yet",
                        systemImage: "books.vertical",
                            description: Text("Search for a film or series to get started.")
                        )
                    }
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
                    // Filled while a filter is on, the system's own way of
                    // saying a filter icon is doing something.
                    Label(
                        "Filter",
                        systemImage: model.filter == nil
                            ? "line.3.horizontal.decrease.circle"
                            : "line.3.horizontal.decrease.circle.fill"
                    )
                }
            }
        }
        .navigationDestination(for: Int64.self) { titleId in
            TitleDetailView(titleId: titleId)
        }
        // A swipe is easy to make by accident, and a removal takes the rating
        // and status with it, so it asks first.
        .confirmationDialog(
            pendingRemoval.map { String(localized: "Remove \($0.title.primaryTitle) from your library?") } ?? "",
            isPresented: Binding(
                get: { pendingRemoval != nil },
                set: { if !$0 { pendingRemoval = nil } }
            ),
            titleVisibility: .visible,
            presenting: pendingRemoval
        ) { item in
            Button("Remove from library", role: .destructive) {
                Task { await model.remove(item) }
            }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("Its status and your rating go with it. Your viewing history is kept.")
        }
    }

    /// Regular width: the library as a shelf of posters. Removal moves from a
    /// swipe, which a grid cell does not have, to the context menu, with the
    /// same confirmation.
    private func grid(_ items: [WatchlistItemResponse], model: LibraryModel) -> some View {
        ScrollView {
            if let filter = model.filter {
                HStack {
                    Text(String(localized: "Showing: \(label(for: filter))"))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Button("Show all") { model.filter = nil }
                        .font(.subheadline)
                }
                .padding(.horizontal)
            }
            LazyVGrid(columns: PosterGrid.columns, spacing: PosterGrid.spacing) {
                ForEach(items, id: \.id) { item in
                    NavigationLink(value: item.title.id) {
                        PosterCell(
                            url: item.title.posterUrl.flatMap(URL.init(string:)),
                            title: item.title.primaryTitle
                        ) {
                            LibraryStatusCaption(item: item)
                        }
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button(role: .destructive) {
                            pendingRemoval = item
                        } label: {
                            Label("Remove from library", systemImage: "trash")
                        }
                    }
                }
            }
            .padding()
        }
        .background(Color.screenBackground)
        .refreshable { await model.load() }
    }

    /// One sentence per status rather than "Nothing %@": German declines what
    /// English leaves alone, and "nichts auf Pause" is not "nichts abgebrochen".
    private func emptyTitle(for status: WatchlistControllerAPI.Status_listWatchlist) -> String {
        switch status {
        case .watchlist: String(localized: "Nothing on your watchlist")
        case .watching: String(localized: "Nothing you're watching")
        case .completed: String(localized: "Nothing completed")
        case .onHold: String(localized: "Nothing on hold")
        case .dropped: String(localized: "Nothing dropped")
        }
    }

    private func label(for status: WatchlistControllerAPI.Status_listWatchlist) -> String {
        switch status {
        case .watchlist: String(localized: "Watchlist")
        case .watching: String(localized: "Watching")
        case .completed: String(localized: "Completed")
        case .onHold: String(localized: "On hold")
        case .dropped: String(localized: "Dropped")
        }
    }
}

private struct LibraryRow: View {
    let item: WatchlistItemResponse

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        // At accessibility sizes the poster goes above the text rather than
        // beside it: beside, it squeezed a title into "Saltwater Ki…".
        let layout = typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(spacing: 12))

        layout {
            PosterView(
                url: item.title.posterUrl.flatMap(URL.init(string:)),
                title: item.title.primaryTitle
            )
            .frame(width: typeSize.isAccessibilitySize ? 96 : 60)

            VStack(alignment: .leading, spacing: 4) {
                Text(item.title.primaryTitle)
                    .font(.headline)
                    .lineLimit(typeSize.isAccessibilitySize ? 4 : 2)

                LibraryStatusCaption(item: item)
            }
        }
    }
}

/// The status and episode progress under a title, shared by the row and the
/// poster grid so both say the same thing the same way.
private struct LibraryStatusCaption: View {
    let item: WatchlistItemResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            // Icon and label alongside the colour: status is never conveyed
            // by colour alone.
            Label {
                Text(item.status.style.label)
                    .foregroundStyle(item.status.style.textColor)
            } icon: {
                Image(systemName: item.status.style.symbol)
                    .foregroundStyle(item.status.style.color)
            }
            .font(.caption)

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
