import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// How backend outcomes become things the UI can act on.
///
/// The mapping matters more than it looks. The backend deliberately keeps
/// serving a user's own library when TMDB is down, so "the catalogue is
/// unavailable" and "you are offline" are different situations with different
/// messages — and only one of them means the user cannot see their own data.
struct FailureMappingTests {

    private func response(_ status: Int) -> ErrorResponse {
        .error(status, nil, nil, NSError(domain: "test", code: status))
    }

    @Test("401 asks for re-authentication")
    func unauthorised() {
        #expect(WatchGuruClient.failure(for: response(401)) == .unauthorised)
    }

    @Test("403 is treated as unauthorised too")
    func forbidden() {
        // The backend answers 404 rather than 403 for another user's item, so
        // a 403 here means the token itself is not acceptable.
        #expect(WatchGuruClient.failure(for: response(403)) == .unauthorised)
    }

    @Test("404 is its own case")
    func notFound() {
        #expect(WatchGuruClient.failure(for: response(404)) == .notFound)
    }

    @Test("a TMDB outage is distinct from being offline")
    func upstream() {
        #expect(WatchGuruClient.failure(for: response(502)) == .upstream)
        #expect(WatchGuruClient.failure(for: response(503)) == .upstream)
    }

    @Test("an unrecognised status keeps its code for the bug report")
    func unexpected() {
        #expect(WatchGuruClient.failure(for: response(418)) == .unexpected(status: 418, message: nil))
    }

    @Test("transport failures read as offline")
    func offline() {
        for code in [URLError.notConnectedToInternet, .timedOut, .cannotFindHost, .networkConnectionLost] {
            #expect(WatchGuruClient.failure(for: URLError(code)) == .offline)
        }
    }

    @Test("a transport failure wrapped in an ErrorResponse is still offline")
    func wrappedTransportFailure() {
        // URLSession failures surface through the generated client wrapped in
        // an ErrorResponse rather than as a bare URLError. Reading only the
        // status would report "something went wrong" to someone who is simply
        // in a lift.
        let wrapped = ErrorResponse.error(-1, nil, nil, URLError(.notConnectedToInternet))
        #expect(WatchGuruClient.failure(for: wrapped) == .offline)
    }

    @Test("poster initials fall back rather than rendering an empty placeholder")
    func posterInitials() {
        #expect(PosterView.initials(of: "Breaking Bad") == "BB")
        #expect(PosterView.initials(of: "Severance") == "S")
        #expect(PosterView.initials(of: "   ") == "?")
    }
}
