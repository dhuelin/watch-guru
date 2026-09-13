import SwiftUI
import WatchGuruAPI

/// Connecting Trakt.
///
/// The authorisation opens in a browser rather than a web view, so the user can
/// see the address bar says trakt.tv before typing a password there. That also
/// means this screen loses sight of them for a moment, so it reloads when the
/// app becomes active again.
struct TraktView: View {

    @Environment(Session.self) private var session
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @State private var model: TraktModel?

    var body: some View {
        Group {
            if let model {
                switch model.state {
                case .loading:
                    ProgressView()
                case .failed(let failure):
                    FailureView(failure: failure) { Task { await model.load() } }
                case .content(let status), .refreshing(let status):
                    form(status: status, model: model)
                case .empty:
                    EmptyView()
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Trakt")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil {
                let created = TraktModel(client: session.client)
                model = created
                await created.load()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            // Back from the browser: what changed happened elsewhere, so the
            // only way to know is to ask.
            if phase == .active, let model {
                Task { await model.load() }
            }
        }
        .alert("That did not work", isPresented: Binding(
            get: { model?.failureMessage != nil },
            set: { if !$0 { model?.failureMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(model?.failureMessage ?? "")
        }
    }

    private func form(status: TraktStatusResponse, model: TraktModel) -> some View {
        List {
            Section {
                Text(
                    """
                    If you already scrobble to Trakt — from Plex, Kodi, Infuse or \
                    anywhere else — connecting it brings all of that here.
                    """
                )
                .font(.subheadline)
            } footer: {
                Text("You approve it on trakt.tv in your browser; Watch Guru never sees your "
                    + "Trakt password.")
            }

            Section {
                StatusRow(status: status)
                if let result = model.lastSync {
                    LastSyncRow(result: result)
                }
            }

            Section {
                if status.connected {
                    Button("Sync now") {
                        Task { await model.syncNow() }
                    }
                    .disabled(model.isBusy)
                    Button("Disconnect", role: .destructive) {
                        Task { await model.disconnect() }
                    }
                    .disabled(model.isBusy)
                } else {
                    Button("Connect Trakt") {
                        Task {
                            if let url = await model.authorizationURL() {
                                openURL(url)
                            }
                        }
                    }
                    .disabled(model.isBusy)
                }
            } footer: {
                Text(status.connected
                    ? "Syncing also runs by itself every couple of hours."
                    : "This opens trakt.tv in your browser. Come back here when it says you can "
                        + "close the page.")
            }

            if !status.recentRuns.isEmpty {
                Section("Recent syncs") {
                    ForEach(status.recentRuns, id: \.id) { run in
                        SyncRunRow(run: run)
                    }
                }
            }

            Section {
                Text(
                    """
                    Watch Guru holds the access Trakt gives it, encrypted, and never your \
                    password. Disconnecting revokes it at Trakt as well as here.
                    """
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }
}

private struct StatusRow: View {

    let status: TraktStatusResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label {
                Text(ConnectionActivity.summary(
                    connected: status.connected,
                    account: status.account,
                    waiting: "Approve the connection in your browser, then come back here."))
            } icon: {
                Image(systemName: icon).foregroundStyle(tint)
            }
            if let lastSyncAt = status.account?.lastSyncAt {
                Text("Last synced \(lastSyncAt.formatted(.relative(presentation: .named)))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var health: ConnectionActivity.Health {
        ConnectionActivity.health(connected: status.connected, account: status.account)
    }

    private var icon: String {
        switch health {
        case .notConnected: "link.badge.plus"
        case .waiting: "clock"
        case .working: "checkmark.circle"
        case .needsAttention: "exclamationmark.triangle"
        }
    }

    private var tint: Color {
        switch health {
        case .needsAttention: .orange
        case .working: .green
        default: .secondary
        }
    }
}

/// What the sync the user just asked for did.
///
/// Anything unmatched is named rather than counted: "3 items could not be
/// matched" is not something a person can act on, and a title they recognise is.
private struct LastSyncRow: View {

    let result: SyncResultResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(result.imported == 1
                ? "Recorded 1 viewing."
                : "Recorded \(result.imported) viewings.")
            if !result.problems.isEmpty {
                Text("Could not be matched: \(result.problems.joined(separator: ", "))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct SyncRunRow: View {

    let run: SyncRunResponse

    var body: some View {
        HStack(alignment: .top) {
            Text(ConnectionActivity.describe(run))
                .font(.subheadline)
            Spacer(minLength: 12)
            Text(run.startedAt.formatted(.relative(presentation: .named)))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
