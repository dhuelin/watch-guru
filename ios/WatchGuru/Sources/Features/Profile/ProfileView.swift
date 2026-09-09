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
    @State private var confirmingDelete = false

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
                        NavigationLink("Viewing history") {
                            HistoryView()
                        }
                    }
                    Section {
                        Button("Sign out") { session.signIn.signOut() }
                        Button("Delete account", role: .destructive) {
                            confirmingDelete = true
                        }
                    } footer: {
                        Text("Deleting your account removes your library, progress and history permanently.")
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
        // Irreversible and cascading, so it asks first.
        .confirmationDialog(
            "Delete your account?",
            isPresented: $confirmingDelete,
            titleVisibility: .visible
        ) {
            Button("Delete account", role: .destructive) {
                Task { await session.signIn.deleteAccount() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes your library, progress and history permanently. It cannot be undone.")
        }
        // A delete that fails leaves the account intact and the user signed in,
        // so it is reported here rather than as a sign-in failure — which would
        // drop them onto the sign-in screen with their account still there.
        .alert(
            "Couldn't delete your account",
            isPresented: Binding(
                get: { session.signIn.accountError != nil },
                set: { if !$0 { session.signIn.accountError = nil } }
            )
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(session.signIn.accountError ?? "")
        }
    }

    private func load() async {
        do {
            state = .content(try await session.client.profile())
        } catch {
            state = .failed(error)
        }
    }
}
