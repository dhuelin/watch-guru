import SwiftUI

/// The four tabs.
///
/// A `TabView` with a `NavigationStack` per tab, which is what gives each tab
/// its own back stack — the behaviour iOS users expect, and the reason this is
/// not one stack shared across tabs.
struct RootView: View {

    @Environment(Session.self) private var session
    @Environment(\.scenePhase) private var scenePhase
    @State private var pending = 0

    var body: some View {
        VStack(spacing: 0) {
            // Only when there is something to say. A permanent "synced" badge
            // is noise; "3 changes waiting" is information, and it is the
            // honest answer to "did my taps on the train count?".
            if pending > 0 {
                PendingChangesBanner(count: pending)
            }
            tabs
        }
        // On every return to the foreground, not on a timer: the moment worth
        // trying is when the user picks the phone up, which is also when the
        // signal is most likely to have come back.
        .task(id: scenePhase) {
            guard scenePhase == .active else { return }
            _ = await session.offline.sync()
            pending = await session.offline.pendingCount()
        }
    }

    // `.tabItem`, not the `Tab` builder: that is iOS 18, and the deployment
    // target is 17. Everything else the app relies on -- @Observable,
    // ContentUnavailableView -- genuinely is iOS 17, so the floor stays.
    private var tabs: some View {
        TabView {
            NavigationStack { HomeView() }
                .tabItem { Label("Home", systemImage: "house") }
            NavigationStack { SearchView() }
                .tabItem { Label("Search", systemImage: "magnifyingglass") }
            NavigationStack { LibraryView() }
                .tabItem { Label("Library", systemImage: "books.vertical") }
            NavigationStack { ProfileView() }
                .tabItem { Label("Profile", systemImage: "person") }
        }
    }
}

/// "Some of what you did is not on the server yet."
///
/// Deliberately not an error. Nothing has gone wrong: the changes are stored
/// and will be sent. It exists so a user who marked three episodes underground
/// can see that the app knows about them.
private struct PendingChangesBanner: View {

    let count: Int

    var body: some View {
        Text(count == 1 ? "1 change waiting to sync" : "\(count) changes waiting to sync")
            .font(.footnote)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(Color.accentColor.opacity(0.12))
    }
}

#Preview {
    RootView()
        .environment(Session(tokens: InMemoryTokenStore(tokens: Tokens(
            accessToken: "preview",
            refreshToken: "preview",
            accessTokenExpiresAt: .distantFuture
        ))))
}
