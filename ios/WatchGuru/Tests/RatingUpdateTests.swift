import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// Why the title screen offers no "Clear rating".
///
/// The update is a partial PATCH: the backend leaves any field it is not given
/// alone, and treats an explicit null the same way. These pin the client half
/// of that, so the day either side changes, this fails and somebody revisits
/// the rating control rather than discovering it by accident.
struct RatingUpdateTests {

    private func encoded(_ update: UpdateWatchlistItem) throws -> [String: Any] {
        let data = try JSONEncoder().encode(update)
        return try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
    }

    @Test("a rating is sent as a number")
    func sendsRating() throws {
        let body = try encoded(UpdateWatchlistItem(rating: 7))
        #expect(body["rating"] as? Double == 7)
    }

    @Test("a nil rating is left out of the body, so it cannot clear one")
    func nilRatingIsOmitted() throws {
        let body = try encoded(UpdateWatchlistItem(rating: nil, status: .watching))
        #expect(body["rating"] == nil)
        #expect(body.keys.contains("rating") == false)
    }
}
