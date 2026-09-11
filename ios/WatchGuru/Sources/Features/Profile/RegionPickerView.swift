import SwiftUI

/// Picks the country whose streaming offers the user sees.
///
/// Every country rather than a shortlist of the popular ones: a shortlist is a
/// judgement about whose custom matters, and the person it excludes is left
/// with an app that is quietly wrong for them. The search field is what makes
/// two hundred rows usable.
struct RegionPickerView: View {

    let currentRegion: String
    let pick: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    // Built once. The list is the same for the life of the screen, and
    // rebuilding it per keystroke would sort two hundred strings for nothing.
    private let regions = Regions.all()

    private var matches: [Regions.Region] {
        Regions.search(regions, query: query)
    }

    var body: some View {
        List(matches) { region in
            Button {
                // Picking a row is the change; there is no save button to
                // press for a decision already made.
                pick(region.code)
                dismiss()
            } label: {
                HStack {
                    Text(region.name)
                        .foregroundStyle(.primary)
                    Spacer()
                    if region.code.caseInsensitiveCompare(currentRegion) == .orderedSame {
                        Image(systemName: "checkmark")
                            .foregroundStyle(.tint)
                            .accessibilityHidden(true)
                    }
                }
            }
            .accessibilityAddTraits(
                region.code.caseInsensitiveCompare(currentRegion) == .orderedSame ? [.isSelected] : []
            )
        }
        .searchable(text: $query, prompt: "Search countries")
        .navigationTitle("Choose your region")
        .navigationBarTitleDisplayMode(.inline)
    }
}
