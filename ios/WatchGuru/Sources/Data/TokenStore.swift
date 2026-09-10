import Foundation
import Security

/// A session, as the client holds it.
///
/// Both tokens together, because they are only meaningful as a pair: an access
/// token with no refresh token is a session that dies in fifteen minutes, and a
/// refresh token with no access token means a round trip before every first
/// request.
struct Tokens: Codable, Equatable, Sendable {

    let accessToken: String
    let refreshToken: String

    /// When to stop using ``accessToken``.
    ///
    /// Derived from the server's `expiresIn` at the moment of receipt rather
    /// than taken as an absolute time: this device's clock may be wrong, and
    /// the server's is the one that decides.
    let accessTokenExpiresAt: Date

    /// A skew so a token that would expire mid-flight is renewed first, rather
    /// than producing a 401 the user waits through.
    static let expirySkew: TimeInterval = 30

    func isExpired(at now: Date = .now) -> Bool {
        accessTokenExpiresAt.addingTimeInterval(-Self.expirySkew) <= now
    }

    init(accessToken: String, refreshToken: String, accessTokenExpiresAt: Date) {
        self.accessToken = accessToken
        self.refreshToken = refreshToken
        self.accessTokenExpiresAt = accessTokenExpiresAt
    }

    /// Builds a session from what the API returns.
    ///
    /// `expiresIn` is seconds, converted against the moment the response
    /// arrived — see ``accessTokenExpiresAt``.
    init(accessToken: String, refreshToken: String, expiresIn: Int64, receivedAt: Date = .now) {
        self.init(
            accessToken: accessToken,
            refreshToken: refreshToken,
            accessTokenExpiresAt: receivedAt.addingTimeInterval(TimeInterval(expiresIn))
        )
    }
}

/// Where the session lives.
///
/// A protocol so previews and tests can supply one without touching the
/// Keychain.
protocol TokenStore: Sendable {
    func tokens() -> Tokens?
    func save(_ tokens: Tokens)
    func clear()
}

/// The real one. Keychain, never `UserDefaults`: these values are the user's
/// whole account — the refresh token especially, which is good for thirty days
/// rather than fifteen minutes, and `UserDefaults` is a plain plist in the app
/// container.
struct KeychainTokenStore: TokenStore {

    private let service = "dev.dhuelin.watchguru"

    /// One item holding the whole session, not one per token. Two items can be
    /// written half-way and read back inconsistent; one cannot.
    private let account = "session"

    func tokens() -> Tokens? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data else {
            return nil
        }
        return try? JSONDecoder().decode(Tokens.self, from: data)
    }

    func save(_ tokens: Tokens) {
        guard let data = try? JSONEncoder().encode(tokens) else { return }

        // Delete first: SecItemAdd fails with errSecDuplicateItem rather than
        // overwriting, and a silent failure here means the user appears signed
        // out on next launch.
        clear()

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            // Not synced to iCloud, and unavailable until the device has been
            // unlocked once after boot.
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    func clear() {
        for account in [account, Self.legacyAccount] {
            let query: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: account,
            ]
            SecItemDelete(query as CFDictionary)
        }
    }

    /// The item used when the app stored a bare provider token.
    ///
    /// Nothing reads it any more, so an upgraded install simply asks the user
    /// to sign in once — but it must still be deleted, or a provider token
    /// stays in the Keychain for ever, which is precisely the credential
    /// signing out exists to remove.
    private static let legacyAccount = "access-token"
}

/// For previews and tests.
struct InMemoryTokenStore: TokenStore {
    private final class Box: @unchecked Sendable {
        var value: Tokens?
        init(_ value: Tokens?) { self.value = value }
    }
    private let box: Box

    init(tokens: Tokens? = nil) { box = Box(tokens) }

    func tokens() -> Tokens? { box.value }
    func save(_ tokens: Tokens) { box.value = tokens }
    func clear() { box.value = nil }
}
