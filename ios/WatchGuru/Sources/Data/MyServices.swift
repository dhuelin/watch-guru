import Foundation
import Observation

/// Which streaming services the user says they pay for.
///
/// Used to order "Where to watch": a subscription they already hold costs them
/// nothing, so it belongs above a free service they have never heard of. See
/// `WatchOffers.group`, whose ordering comment promised this before it existed.
///
/// ### Why this lives on the device
///
/// It is not on the backend, and that is a deliberate limit rather than an
/// oversight. The region *is* server-side because the server needs it — every
/// availability query is per country. This is only ever read to arrange a list
/// the client already has, so the client can own it, and owning it there means
/// no new endpoint, no migration, and no regenerated API client.
///
/// What that costs: the selection does not follow the user to a second device.
/// #18 asks for it to *persist*, which this does. Moving it server-side later
/// is a migration of a handful of ids, and the seam for it is this type.
///
/// ### Why it clears on sign-out
///
/// "I pay for Netflix and MUBI" is a fact about a person, not about a phone. On
/// a shared device it would otherwise greet the next user with the last one's
/// subscriptions — the same reason the cached library and the widget feed go.
@Observable
@MainActor
final class MyServices {

    private static let fileName = "my-services.json"

    private let store: OfflineStore

    /// The ids, observed so an open title screen re-orders as soon as the
    /// picker is dismissed rather than on its next load.
    private(set) var selected: Set<Int64>

    init(store: OfflineStore) {
        self.store = store
        self.selected = Self.read(from: store)
    }

    func save(_ ids: Set<Int64>) {
        selected = ids
        // Sorted, so the file does not churn when a set iterates differently
        // between runs; a diff of this file should mean a real change.
        guard let data = try? JSONEncoder().encode(ids.sorted()) else { return }
        store.write(Self.fileName, data)
    }

    func clear() {
        selected = []
        store.delete(Self.fileName)
    }

    private static func read(from store: OfflineStore) -> Set<Int64> {
        guard let data = store.read(fileName) else { return [] }
        // A file we cannot decode is treated as no selection rather than as an
        // error: the worst case is an ordering the user has to set again, and
        // refusing to show "Where to watch" over it would be far worse.
        guard let ids = try? JSONDecoder().decode([Int64].self, from: data) else { return [] }
        return Set(ids)
    }
}
