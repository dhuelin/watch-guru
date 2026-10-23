import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// How the title screen says "change my rating" and "take it back".
///
/// The update is a partial PATCH: the backend leaves any field it is not given
/// alone, and treats an explicit null the same way. Clearing is therefore its
/// own flag, and these pin the client half of that.
struct RatingUpdateTests {

    private func encoded(_ update: UpdateWatchlistItem) throws -> [String: Any] {
        let data = try JSONEncoder().encode(update)
        return try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
    }

    @Test("a rating is sent as a number")
    func sendsRating() throws {
        let body = try encoded(TitleDetailModel.ratingUpdate(7))
        #expect(body["rating"] as? Double == 7)
        #expect(body.keys.contains("clearRating") == false)
    }

    @Test("no rating is sent as clearRating, not as a missing field")
    func clearsRating() throws {
        let body = try encoded(TitleDetailModel.ratingUpdate(nil))
        #expect(body["clearRating"] as? Bool == true)
        #expect(body.keys.contains("rating") == false)
    }

    @Test("a nil rating is left out of the body, so it cannot clear one by itself")
    func nilRatingIsOmitted() throws {
        let body = try encoded(UpdateWatchlistItem(rating: nil, status: .watching))
        #expect(body["rating"] == nil)
        #expect(body.keys.contains("rating") == false)
    }
}
