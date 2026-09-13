import SwiftUI
import UIKit
import WatchGuruAPI

/// Connecting a media server.
///
/// One screen for Plex, Jellyfin and Emby. The setup is genuinely all the same:
/// the server calls us, so there is no login here and nothing to authorise.
/// What differs — its name, where its webhook settings live, whether it needs a
/// username — comes back from the server with the connection, so this screen
/// does not carry three sets of menu directions that would go stale.
struct MediaServerView: View {

    let service: String

    @Environment(Session.self) private var session
    @State private var model: MediaServerModel?
    @State private var accountName = ""

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
        .navigationTitle(model?.state.value?.serviceName ?? service.capitalized)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil {
                let created = MediaServerModel(client: session.client, service: service)
                model = created
                await created.load()
            }
        }
        .alert("That did not work", isPresented: Binding(
            get: { model?.actionFailed ?? false },
            set: { model?.actionFailed = $0 }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Check the username and try again.")
        }
    }

    private func form(status: MediaServerStatusResponse, model: MediaServerModel) -> some View {
        List {
            Section {
                Text(
                    """
                    Watching something on your own \(status.serviceName) server records it \
                    here, without marking anything. Your server calls Watch Guru, so there is \
                    no password to give: you paste a URL into its settings and it does the \
                    talking.
                    """
                )
                .font(.subheadline)
            }

            Section {
                StatusRow(status: status)
            }

            if let url = model.issuedURL {
                Section {
                    WebhookURL(url: url) {
                        model.issuedURL = nil
                        model.setUpHint = nil
                    }
                } header: {
                    Text("Your webhook URL")
                } footer: {
                    Text(model.setUpHint
                        ?? "Copy it now: it is shown once and cannot be shown again.")
                }
            }

            Section {
                if !status.connected {
                    TextField(
                        status.requiresAccountName
                            ? "\(status.serviceName) username"
                            : "\(status.serviceName) username (optional)",
                        text: $accountName)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Button(status.connected ? "Issue a new URL" : "Connect") {
                    Task { await model.connect(accountName: accountName) }
                }
                .disabled(model.isBusy || (status.requiresAccountName
                    && !status.connected
                    && accountName.trimmingCharacters(in: .whitespaces).isEmpty))

                if status.connected {
                    Button("Disconnect", role: .destructive) {
                        Task { await model.disconnect() }
                    }
                    .disabled(model.isBusy)
                }
            } footer: {
                Text(footer(for: status))
            }

            if !status.recentRuns.isEmpty {
                Section("Recent deliveries") {
                    ForEach(status.recentRuns, id: \.id) { run in
                        DeliveryRow(run: run)
                    }
                }
            }

            Section {
                Text(
                    """
                    Netflix, Prime Video and Apple TV cannot work this way: none of them \
                    offers any way to ask what you watched. Import their export instead — \
                    re-importing duplicates nothing.
                    """
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
        .onAppear {
            if accountName.isEmpty, let label = status.account?.accountLabel {
                accountName = label
            }
        }
    }

    private func footer(for status: MediaServerStatusResponse) -> String {
        if status.connected {
            return "A new URL retires the old one, which is the way out if yours ended up "
                + "somewhere public."
        }
        // Jellyfin and Emby fire one webhook for everybody on the server, so
        // the username is the only thing keeping a housemate's evening out of
        // this library.
        return status.requiresAccountName
            ? "Required: this server sends one webhook for everybody on it, so the username is "
                + "what separates your viewing from theirs."
            : "The username is only needed if other people watch things on the same server. "
                + "Without it, only viewing your server marks as yours is recorded."
    }
}

/// Whether deliveries are arriving, in one sentence and one timestamp.
private struct StatusRow: View {

    let status: MediaServerStatusResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label {
                Text(ConnectionActivity.summary(
                    connected: status.connected,
                    account: status.account,
                    waiting: "Waiting for your \(status.serviceName) server. Paste the webhook "
                        + "URL into it, then watch something."))
            } icon: {
                Image(systemName: icon).foregroundStyle(tint)
            }
            if let lastSyncAt = status.account?.lastSyncAt {
                Text("Last heard from your server "
                    + lastSyncAt.formatted(.relative(presentation: .named)))
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

/// The URL, once.
///
/// Shown with its warning rather than beside it: this is the only moment it
/// exists anywhere but in the server's settings, because we keep a hash of it
/// and nothing else.
private struct WebhookURL: View {

    let url: String
    let onDone: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(url)
                .font(.footnote.monospaced())
                .textSelection(.enabled)
                .lineLimit(3)
            HStack {
                Button("Copy") { UIPasteboard.general.string = url }
                    .buttonStyle(.borderedProminent)
                Button("Done", action: onDone)
            }
        }
    }
}

private struct DeliveryRow: View {

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
