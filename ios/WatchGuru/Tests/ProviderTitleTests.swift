import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// A search hit opens through its provider id, never as a catalogue id.
struct ProviderTitleTests {

    private func hit(_ type: SearchHit.TitleType, id: Int64 = 9003) -> SearchHit {
        SearchHit(providerId: id, title: "Paper Moons", titleType: type)
    }

    @Test("the provider id and type travel together into the import")
    func carriesProviderIdAndType() {
        let film = ProviderTitle(hit(.movie))
        let series = ProviderTitle(hit(.tvSeries))

        #expect(film.providerId == 9003)
        #expect(film.importType == .movie)
        #expect(series.importType == .tvSeries)
    }

    @Test("the same id as a film and as a series are different titles")
    func typeIsPartOfIdentity() {
        #expect(ProviderTitle(hit(.movie)) != ProviderTitle(hit(.tvSeries)))
    }
}
