import SwiftUI
import UIKit
import UserNotifications

/// This install's APNs token, and whether the user has agreed to see anything.
///
/// Two separate things that look like one from the outside: iOS can grant
/// permission while APNs has issued no token, and it can issue a token the user
/// has told the system to show silently. The settings screen needs both to say
/// anything true, so both live here.
///
/// A singleton, which the rest of this app avoids, because the token does not
/// arrive through anything that can be injected: it comes back on the app
/// delegate, which UIKit constructs. One shared box is the smallest honest way
/// to get it from there to a view.
@MainActor
@Observable
final class PushTokens {

    static let shared = PushTokens()

    /// The token APNs issued, hex-encoded as the backend expects it.
    ///
    /// Nil is an ordinary answer, not an error: a simulator without a push
    /// capability, a build whose provisioning profile lacks Push Notifications
    /// (#30), and a registration that simply has not come back yet all read the
    /// same to every caller — nothing to register.
    private(set) var token: String?

    /// Why there is no token, when the system bothered to say.
    ///
    /// Kept only to be shown: a user whose notifications do not work is owed
    /// more than a switch that silently does nothing.
    private(set) var failure: String?

    private init() {}

    /// Whether the user has agreed to be notified at all.
    ///
    /// Asked rather than remembered, because it changes outside the app: the
    /// Settings app can revoke it while Watch Guru is in the background, and a
    /// cached answer would have this screen confidently contradict the system.
    func authorized() async -> Bool {
        // A continuation around the callback form rather than the `async`
        // property, so the only thing that crosses back here is a Bool.
        // `UNNotificationSettings` is a reference type from a framework that
        // predates concurrency, and whether it is Sendable depends on the SDK;
        // deciding the answer where the object already is sidesteps the
        // question entirely.
        await withCheckedContinuation { continuation in
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                continuation.resume(returning: settings.authorizationStatus == .authorized)
            }
        }
    }

    /// Asks, once, and registers with APNs if the answer is yes.
    ///
    /// iOS only ever shows this prompt once per install; afterwards the call
    /// returns the previous answer without showing anything, which is why the
    /// screen sends anyone who said no to the Settings app rather than asking
    /// again and appearing to do nothing.
    @discardableResult
    func requestAuthorization() async -> Bool {
        let granted = (try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge])) ?? false
        if granted { registerWithAPNs() }
        return granted
    }

    /// Asks APNs for a token. The answer arrives on the app delegate.
    func registerWithAPNs() {
        UIApplication.shared.registerForRemoteNotifications()
    }

    fileprivate func received(_ token: String) {
        self.token = token
        self.failure = nil
    }

    fileprivate func failed(_ message: String) {
        self.token = nil
        self.failure = message
    }
}

/// The only reason this app has an app delegate.
///
/// A device token cannot be awaited: UIKit hands it back through these two
/// methods and nowhere else, so SwiftUI needs an adaptor to receive it. Nothing
/// else belongs in here.
final class PushDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Hex, because that is what the backend stores and what every push
        // service expects. `deviceToken.description` looks similar and has not
        // been that since iOS 13.
        let hex = deviceToken.map { String(format: "%02x", $0) }.joined()
        Task { @MainActor in PushTokens.shared.received(hex) }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Routine in development: a simulator, or a profile without the Push
        // Notifications capability, fails here every launch. Recorded rather
        // than logged so the settings screen can say what happened.
        let message = error.localizedDescription
        Task { @MainActor in PushTokens.shared.failed(message) }
    }
}
