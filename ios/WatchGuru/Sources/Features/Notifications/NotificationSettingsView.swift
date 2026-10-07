import SwiftUI
import UIKit
import WatchGuruAPI

/// Whether to be told about new episodes, and of what.
///
/// The obstacle note above the list is the point of this screen as much as the
/// switches are. Push has three independent prerequisites, and when one is
/// missing the switches keep working and nothing ever arrives — so the screen
/// says which one it is rather than letting the user conclude the app is
/// broken.
struct NotificationSettingsView: View {

    @Environment(Session.self) private var session
    @Environment(\.openURL) private var openURL
    @State private var model: NotificationSettingsModel?

    var body: some View {
        Group {
            if let model {
                switch model.state {
                case .loading:
                    ProgressView()
                case .failed(let failure):
                    FailureView(failure: failure) { Task { await model.load() } }
                case .content(let settings), .refreshing(let settings):
                    form(settings: settings, model: model)
                case .empty:
                    EmptyView()
                }
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if model == nil {
                model = NotificationSettingsModel(client: session.client)
            }
            // On every appearance, not only the first: permission can be
            // revoked in the Settings app while this screen is in the
            // background.
            await model?.load()
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

    private func form(
        settings: NotificationSettingsResponse,
        model: NotificationSettingsModel
    ) -> some View {
        List {
            Section {
                Toggle("New episodes", isOn: Binding(
                    get: { settings.enabled },
                    set: { enabled in Task { await model.setEnabled(enabled) } }
                ))
                .disabled(model.isBusy)
            } footer: {
                Text("Told when a new episode of something you are watching airs.")
            }

            if let obstacle = model.obstacle {
                Section {
                    ObstacleNote(obstacle: obstacle, model: model, openURL: openURL)
                }
            }

            // Muted, not "all series". The server stores a row only when the
            // user has said no — switching one back on deletes it — so this
            // list is the exceptions, and every series without a row notifies
            // by default. Calling it "Series" would make an empty list read as
            // "you have no series", which is a different and untrue thing.
            Section {
                if settings.series.isEmpty {
                    Text("You have not muted anything. Every series in your library can notify you.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(settings.series, id: \.titleId) { series in
                        Toggle(series.titleName, isOn: Binding(
                            get: { series.newEpisodes },
                            set: { on in
                                Task { await model.setSeries(titleId: series.titleId, newEpisodes: on) }
                            }
                        ))
                        // Disabled rather than hidden when the global switch is
                        // off: these choices still exist and come back.
                        .disabled(model.isBusy || !settings.enabled)
                    }
                }
            } header: {
                Text("Muted series")
            } footer: {
                Text("Turn a series off on its own screen. Switching one back on here removes it from this list.")
            }
        }
    }
}

/// What is stopping a notification from arriving, and what to do about it.
private struct ObstacleNote: View {

    let obstacle: NotificationSettingsModel.Obstacle
    let model: NotificationSettingsModel
    let openURL: OpenURLAction

    var body: some View {
        switch obstacle {
        case .notAuthorized:
            VStack(alignment: .leading, spacing: 8) {
                Text("iOS is not allowing Watch Guru to notify you.")
                // Asking twice does nothing visible, so once the system prompt
                // has been shown the only honest button is one to Settings.
                if model.hasBeenAsked {
                    Button("Open Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            openURL(url)
                        }
                    }
                } else {
                    Button("Allow notifications") {
                        Task { await model.requestAuthorization() }
                    }
                }
            }

        case .noToken(let reason):
            VStack(alignment: .leading, spacing: 4) {
                Text("This device has no push token, so nothing can be delivered to it.")
                if let reason {
                    Text(reason)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

        case .notRegistered:
            Text("This device is not registered for notifications yet.")
        }
    }
}
