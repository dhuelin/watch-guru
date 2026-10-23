import SwiftUI

/// A grid of posters, for lists of titles at regular width.
///
/// On an iPad a list of rows runs the width of a 13-inch screen with a
/// thumbnail at one edge and most of the space empty. Posters are what people
/// recognise a title by, so at regular width Library and Search show them as
/// a grid, the way a shelf of films is laid out; compact width keeps the rows.
enum PosterGrid {
    /// As many columns as fit at 150 to 190pt each, so an iPad in portrait,
    /// landscape and Split View each get a sensible count without a branch.
    static let columns = [GridItem(.adaptive(minimum: 150, maximum: 190), spacing: 20, alignment: .top)]
    static let spacing: CGFloat = 24
}

/// One title in a `PosterGrid`: the poster, its name, and whatever the screen
/// wants to say under it.
struct PosterCell<Caption: View>: View {
    let url: URL?
    let title: String
    @ViewBuilder let caption: Caption

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            PosterView(url: url, title: title)
                .shadow(color: .black.opacity(0.15), radius: 6, y: 3)
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            caption
        }
        // One element for VoiceOver, read as the title and its caption, rather
        // than a poster, a name and a status to swipe through separately.
        .accessibilityElement(children: .combine)
    }
}
