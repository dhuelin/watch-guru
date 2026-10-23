import SwiftUI

extension View {

    /// The screen's main action: a filled button whose label stays legible in
    /// both themes.
    ///
    /// `.borderedProminent` alone draws a white label on the accent. That works
    /// on the light accent, a deep violet, and fails on the dark one — the same
    /// violet at tone 80, a pale lavender — where white measured about 1.8:1.
    /// The accent stays as it is, because it is the brand tone Android's
    /// `primary` uses too; the label changes instead, to the colour of the
    /// screen behind it. That is white in light mode and black in dark, which
    /// is the pairing Material gives `onPrimary` for the same two tones.
    ///
    /// Apply `.disabled` outside this, not inside: the label colour depends on
    /// whether the button is enabled, and only a modifier applied outside is
    /// visible from in here.
    func prominentAction() -> some View {
        modifier(ProminentAction())
    }
}

private struct ProminentAction: ViewModifier {

    @Environment(\.isEnabled) private var isEnabled

    func body(content: Content) -> some View {
        content
            .buttonStyle(.borderedProminent)
            // A disabled prominent button is drawn on a grey fill, where the
            // background colour would vanish; secondary is what the system
            // itself uses for a disabled label.
            .foregroundStyle(
                isEnabled ? AnyShapeStyle(Color(uiColor: .systemBackground)) : AnyShapeStyle(.secondary)
            )
    }
}
