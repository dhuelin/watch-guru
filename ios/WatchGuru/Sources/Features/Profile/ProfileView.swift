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
    @State private var regionFailed = false
    @State private var isSavingRegion = false

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
                    Section {
                        // The region is the one preference here that changes
                        // what the app shows, so it is a control rather than a
                        // line of text. The rest are read-only for now.
                        NavigationLink {
                            RegionPickerView(currentRegion: user.region) { code in
                                Task { await setRegion(code) }
                            }
                        } label: {
                            LabeledContent("Region", value: Regions.displayName(user.region))
                        }
                        // Closed while a change is in flight. Two picks in a
                        // row would be two PATCHes that can answer out of
                        // order, leaving the server, this screen and the
                        // reload counter disagreeing about the country.
                        .disabled(isSavingRegion)
                        LabeledContent("Language", value: user.language)
                        LabeledContent("Time zone", value: user.timeZone)
                    } header: {
                        Text("Preferences")
                    } footer: {
                        Text("Streaming offers are shown for this country.")
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
        .alert("Couldn't change your region", isPresented: $regionFailed) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Your region is unchanged. Please try again.")
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

    /// Changes which country's streaming offers the user is shown.
    ///
    /// Deliberately not optimistic. This screen is the only place the region
    /// is visible, so showing the new one before the server has taken it would
    /// leave someone believing they had changed it when they had not — and the
    /// symptom, offers for the wrong country, is exactly what they were trying
    /// to fix. What lands in the state is the server's own answer.
    private func setRegion(_ code: String) async {
        guard !isSavingRegion,
              let user = state.value,
              user.region.caseInsensitiveCompare(code) != .orderedSame else { return }

        isSavingRegion = true
        defer { isSavingRegion = false }

        state = .refreshing(user)
        do {
            // Hoisted rather than inlined into the case, as in load() below.
            let updated = try await session.client.updateProfile(UpdateProfile(region: code))
            state = .content(updated)
            // Any title screen still on a navigation stack is now showing
            // offers for the country the user just left.
            session.profileRevision += 1
        } catch {
            // The profile itself is still valid, so the screen keeps it: a
            // rejected PATCH is no reason to replace the sign-out button with
            // an error view.
            state = .content(user)
            regionFailed = true
        }
    }

    private func load() async {
        do {
            // See TitleDetailModel.load: inlining the await into the case
            // defeats the region based isolation checker.
            let profile = try await session.client.profile()
            state = .content(profile)
        } catch {
            state = .failed(error)
        }
    }
}
