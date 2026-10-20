import Foundation
import WatchGuruAPI

/// How the offers on a title become the "Where to watch" section.
///
/// Kept out of the view because the two rules below are the ones worth
/// testing: what order the ways of watching appear in, and that a service
/// never shows up twice within one of them.
enum WatchOffers {

    /// Ways of watching, in the order they are offered to the user.
    ///
    /// Cheapest first, which is what the app can honestly rank by: free, then
    /// free with ads, then a subscription, then rent, then buy. It does not
    /// know which services the user already pays for — when it does, a
    /// subscription they hold belongs above a free service they have never
    /// heard of, and this order should change with it.
    ///
    /// Free was previously listed after subscriptions, which contradicted this
    /// comment; the comment was the one making the promise.
    static let order: [AvailabilityResponse.OfferType] = [.free, .ads, .flatrate, .rent, .buy]

    struct Group: Identifiable, Sendable {
        let offerType: AvailabilityResponse.OfferType
        let services: [AvailabilityResponse]

        /// Whether this group uses a service the user subscribes to.
        ///
        /// Carried so the screen can say so. Reordering with no visible reason
        /// reads as arbitrary — the badge is what turns "why is Rent above
        /// Free" into "because I pay for that one".
        var yours: Bool = false

        var id: String { offerType.rawValue }
    }

    /// One group per way of watching, empty groups dropped.
    ///
    /// - Parameter mine: the services the user says they subscribe to. A way of
    ///   watching that uses one of them comes first, because to that user it
    ///   costs nothing — the same reason free comes before rent. The remaining
    ///   groups keep `order`.
    ///
    /// Partitioned rather than sorted, on both platforms, so the two agree
    /// exactly. `sorted(by:)` makes no stability promise, so a sort on the
    /// boolean alone could reshuffle the groups that tie; splitting the list
    /// preserves each side's order by construction.
    static func group(
        _ offers: [AvailabilityResponse],
        mine: Set<Int64> = []
    ) -> [Group] {
        let groups: [Group] = order.compactMap { type in
            var seen = Set<Int64>()
            let services = offers
                .filter { $0.offerType == type }
                // A provider can return one service twice for the same offer
                // type (regional sub-brands share an id); "Netflix, Netflix"
                // would read as a bug.
                .filter { seen.insert($0.serviceId).inserted }
                .sorted { $0.serviceName.lowercased() < $1.serviceName.lowercased() }
            guard !services.isEmpty else { return nil }
            // Within a group too: the service they pay for is the one they will
            // use, so it goes above one they would have to sign up to.
            let held = services.filter { mine.contains($0.serviceId) }
            let rest = services.filter { !mine.contains($0.serviceId) }
            return Group(offerType: type, services: held + rest, yours: !held.isEmpty)
        }
        return groups.filter(\.yours) + groups.filter { !$0.yours }
    }

    /// The one link to open, or nil when there isn't one.
    ///
    /// The provider gives a single link per region — a page listing every way
    /// to watch the title — not a deep link per service. So the section offers
    /// one link rather than making each service row look like a way into that
    /// app, which it is not.
    static func link(_ offers: [AvailabilityResponse]) -> URL? {
        offers
            .compactMap(\.link)
            .first { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .flatMap(URL.init(string:))
    }

    /// When this data was last confirmed with the provider.
    ///
    /// The oldest of the rows rather than the newest: a section is only as
    /// current as its stalest row, and claiming otherwise would round in the
    /// flattering direction.
    static func checkedAt(_ offers: [AvailabilityResponse]) -> Date? {
        offers.map(\.fetchedAt).min()
    }

    /// The heading for a group. Shared with the Android app verbatim.
    static func label(for type: AvailabilityResponse.OfferType) -> String {
        switch type {
        case .flatrate: String(localized: "Stream")
        case .free: String(localized: "Free")
        case .ads: String(localized: "Free with ads")
        case .rent: String(localized: "Rent")
        case .buy: String(localized: "Buy")
        }
    }
}
