import SwiftUI

/// Up Next: the screen that answers "what do I put on now".
///
/// Not built yet, and deliberately left as a placeholder rather than assembled
/// from the endpoints that exist. Doing it properly needs a single
/// `GET /api/v1/me/up-next` returning the next unwatched episode per
/// in-progress series; today that would be one `/progress` call per series, on
/// the screen the app opens to. The rule for what counts as "next" also belongs
/// on the server, where both apps share it, rather than implemented twice.
///
/// Tracked by issue #13, which proposes exactly that endpoint.
struct HomeView: View {
    var body: some View {
        ContentUnavailableView(
            "Up Next is coming soon",
            systemImage: "play.tv",
            description: Text("In the meantime, your library has everything you're tracking.")
        )
        .navigationTitle("Home")
    }
}
