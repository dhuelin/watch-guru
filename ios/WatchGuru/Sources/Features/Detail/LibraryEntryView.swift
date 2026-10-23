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
    let setRating: (Int64, Int?) -> Void
    let remove: (Int64) -> Void

    @State private var confirmingRemoval = false

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

                // Last in the section rather than between the two controls
                // people use most, and behind a question: a destructive link
                // squeezed between the rating and the progress bar was one
                // mis-tap from losing both.
                Button("Remove from library", role: .destructive) { confirmingRemoval = true }
                    .buttonStyle(.borderless)
                    .font(.subheadline)
                    .padding(.top, 8)
                    .disabled(isMarking)
                    .confirmationDialog(
                        String(localized: "Remove \(title.primaryTitle) from your library?"),
                        isPresented: $confirmingRemoval,
                        titleVisibility: .visible
                    ) {
                        Button("Remove from library", role: .destructive) { remove(entry.itemId) }
                        Button("Cancel", role: .cancel) {}
                    } message: {
                        Text("Its status and your rating go with it. Your viewing history is kept.")
                    }
            } else {
                Text("You are not tracking this yet.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button("Add to library", action: add)
                    .prominentAction()
                    .disabled(isMarking)
            }
        }
    }

    /// The user's own score, as a labelled menu showing its value.
    ///
    /// A menu of ten values rather than the slider it replaced. The slider was
    /// a violet track a few points above the episode progress bar — another
    /// violet track — so the user's opinion looked like a measurement, which
    /// docs/DESIGN.md says it must never be confused with. A menu also sends
    /// one request per decision without any drag state to get stuck.
    ///
    /// "Not rated" is always on the menu, so a rating can be taken back as
    /// easily as it was given. Choosing it clears the rating rather than
    /// sending 0, which the design keeps distinct from "not rated".
    @ViewBuilder
    private func rating(_ entry: LibraryEntry) -> some View {
        let current = entry.rating.map { Int($0.rounded()) }

        // Label and value side by side: outside a Form a menu picker draws only
        // its current value, and "7/10" with nothing beside it says nothing
        // about whose seven it is.
        HStack {
            Label("Your rating", systemImage: "star")
                .font(.subheadline)
            Spacer()
            Picker("Your rating", selection: Binding(
                get: { current },
                set: { picked in
                    if picked != current { setRating(entry.itemId, picked) }
                }
            )) {
                Text("Not rated").tag(Int?.none)
                ForEach(Array((1...10).reversed()), id: \.self) { score in
                    Text("\(score)/10").tag(Int?.some(score))
                }
            }
            .pickerStyle(.menu)
            .labelsHidden()
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
        case .watchlist: String(localized: "Watchlist")
        case .watching: String(localized: "Watching")
        case .completed: String(localized: "Completed")
        case .onHold: String(localized: "On hold")
        case .dropped: String(localized: "Dropped")
        }
    }
}
