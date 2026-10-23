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

/// Only the backend's redirect to the app's own scheme completes a connection.
struct TraktCallbackURLTests {

    @Test func readsCodeAndState() throws {
        let url = try #require(URL(string: "watchguru://trakt/callback?code=abc&state=s%2Ft"))
        let parameters = try #require(TraktModel.callbackParameters(url))
        #expect(parameters.code == "abc")
        #expect(parameters.state == "s/t")
    }

    @Test func refusesAnythingElse() throws {
        for value in [
            "https://trakt.tv/callback?code=a&state=b",
            "watchguru://other/callback?code=a&state=b",
            "watchguru://trakt/callback?code=a",
            "watchguru://trakt/callback?code=&state=b",
        ] {
            let url = try #require(URL(string: value))
            #expect(TraktModel.callbackParameters(url) == nil)
        }
    }
}
