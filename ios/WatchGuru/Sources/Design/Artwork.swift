import SwiftUI

enum Artwork {
    /// The universal film-poster ratio. Never crop a poster to a square.
    static let posterAspectRatio: CGFloat = 2.0 / 3.0
    static let backdropAspectRatio: CGFloat = 16.0 / 9.0
}

/// A poster, with the placeholder that stands in when there isn't one.
///
/// TMDB does not have artwork for everything, so a missing poster is normal
/// rather than exceptional. The placeholder carries the title's initials: a
/// bare grey rectangle is indistinguishable from a failed load, which is
/// indistinguishable from a bug.
///
/// The frame is fixed at the correct ratio before the image arrives, so a list
/// does not reflow as its rows load.
struct PosterView: View {
    let url: URL?
    let title: String

    var body: some View {
        Rectangle()
            .fill(.quaternary)
            .aspectRatio(Artwork.posterAspectRatio, contentMode: .fit)
            .overlay {
                if let url {
                    AsyncImage(url: url) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        ProgressView()
                    }
                } else {
                    Text(Self.initials(of: title))
                        .font(.title2)
                        .foregroundStyle(.secondary)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .accessibilityLabel(Text("Poster for \(title)"))
    }

    static func initials(of title: String) -> String {
        let words = title.split(separator: " ").prefix(2)
        let letters = words.compactMap(\.first).map(String.init).joined().uppercased()
        return letters.isEmpty ? "?" : letters
    }
}
