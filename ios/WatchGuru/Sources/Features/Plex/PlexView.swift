import SwiftUI
import UIKit
import WatchGuruAPI

/// Connecting a Plex server.
///
/// Mostly one instruction and one piece of text to copy, because that is
/// genuinely all the setup is: Plex calls us, so there is no login here and
/// nothing to authorise. What this screen has to do well is the part after
/// setup — saying whether deliveries are arriving, and what happened to the
/// last one.
struct PlexView: View {

    @Environment(Session.self) private var session
    @State private var model: PlexModel?
    @State private var plexUsername = ""

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
        .navigationTitle("Plex")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil {
                let created = PlexModel(client: session.client)
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
            Text("Try again in a moment.")
        }
    }

    private func form(status: PlexStatusResponse, model: PlexModel) -> some View {
        List {
            Section {
                Text(
                    """
                    Watching something on your own Plex server records it here, \
                    without marking anything. Plex calls Watch Guru, so there is no \
                    Plex password to give: you paste a URL into your Plex settings \
                    and your server does the talking.
                    """
                )
                .font(.subheadline)
            } footer: {
                Text("Plex Pass is required for webhooks.")
            }

            Section {
                StatusRow(status: status)
            }

            if let url = model.issuedURL {
                Section {
                    WebhookURL(url: url) { model.issuedURL = nil }
                } header: {
                    Text("Your webhook URL")
                } footer: {
                    Text(
                        "Copy it now: it is shown once and cannot be shown again. "
                            + "In Plex: Settings, Webhooks, Add Webhook, paste, save."
                    )
                }
            }

            Section {
                if !status.connected {
                    TextField("Plex username (optional)", text: $plexUsername)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Button(status.connected ? "Issue a new URL" : "Connect") {
                    Task { await model.connect(plexUsername: plexUsername) }
                }
                .disabled(model.isBusy)

                if status.connected {
                    Button("Disconnect", role: .destructive) {
                        Task { await model.disconnect() }
                    }
                    .disabled(model.isBusy)
                }
            } footer: {
                Text(
                    status.connected
                        ? "A new URL retires the old one, which is the way out if yours ended up somewhere public."
                        : "The username is only needed if other people watch things on the same server. Without it, only viewing your server marks as yours is recorded."
                )
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
                    Netflix, Prime Video and Apple TV cannot work this way: none of \
                    them offers any way to ask what you watched. Import their export \
                    instead — re-importing duplicates nothing.
                    """
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }
}

/// Whether deliveries are arriving, in one sentence and one timestamp.
private struct StatusRow: View {

    let status: PlexStatusResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label {
                Text(PlexActivity.summary(of: status))
            } icon: {
                Image(systemName: icon)
                    .foregroundStyle(tint)
            }
            if let lastSyncAt = status.account?.lastSyncAt {
                Text("Last heard from your server \(lastSyncAt.formatted(.relative(presentation: .named)))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var icon: String {
        switch PlexActivity.health(of: status) {
        case .notConnected: "link.badge.plus"
        case .waiting: "clock"
        case .working: "checkmark.circle"
        case .needsAttention: "exclamationmark.triangle"
        }
    }

    private var tint: Color {
        switch PlexActivity.health(of: status) {
        case .needsAttention: .orange
        case .working: .green
        default: .secondary
        }
    }
}

/// The URL, once.
///
/// Shown with its warning rather than beside it: this is the only moment it
/// exists anywhere but in Plex, because the server keeps a hash of it and
/// nothing else.
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
            Text(PlexActivity.describe(run))
                .font(.subheadline)
            Spacer(minLength: 12)
            Text(run.startedAt.formatted(.relative(presentation: .named)))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
