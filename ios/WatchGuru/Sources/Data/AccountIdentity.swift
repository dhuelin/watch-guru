import Foundation

/// How the profile names the person it belongs to.
///
/// Kept out of the view because both rules are judgements about strings, and
/// a judgement that is only ever looked at on a screen is never checked again.
enum AccountIdentity {

    /// The email to show under the name, or nil when there is none worth
    /// showing.
    ///
    /// An account created without a shared address gets a synthetic one, such
    /// as `9732…@users.noreply.watch-guru.invalid`, because the column cannot
    /// be empty. That string is an implementation detail: shown under the name
    /// it reads as the user's identity, and it is not an address anyone can
    /// write to. `.invalid` is the TLD reserved for exactly that (RFC 2606),
    /// and "noreply" covers relays that use a real domain.
    static func visibleEmail(_ email: String) -> String? {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let lowered = trimmed.lowercased()
        if lowered.hasSuffix(".invalid") || lowered.contains("noreply") || lowered.contains("no-reply") {
            return nil
        }
        return trimmed
    }

    /// "English (United States)" for "en-US", in the reader's own language.
    ///
    /// The raw tag is what the server stores and what TMDB is sent, and it is
    /// a code rather than a word. Falls back to the code for a tag the system
    /// cannot name, rather than to nothing.
    static func languageName(_ tag: String, locale: Locale = .current) -> String {
        locale.localizedString(forIdentifier: tag) ?? tag
    }

    /// "Central European Time (Europe/Berlin)" for "Europe/Berlin".
    ///
    /// The identifier stays beside the name: two zones can share a generic
    /// name, and the identifier is what someone would quote when it is wrong.
    static func timeZoneName(_ identifier: String, locale: Locale = .current) -> String {
        guard let zone = TimeZone(identifier: identifier),
              let name = zone.localizedName(for: .generic, locale: locale),
              name != identifier else {
            return identifier
        }
        return "\(name) (\(identifier))"
    }
}
