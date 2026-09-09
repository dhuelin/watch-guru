import SwiftUI

@main
struct WatchGuruApp: App {

    @State private var session = Session()

    var body: some Scene {
        WindowGroup {
            // Everything behind the token. Showing the tab bar first and then
            // 401-ing on every screen would be a worse first launch than a
            // sign-in screen.
            switch session.signIn.state {
            case .signedIn:
                RootView()
                    .environment(session)
            case .checking:
                ProgressView()
            case .signedOut, .signingIn, .failed:
                SignInView(model: session.signIn)
            }
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
    let tokens: TokenStore
    let signIn: SignInModel

    init(
        baseURL: URL = Session.defaultBaseURL,
        tokens: TokenStore = KeychainTokenStore()
    ) {
        self.tokens = tokens
        let client = WatchGuruClient(baseURL: baseURL, tokens: tokens)
        self.client = client
        self.signIn = SignInModel(tokens: tokens, client: client)
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
