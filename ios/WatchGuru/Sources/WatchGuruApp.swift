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
        let sessions = SessionClient(baseURL: baseURL)
        let client = WatchGuruClient(baseURL: baseURL, tokens: tokens, sessions: sessions)
        self.client = client
        let signIn = SignInModel(tokens: tokens, client: client, sessions: sessions)
        self.signIn = signIn

        // When the refresh token is refused there is nothing left to try, and
        // the app has to say so rather than 401 quietly on every screen. The
        // client discovers this deep inside a request, so it needs a way back
        // out to the state the UI is gated on.
        Task { await client.setOnSessionLost { @Sendable in
            Task { @MainActor in signIn.sessionExpired() }
        } }
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
