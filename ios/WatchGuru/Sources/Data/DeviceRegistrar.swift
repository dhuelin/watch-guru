import Foundation

/// Tells the backend where to send this user's pushes, and when to stop.
///
/// Thin on purpose. The interesting parts are elsewhere: getting a token is
/// `PushTokens`, and deciding what to send is the settings screen. This is the
/// one place that knows both a token and the client, so it is the one place
/// that can join them up.
///
/// Takes the token as an argument rather than reading `PushTokens` itself, and
/// so carries no actor isolation. That matters because sign-in and sign-out
/// reach this from `@Sendable` hooks: a main-actor registrar would have to be
/// hopped to from each of them, for no benefit — reading one string on the main
/// actor is the caller's business, and this type's is the request.
struct DeviceRegistrar: Sendable {

    private let client: WatchGuruClient

    init(client: WatchGuruClient) {
        self.client = client
    }

    /// Registers this install.
    ///
    /// Best-effort: a failure here must not interrupt a launch or a sign-in,
    /// and the next launch tries again. Nothing the user can see depends on
    /// this particular attempt having worked.
    @discardableResult
    func register(token: String) async -> Bool {
        do {
            try await client.registerDevice(token: token)
            return true
        } catch {
            return false
        }
    }

    /// Forgets this install, giving up quickly if the network will not have it.
    ///
    /// Must run while the session still exists — it is an authenticated call
    /// about the caller's own device — which is why sign-out does this before
    /// clearing the token rather than after. On a shared phone it is the
    /// difference between the next user being notified about their own series
    /// and about the last user's.
    ///
    /// Bounded because sign-out waits for it. The access token cannot be
    /// cleared until this has had its chance, and `URLSession`'s own patience
    /// runs to a minute — far too long to leave a usable token in the Keychain
    /// of a phone somebody has just handed over.
    func unregister(token: String, within limit: Duration = .seconds(5)) async {
        let client = self.client
        await withTaskGroup(of: Void.self) { group in
            group.addTask { try? await client.unregisterDevice(token: token) }
            group.addTask { try? await Task.sleep(for: limit) }
            // Whichever finishes first wins; the other is abandoned.
            await group.next()
            group.cancelAll()
        }
    }
}
