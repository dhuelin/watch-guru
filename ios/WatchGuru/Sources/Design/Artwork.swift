import SwiftUI

enum Artwork {
    /// The universal film-poster ratio. Never crop a poster to a square.
    static let posterAspectRatio: CGFloat = 2.0 / 3.0
    static let backdropAspectRatio: CGFloat = 16.0 / 9.0
    /// How tall a title screen's backdrop is, whatever the width.
    static let backdropHeight: CGFloat = 240
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

    /// `nonisolated` because it is pure: a String in, a String out, no view
    /// state touched. `PosterView` is a `View`, so under Swift 6 the whole type
    /// is main-actor isolated and this inherited that for no reason -- which
    /// the test caught, being the only caller outside the main actor. Marking
    /// the test @MainActor instead would have hidden a declaration that was
    /// simply wrong.
    nonisolated static func initials(of title: String) -> String {
        let words = title.split(separator: " ").prefix(2)
        let letters = words.compactMap(\.first).map(String.init).joined().uppercased()
        return letters.isEmpty ? "?" : letters
    }
}

/// A title's backdrop, fading into the screen behind it.
///
/// A fixed height rather than 16:9 of the width: on a phone in portrait the
/// two are about the same, and in landscape or on an iPad 16:9 would be a
/// whole screen of artwork before anything the user came for. The image is
/// cropped to fill it.
///
/// Decoration under the header rather than content, so VoiceOver skips it,
/// and with no placeholder: without a backdrop the header is simply the
/// background, which is how a title with no artwork should look.
struct BackdropView: View {
    let url: URL?

    var body: some View {
        Color.clear
            .frame(height: Artwork.backdropHeight)
            .frame(maxWidth: .infinity)
            .overlay {
                if let url {
                    AsyncImage(url: url) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        Color.clear
                    }
                }
            }
            .clipped()
            // Clear at the top to the screen colour at the bottom, so the
            // poster laid over its lower edge sits on the screen rather than
            // on a hard line where the image stops.
            .overlay {
                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0.3),
                        .init(color: .screenBackground, location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .accessibilityHidden(true)
    }
}
