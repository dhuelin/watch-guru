import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// The two rules behind the "Where to watch" section: what order the ways of
/// watching appear in, and that one service never shows up twice in a group.
struct WatchOffersTests {

    private func offer(
        _ serviceId: Int64,
        _ name: String,
        _ type: AvailabilityResponse.OfferType,
        link: String? = nil
    ) -> AvailabilityResponse {
        AvailabilityResponse(
            fetchedAt: Date(timeIntervalSince1970: 0),
            link: link,
            logoUrl: nil,
            offerType: type,
            serviceId: serviceId,
            serviceName: name
        )
    }

    @Test("free ways to watch come before paid ones")
    func order() {
        let groups = WatchOffers.group([
            offer(1, "Apple TV", .buy),
            offer(2, "Amazon", .rent),
            offer(3, "Netflix", .flatrate),
            offer(4, "Pluto TV", .ads)
        ])

        #expect(groups.map(\.offerType) == [.flatrate, .ads, .rent, .buy])
    }

    @Test("a group with nothing in it is not shown at all")
    func emptyGroupsDropped() {
        let groups = WatchOffers.group([offer(3, "Netflix", .flatrate)])

        #expect(groups.count == 1)
        #expect(groups.first?.offerType == .flatrate)
    }

    @Test("the same service twice in one group is shown once")
    func duplicatesCollapse() {
        let groups = WatchOffers.group([
            offer(3, "Netflix", .flatrate),
            offer(3, "Netflix", .flatrate)
        ])

        #expect(groups.first?.services.map(\.serviceName) == ["Netflix"])
    }

    @Test("a service offering two ways to watch appears in both groups")
    func sameServiceTwoOfferTypes() {
        let groups = WatchOffers.group([
            offer(1, "Apple TV", .rent),
            offer(1, "Apple TV", .buy)
        ])

        #expect(groups.map(\.offerType) == [.rent, .buy])
    }

    @Test("services within a group are sorted by name, case aside")
    func sortedByName() {
        let groups = WatchOffers.group([
            offer(1, "netflix", .flatrate),
            offer(2, "Disney+", .flatrate)
        ])

        #expect(groups.first?.services.map(\.serviceName) == ["Disney+", "netflix"])
    }

    @Test("the link is taken from whichever offer has one")
    func linkFound() {
        let link = WatchOffers.link([
            offer(3, "Netflix", .flatrate),
            offer(1, "Apple TV", .buy, link: "https://example.test/watch")
        ])

        #expect(link == URL(string: "https://example.test/watch"))
    }

    @Test("a blank link is no link, so no button is offered")
    func blankLinkIsNoLink() {
        #expect(WatchOffers.link([offer(3, "Netflix", .flatrate, link: "  ")]) == nil)
        #expect(WatchOffers.link([]) == nil)
    }

    @Test("the section is only as current as its stalest row")
    func checkedAtIsTheOldest() {
        let older = Date(timeIntervalSince1970: 1_000)
        let newer = Date(timeIntervalSince1970: 2_000)
        let offers = [
            AvailabilityResponse(fetchedAt: newer, offerType: .flatrate, serviceId: 1, serviceName: "Netflix"),
            AvailabilityResponse(fetchedAt: older, offerType: .rent, serviceId: 2, serviceName: "Apple TV")
        ]

        #expect(WatchOffers.checkedAt(offers) == older)
    }

    @Test("no offers means nothing to date")
    func checkedAtOfNothing() {
        #expect(WatchOffers.checkedAt([]) == nil)
    }
}
