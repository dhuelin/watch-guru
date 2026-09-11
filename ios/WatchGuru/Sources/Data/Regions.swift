import Foundation

/// The list of countries the user can pick from, and how it is searched.
///
/// Kept out of the view so the rules can be tested: names come from the
/// platform in the user's own language, the list is sorted the way that
/// language sorts (which is not the same as sorting by code point — "Åland
/// Islands" belongs under A, not after Zimbabwe), and a search matches the
/// country code as well as the name.
enum Regions {

    struct Region: Identifiable, Hashable, Sendable {
        let code: String
        let name: String

        var id: String { code }
    }

    /// Every ISO 3166-1 country, named in `locale` and sorted for it.
    ///
    /// Two-letter codes only: the ISO region list also carries groupings such
    /// as "150" (Europe) and "001" (World), which are not somewhere a person
    /// subscribes to Netflix. Codes the platform has no name for are dropped —
    /// it hands the code back when it has no translation, and a row reading
    /// "QQ" helps nobody choose.
    static func all(locale: Locale = .current) -> [Region] {
        Locale.Region.isoRegions
            .filter { $0.identifier.count == 2 && $0.identifier.allSatisfy(\.isLetter) }
            .compactMap { region -> Region? in
                guard let name = locale.localizedString(forRegionCode: region.identifier),
                      name != region.identifier else { return nil }
                return Region(code: region.identifier, name: name)
            }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    /// The list narrowed to a search.
    ///
    /// The code is matched too, because someone who knows their country is
    /// "CH" should not have to remember whether the app calls it Switzerland,
    /// Suisse or Schweiz.
    static func search(_ regions: [Region], query: String) -> [Region] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return regions }
        return regions.filter {
            $0.name.localizedCaseInsensitiveContains(trimmed)
                || $0.code.caseInsensitiveCompare(trimmed) == .orderedSame
        }
    }

    /// The country's name, or the code itself when the platform has no name
    /// for it — never blank, because an empty row reads as a failed load.
    static func displayName(_ code: String, locale: Locale = .current) -> String {
        locale.localizedString(forRegionCode: code) ?? code
    }
}
