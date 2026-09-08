import SwiftUI

/// The four tabs.
///
/// A `TabView` with a `NavigationStack` per tab, which is what gives each tab
/// its own back stack — the behaviour iOS users expect, and the reason this is
/// not one stack shared across tabs.
struct RootView: View {

    var body: some View {
        TabView {
            Tab("Home", systemImage: "house") {
                NavigationStack { HomeView() }
            }
            Tab("Search", systemImage: "magnifyingglass") {
                NavigationStack { SearchView() }
            }
            Tab("Library", systemImage: "books.vertical") {
                NavigationStack { LibraryView() }
            }
            Tab("Profile", systemImage: "person") {
                NavigationStack { ProfileView() }
            }
        }
    }
}

#Preview {
    RootView()
        .environment(Session(tokens: InMemoryTokenStore(token: "preview")))
}
