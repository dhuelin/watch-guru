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
///
/// `@MainActor` because it builds and wires `SignInModel`, which is main-actor
/// isolated, and because every view reads it on the main actor anyway. Without
/// it, complete concurrency checking rejects the init.
@Observable
@MainActor
final class Session {

    let client: WatchGuruClient
    let offline: OfflineClient
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

        let store = FileOfflineStore()
        let offline = OfflineClient(
            client: client,
            cache: SnapshotCache(store: store),
            queue: MutationQueue(store: store)
        )
        self.offline = offline
        let signIn = SignInModel(tokens: tokens, client: client, sessions: sessions)
        self.signIn = signIn

        // When the refresh token is refused there is nothing left to try, and
        // the app has to say so rather than 401 quietly on every screen. The
        // client discovers this deep inside a request, so it needs a way back
        // out to the state the UI is gated on.
        Task { await client.setOnSessionLost { @Sendable in
            Task { @MainActor in signIn.sessionExpired() }
        } }

        // Signing out must leave nothing of the previous user behind. The
        // cached library is their watch history in all but name, and the queue
        // may hold changes that would otherwise be replayed against whoever
        // signs in next -- attributing one person's viewing to another.
        signIn.onSignedOut = { @Sendable in
            Task { await offline.clearLocalData() }
        }
    }

    /// The simulator reaches a backend on the developer's machine at
    /// `localhost`; a device does not, which is the usual first surprise.
    nonisolated static var defaultBaseURL: URL {
        #if DEBUG
        URL(string: "http://localhost:8080")!
        #else
        URL(string: "https://api.watch-guru.example")!
        #endif
    }
}
