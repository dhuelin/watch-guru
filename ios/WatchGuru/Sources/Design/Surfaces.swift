import SwiftUI

/// The screen and card colours.
///
/// Named colours in the asset catalog, each with a light and a dark value, so
/// screens use a role and dark mode comes with it. They are the same tones as
/// Android's `background` and card roles: a violet-tinted screen with cards one
/// step lighter on it. Each also has an Increase Contrast variant, where the
/// screen goes a step darker (black in dark mode) so the cards stand apart
/// from it more than the gentle default does.
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
    @ViewBuilder let content: Content

    var body: some View {
        Section {
            content
        }
        .listRowBackground(Color.cardBackground)
    }
}
