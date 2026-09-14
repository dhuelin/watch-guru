import SwiftUI
import WatchGuruAPI

/// What the user has recorded about this title.
///
/// The detail screen could not previously say whether a title was in the
/// library at all, let alone add it — which became a dead end the moment the
/// search tab started showing people things they had not gone looking for.
///
/// Rating lives here rather than on a history entry because it is an opinion
/// about the thing, not about one viewing of it: rewatching a film does not
/// give you a second opinion of it, it gives you the same one again.
struct LibraryEntryView: View {

    let title: TitleResponse
    let isMarking: Bool
    let add: () -> Void
    let setStatus: (Int64, UpdateWatchlistItem.Status) -> Void
    let setRating: (Int64, Int) -> Void
    let remove: (Int64) -> Void

    /// Held while dragging and sent once at the end: sending on every change
    /// would be nine requests on the way from 1 to 10.
    @State private var draggingRating: Double?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Your library").font(.headline)

            if let entry = title.library {
                Picker("Status", selection: statusBinding(entry)) {
                    ForEach(Self.statuses, id: \.self) { status in
                        Text(Self.label(for: status)).tag(status)
                    }
                }
                .pickerStyle(.menu)
                .disabled(isMarking)

                rating(entry)

                Button("Remove from library", role: .destructive) { remove(entry.itemId) }
                    .buttonStyle(.borderless)
                    .disabled(isMarking)
            } else {
                Text("You are not tracking this yet.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Add to library", action: add)
                    .buttonStyle(.borderedProminent)
                    .disabled(isMarking)
            }
        }
    }

    @ViewBuilder
    private func rating(_ entry: LibraryEntry) -> some View {
        let current = draggingRating ?? entry.rating ?? 0
        let shown = Int(current.rounded())

        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("Your rating").font(.subheadline)
                Text(shown == 0 ? "Not rated" : "\(shown) / 10")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            // Ten stops rather than five stars: 0 to 10 is what the column
            // stores and what the two ratings above this section already use.
            Slider(
                value: Binding(
                    get: { current },
                    set: { draggingRating = $0 }
                ),
                in: 0...10,
                step: 1,
                onEditingChanged: { editing in
                    guard !editing, shown > 0 else { return }
                    setRating(entry.itemId, shown)
                    draggingRating = nil
                }
            )
            .disabled(isMarking)
            .accessibilityLabel("Your rating out of ten")
        }
    }

    private func statusBinding(_ entry: LibraryEntry) -> Binding<UpdateWatchlistItem.Status> {
        Binding(
            get: { UpdateWatchlistItem.Status(rawValue: entry.status.rawValue) ?? .watchlist },
            set: { setStatus(entry.itemId, $0) }
        )
    }

    /// In the order somebody moves through them, not the order the enum
    /// declares.
    private static let statuses: [UpdateWatchlistItem.Status] = [
        .watchlist, .watching, .completed, .onHold, .dropped,
    ]

    private static func label(for status: UpdateWatchlistItem.Status) -> String {
        switch status {
        case .watchlist: "Watchlist"
        case .watching: "Watching"
        case .completed: "Completed"
        case .onHold: "On hold"
        case .dropped: "Dropped"
        }
    }
}
