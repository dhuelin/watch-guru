import Foundation
import Observation
import WatchGuruAPI

/// The notification settings screen.
///
/// Two switches with different scopes — one global, one per series — and the
/// global one wins. The server enforces that; this only has to show it, which
/// is why turning the global switch off disables the list rather than hiding
/// it: somebody who turns everything off and back on should find their
/// per-series choices where they left them.
///
/// Every change is sent at once and the server's answer replaces local state.
/// No save button, and no optimistic flip either: a switch that springs back is
/// confusing, but a switch that lies is worse.
@Observable
@MainActor
final class NotificationSettingsModel {

    private let client: WatchGuruClient
    private let devices: DeviceRegistrar
    private let push: PushTokens

    var state: ViewState<NotificationSettingsResponse> = .loading
    var isBusy = false
    var failureMessage: String?

    /// Whether iOS will let this app show anything at all.
    ///
    /// Read from the system on each appearance rather than remembered: it can
    /// be revoked in the Settings app while Watch Guru is in the background,
    /// and a cached answer would have this screen contradict the system.
    var isAuthorized = false

    /// True once the user has been asked, so a second tap goes to Settings.
    ///
    /// iOS shows its prompt once per install. Asking again does nothing
    /// visible, which reads as a broken button.
    var hasBeenAsked = false

    init(client: WatchGuruClient, push: PushTokens = .shared) {
        self.client = client
        self.devices = DeviceRegistrar(client: client)
        self.push = push
    }

    /// Why no push can arrive, if something is in the way.
    ///
    /// Three things must all be true and they fail independently: iOS has to
    /// permit notifications, APNs has to have issued a token the backend knows,
    /// and the switches have to be on. A screen showing only the switches would
    /// leave somebody toggling a setting that could never do anything.
    var obstacle: Obstacle? {
        Self.obstacle(
            enabled: state.value?.enabled ?? false,
            authorized: isAuthorized,
            token: push.token,
            registeredDevices: state.value?.registeredDevices ?? 0,
            failure: push.failure
        )
    }

    /// The same decision, as a function of its inputs.
    ///
    /// Pulled out so the ordering can be tested: `PushTokens` is a singleton
    /// the test target cannot substitute, and the order is the part worth
    /// checking. Reporting "not registered" to somebody who has not been asked
    /// for permission sends them looking in the wrong place.
    static func obstacle(
        enabled: Bool,
        authorized: Bool,
        token: String?,
        registeredDevices: Int,
        failure: String?
    ) -> Obstacle? {
        // Nothing is in the way of a thing the user has switched off.
        guard enabled else { return nil }
        // Most specific first, and in the order the user would have to fix
        // them: permission, then a token, then the backend knowing about it.
        if !authorized { return .notAuthorized }
        if token == nil { return .noToken(reason: failure) }
        if registeredDevices == 0 { return .notRegistered }
        return nil
    }

    enum Obstacle: Equatable {
        /// iOS has not been asked, or was told no.
        case notAuthorized
        /// APNs issued nothing. Usual in development: see #30.
        case noToken(reason: String?)
        /// A token exists but the backend has not been told about it.
        case notRegistered
    }

    func load() async {
        isAuthorized = await push.authorized()
        if let current = state.value {
            state = .refreshing(current)
        } else {
            state = .loading
        }
        do {
            state = .content(try await client.notificationSettings())
        } catch {
            state = .failed(error)
        }
    }

    func setEnabled(_ enabled: Bool) async {
        await change { try await self.client.setNotificationsEnabled(enabled) }
    }

    func setSeries(titleId: Int64, newEpisodes: Bool) async {
        await change {
            try await self.client.setSeriesNotification(titleId: titleId, newEpisodes: newEpisodes)
        }
    }

    /// Asks iOS, then tells the backend where to send.
    ///
    /// Granting is the moment registration becomes possible, rather than the
    /// next launch — which is when somebody who just tapped "Allow" would
    /// otherwise have to come back.
    func requestAuthorization() async {
        hasBeenAsked = true
        isAuthorized = await push.requestAuthorization()
        guard isAuthorized, let token = push.token else { return }
        if await devices.register(token: token) { await load() }
    }

    private func change(
        _ request: @escaping () async throws -> NotificationSettingsResponse
    ) async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        do {
            // The server's whole answer rather than the value just sent, so the
            // global switch and the list cannot drift apart.
            state = .content(try await request())
        } catch {
            failureMessage = "That could not be saved. Please try again."
        }
    }
}
