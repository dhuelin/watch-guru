import SwiftUI

/// The screen and card colours.
///
/// Named colours in the asset catalog, each with a light and a dark value, so
/// screens use a role and dark mode comes with it. They are the same tones as
/// Android's `background` and card roles: a violet-tinted screen with cards one
/// step lighter on it.
extension Color {
    static let screenBackground = Color("ScreenBackground")
    static let cardBackground = Color("CardBackground")
}

extension View {

    /// A list of cards on the tinted background.
    ///
    /// Inset-grouped, so each `CardRow` is a rounded card with a gap above
    /// and below it, and a swipe action is clipped to the card's corners
    /// rather than running the width of the screen.
    func cardList() -> some View {
        self
            .listStyle(.insetGrouped)
            .listSectionSpacing(10)
            .scrollContentBackground(.hidden)
            .background(Color.screenBackground)
    }

    /// A screen whose background is the tinted one, for a list that is not
    /// made of cards.
    func screenBackground() -> some View {
        self
            .scrollContentBackground(.hidden)
            .background(Color.screenBackground)
    }
}

/// One row of a `cardList()`: its own section, which is what draws it as a
/// separate card.
struct CardRow<Content: View>: View {
    /// Drawn in the accent's tint instead of the card colour: the selected
    /// row of a list with its detail beside it. The card's own background
    /// covers the list's selection highlight, so the card has to show it.
    var highlighted = false
    @ViewBuilder let content: Content

    var body: some View {
        Section {
            content
        }
        .listRowBackground(
            Color.cardBackground
                .overlay(highlighted ? Color.accentColor.opacity(0.18) : .clear)
        )
    }
}

extension View {

    /// Keeps a scrolling screen's content to a readable column on a wide
    /// screen, centred, with the background still running edge to edge.
    ///
    /// On an iPad a title screen or the statistics pushed full width put a
    /// progress bar 1,000 points long under a line of text at the other end.
    /// A phone is narrower than the limit, so nothing changes there.
    func readableWidth(_ maxWidth: CGFloat = 760) -> some View {
        modifier(ReadableWidth(maxWidth: maxWidth))
    }
}

private struct ReadableWidth: ViewModifier {
    let maxWidth: CGFloat

    func body(content: Content) -> some View {
        GeometryReader { proxy in
            content.contentMargins(
                .horizontal,
                max(0, (proxy.size.width - maxWidth) / 2),
                for: .scrollContent
            )
        }
    }
}
