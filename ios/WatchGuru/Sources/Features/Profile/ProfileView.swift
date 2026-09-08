import SwiftUI
import WatchGuruAPI

/// The signed-in user's profile.
///
/// Sign-in itself is issue #15: the app expects a token to be present already,
/// so every call returns 401 until that lands. Region and language are shown
/// because they are not cosmetic — region decides which streaming offers
/// appear, and language is passed through to TMDB.
struct ProfileView: View {

    @Environment(Session.self) private var session
    @State private var state: ViewState<UserResponse> = .loading

    var body: some View {
        Group {
            switch state {
            case .content(let user), .refreshing(let user):
                List {
                    Section {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(user.displayName).font(.headline)
                            Text(user.email).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    Section("Preferences") {
                        LabeledContent("Region", value: user.region)
                        LabeledContent("Language", value: user.language)
                        LabeledContent("Time zone", value: user.timeZone)
                    }
                    Section {
                        Text("This product uses the TMDB API but is not endorsed or certified by TMDB.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            case .failed(let failure):
                FailureView(failure: failure) { Task { await load() } }
            case .loading:
                ProgressView()
            case .empty:
                EmptyView()
            }
        }
        .navigationTitle("Profile")
        .task { await load() }
    }

    private func load() async {
        do {
            state = .content(try await session.client.profile())
        } catch {
            state = .failed(error)
        }
    }
}
