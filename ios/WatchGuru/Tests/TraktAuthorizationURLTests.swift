import Foundation
import Testing
@testable import WatchGuru

/// The app opens the browser only on Trakt's own sign-in page.
struct TraktAuthorizationURLTests {

    @Test("Trakt's authorize page is accepted")
    func traktIsAccepted() throws {
        let url = try #require(URL(string: "https://trakt.tv/oauth/authorize?client_id=x&state=y"))
        #expect(TraktModel.isTraktAuthorizationURL(url))
    }

    @Test("anything else is refused", arguments: [
        "http://trakt.tv/oauth/authorize",
        "https://trakt.tv.example.com/oauth/authorize",
        "https://eviltrakt.tv/oauth/authorize",
        "watchguru://trakt.tv",
    ])
    func othersAreRefused(_ string: String) throws {
        let url = try #require(URL(string: string))
        #expect(!TraktModel.isTraktAuthorizationURL(url))
    }
}
