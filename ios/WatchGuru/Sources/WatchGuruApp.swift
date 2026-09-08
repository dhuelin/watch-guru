import SwiftUI

@main
struct WatchGuruApp: App {

    @State private var session = Session()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
        }
    }
}

/// App-wide dependencies.
///
/// `@Observable` rather than a singleton so previews can hand a view a session
/// pointed at fixtures instead of the network.
@Observable
final class Session {

    let client: WatchGuruClient

    init(
        baseURL: URL = Session.defaultBaseURL,
        tokens: TokenStore = KeychainTokenStore()
    ) {
        client = WatchGuruClient(baseURL: baseURL, tokens: tokens)
    }

    /// The simulator reaches a backend on the developer's machine at
    /// `localhost`; a device does not, which is the usual first surprise.
    static var defaultBaseURL: URL {
        #if DEBUG
        URL(string: "http://localhost:8080")!
        #else
        URL(string: "https://api.watch-guru.example")!
        #endif
    }
}
