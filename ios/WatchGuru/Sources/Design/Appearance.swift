import SwiftUI

/// Light, dark, or whatever the phone is set to.
///
/// Stored per device in `UserDefaults` rather than on the account: it is
/// about the screen in front of you, and the same person can want a dark
/// phone and a light iPad.
enum Appearance: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    /// The key `@AppStorage` keeps it under.
    static let storageKey = "appearance"

    var id: String { rawValue }

    /// Nil follows the system, which is what `preferredColorScheme` takes it
    /// to mean.
    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }

    var label: LocalizedStringKey {
        switch self {
        case .system: "System"
        case .light: "Light"
        case .dark: "Dark"
        }
    }
}
