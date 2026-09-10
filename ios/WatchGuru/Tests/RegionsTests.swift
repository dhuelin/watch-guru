import Foundation
import Testing
@testable import WatchGuru

/// How the country list is built and searched.
struct RegionsTests {

    private let english = Locale(identifier: "en_US")

    @Test("the list covers the countries people will look for")
    func coversCommonCountries() {
        let codes = Set(Regions.all(locale: english).map(\.code))

        #expect(codes.isSuperset(of: ["US", "GB", "CH", "DE", "JP"]))
    }

    @Test("countries are named, not left as codes")
    func named() {
        #expect(Regions.all(locale: english).first { $0.code == "CH" }?.name == "Switzerland")
    }

    @Test("groupings such as Europe and World are not offered as countries")
    func noGroupings() {
        let codes = Set(Regions.all(locale: english).map(\.code))

        #expect(!codes.contains("150"))
        #expect(!codes.contains("001"))
    }

    @Test("accented names sort under their base letter, not after Z")
    func sortedWithDiacritics() {
        // The reason the list is sorted with localizedStandardCompare rather
        // than <. By code point, the two countries spelled with an accent land
        // past Zimbabwe, where nobody scrolling for A or C would find them.
        let names = Regions.all(locale: english).map(\.name)
        guard let aland = names.first(where: { $0.hasSuffix("land Islands") }),
              let ivoryCoast = names.first(where: { $0.hasSuffix("te d\u{2019}Ivoire") }),
              let alandIndex = names.firstIndex(of: aland),
              let ivoryIndex = names.firstIndex(of: ivoryCoast),
              let albania = names.firstIndex(of: "Albania"),
              let croatia = names.firstIndex(of: "Croatia"),
              let costaRica = names.firstIndex(of: "Costa Rica") else {
            Issue.record("the country list is missing the names this test is about")
            return
        }

        #expect(alandIndex < albania)
        #expect(ivoryIndex < croatia)
        #expect(ivoryIndex > costaRica)
    }

    @Test("searching matches part of a name, whatever the case")
    func searchByName() {
        let hits = Regions.search(Regions.all(locale: english), query: "switz")

        #expect(hits.map(\.code) == ["CH"])
    }

    @Test("searching matches the country code itself")
    func searchByCode() {
        let hits = Regions.search(Regions.all(locale: english), query: "ch")

        #expect(hits.contains { $0.code == "CH" })
    }

    @Test("an empty search is not a filter")
    func emptySearch() {
        let all = Regions.all(locale: english)

        #expect(Regions.search(all, query: "   ") == all)
    }

    @Test("a code the platform has no name for reads back as itself")
    func unknownCode() {
        // Never blank: a profile row showing nothing at all looks like a
        // failed load. "ZZ" is not the example to use — CLDR does have a name
        // for it, its own placeholder, "Unknown Region".
        #expect(Regions.displayName("QQ", locale: english) == "QQ")
    }
}
