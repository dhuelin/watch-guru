import Foundation
import WatchGuruAPI

/// The token endpoints: exchanging a provider sign-in for a session, renewing
/// one, and ending one.
///
/// Separate from ``WatchGuruClient`` for two reasons. It is the only part of
/// the app that talks to the API without a session, and it must never carry the
/// retry-on-401 behaviour — a refusal from the refresh endpoint would otherwise
/// trigger a refresh, which fails, which triggers a refresh.
actor SessionClient {

    private let configuration: WatchGuruAPIAPIConfiguration

    init(baseURL: URL) {
        // Its own configuration, with no Authorization header ever set on it.
        self.configuration = WatchGuruAPIAPIConfiguration(basePath: baseURL.absoluteString)
    }

    /// Turns an Apple or Google identity token into a session.
    ///
    /// Also the account-creation call: the backend provisions a user the first
    /// time it sees a valid token from a trusted issuer.
    func exchange(providerToken: String) async throws(APIFailure) -> Tokens {
        try await run {
            try await AuthenticationAPI.createSession(
                exchangeToken: ExchangeToken(providerToken: providerToken),
                apiConfiguration: $0
            )
        }
    }

    /// Rotates a refresh token.
    ///
    /// The presented token stops working the moment this succeeds, so the
    /// result must be stored before the next call goes out.
    func refresh(refreshToken: String) async throws(APIFailure) -> Tokens {
        try await run {
            try await AuthenticationAPI.refreshSession(
                refreshSession: RefreshSession(refreshToken: refreshToken),
                apiConfiguration: $0
            )
        }
    }

    /// Ends the session server-side.
    ///
    /// Best-effort by design, and it does not throw: the local tokens are
    /// cleared whether or not this succeeds, because a user who taps sign out
    /// on a plane must still be signed out. The server-side token then expires
    /// on its own.
    func logout(refreshToken: String) async {
        _ = try? await AuthenticationAPI.endSession(
            refreshSession: RefreshSession(refreshToken: refreshToken),
            apiConfiguration: configuration
        )
    }

    private func run(
        _ operation: (WatchGuruAPIAPIConfiguration) async throws -> SessionResponse
    ) async throws(APIFailure) -> Tokens {
        do {
            let response = try await operation(configuration)
            // expiresIn is converted here, against the moment the response
            // arrived, rather than trusting refreshTokenExpiresAt against a
            // device clock that may be wrong.
            return Tokens(
                accessToken: response.accessToken,
                refreshToken: response.refreshToken,
                expiresIn: response.expiresIn
            )
        } catch let error as ErrorResponse {
            throw WatchGuruClient.failure(for: error)
        } catch let error as URLError {
            throw WatchGuruClient.failure(for: error)
        } catch {
            throw .unexpected(status: nil, message: error.localizedDescription)
        }
    }
}
