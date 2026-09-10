import AuthenticationServices
import Foundation
import Observation
import WatchGuruAPI

/// Where the app stands with respect to having a usable token.
enum AuthState: Equatable {
    /// Deciding, on launch, whether a stored token exists.
    case checking
    case signedOut
    case signingIn
    case signedIn
    case failed(String)
}

/// Sign in with Apple, and sign out.
///
/// The backend accepts the provider's identity token directly as a bearer token
/// — there is no exchange step and no registration call. An account comes into
/// existence the first time a valid token arrives.
///
/// The model does not run the authorisation itself: `SignInWithAppleButton`
/// already presents its own `ASAuthorizationController`, so driving a second
/// one here would prompt the user twice. It takes the button's result instead.
///
/// Apple's identity token is exchanged for a session this app can renew
/// silently (#26); it is never stored. The renewal itself is not here — it
/// happens inside ``WatchGuruClient``, on the server's 401, so every call
/// benefits rather than only the ones a screen remembered to guard.
@Observable
@MainActor
final class SignInModel {

    private(set) var state: AuthState = .checking

    /// A failure that must not change the auth state — a delete that fails
    /// leaves the user signed in, so it cannot travel as `.failed`.
    var accountError: String?

    /// Cleared local data belongs to whoever was signed in. Set by ``Session``.
    var onSignedOut: (@Sendable () -> Void)?

    private let tokens: TokenStore
    private let client: WatchGuruClient
    private let sessions: SessionClient

    init(tokens: TokenStore, client: WatchGuruClient, sessions: SessionClient) {
        self.tokens = tokens
        self.client = client
        self.sessions = sessions
        state = tokens.tokens() == nil ? .signedOut : .signedIn
    }

    /// Handles the result handed back by `SignInWithAppleButton`.
    func complete(_ result: Result<ASAuthorization, Error>) async {
        switch result {
        case .success(let authorization):
            await store(authorization)

        case .failure(let error as ASAuthorizationError) where error.code == .canceled:
            // Backing out of the sheet is not a failure worth showing.
            state = .signedOut

        case .failure(let error):
            state = .failed(error.localizedDescription)
        }
    }

    private func store(_ authorization: ASAuthorization) async {
        guard
            let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
            let data = credential.identityToken,
            let token = String(data: data, encoding: .utf8)
        else {
            state = .failed("Apple did not return an identity token.")
            return
        }

        state = .signingIn

        // Trade Apple's token for one of ours. Apple's lasts about an hour and
        // cannot be renewed without putting the sheet in front of the user
        // again; what this stores refreshes silently for thirty days.
        do {
            tokens.save(try await sessions.exchange(providerToken: token))
        } catch {
            // Apple accepted the user, we did not. Almost always a
            // configuration mismatch — the bundle id is not in the backend's
            // audience list — so the message says the session failed rather
            // than blaming the sign-in.
            state = .failed("Signed in with Apple, but Watch Guru couldn't start a session. Please try again.")
            return
        }

        // Apple returns the user's name ONLY in this first authorisation
        // response, and never in the token or on any later sign-in. If it is
        // not captured and sent now, the account keeps its placeholder display
        // name permanently — there is no way to ask Apple again.
        //
        // A failure here is deliberately not fatal: a wrong display name is a
        // far smaller problem than refusing a sign-in that otherwise worked.
        if let name = credential.fullName, let formatted = Self.format(name) {
            _ = try? await client.updateProfile(UpdateProfile(displayName: formatted))
        }

        state = .signedIn
    }

    /// Clears the token, and everything derived from it.
    ///
    /// A shared device must not leak the previous user's watch history, so this
    /// is not only about the token.
    func signOut() {
        let refreshToken = tokens.tokens()?.refreshToken
        tokens.clear()
        URLCache.shared.removeAllCachedResponses()
        onSignedOut?()
        state = .signedOut

        // Revoking server-side is best-effort and deliberately after the local
        // clear: a user who taps sign out on a plane must still be signed out.
        // The refresh token expires on its own if this never reaches us.
        if let refreshToken {
            Task { [sessions] in await sessions.logout(refreshToken: refreshToken) }
        }
    }

    /// Deletes the account server-side, then signs out.
    ///
    /// Required by App Review for any app offering account creation, and the
    /// backend cascades the delete rather than setting a flag.
    func deleteAccount() async {
        do {
            try await client.deleteAccount()
            signOut()
        } catch {
            accountError = error.message
        }
    }

    /// The session ended for a reason the user cannot retry away.
    ///
    /// Called from ``WatchGuruClient`` when a renewal is refused: the refresh
    /// token was revoked, expired, or the server detected it being reused. The
    /// tokens are already cleared by then; this is what moves the UI.
    func sessionExpired() {
        guard state == .signedIn else { return }
        state = .failed("Your session expired. Please sign in again.")
    }

    private static func format(_ name: PersonNameComponents) -> String? {
        let formatted = PersonNameComponentsFormatter.localizedString(from: name, style: .default)
        return formatted.isEmpty ? nil : formatted
    }
}
