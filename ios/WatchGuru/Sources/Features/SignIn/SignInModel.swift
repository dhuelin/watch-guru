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
/// Known limitation: Apple's identity token expires in about an hour and is
/// used directly as the bearer token, so a long session ends in 401s rather
/// than a silent renewal. Fixing that means a refresh flow or a token exchange
/// on the backend; it is tracked separately rather than faked with a retry that
/// cannot succeed.
@Observable
@MainActor
final class SignInModel {

    private(set) var state: AuthState = .checking

    /// A failure that must not change the auth state — a delete that fails
    /// leaves the user signed in, so it cannot travel as `.failed`.
    var accountError: String?

    private let tokens: TokenStore
    private let client: WatchGuruClient

    init(tokens: TokenStore, client: WatchGuruClient) {
        self.tokens = tokens
        self.client = client
        state = tokens.token() == nil ? .signedOut : .signedIn
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
        tokens.save(token)

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
        tokens.clear()
        URLCache.shared.removeAllCachedResponses()
        state = .signedOut
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

    private static func format(_ name: PersonNameComponents) -> String? {
        let formatted = PersonNameComponentsFormatter.localizedString(from: name, style: .default)
        return formatted.isEmpty ? nil : formatted
    }
}
