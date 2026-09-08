import Foundation
import Security

/// Where the OIDC token lives.
///
/// A protocol so previews and tests can supply one without touching the
/// Keychain.
protocol TokenStore: Sendable {
    func token() -> String?
    func save(_ token: String)
    func clear()
}

/// The real one. Keychain, never `UserDefaults`: this value is the user's
/// whole account, and `UserDefaults` is plain plist in the app container.
struct KeychainTokenStore: TokenStore {

    private let service = "dev.dhuelin.watchguru"
    private let account = "access-token"

    func token() -> String? {
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
        return String(data: data, encoding: .utf8)
    }

    func save(_ token: String) {
        // Delete first: SecItemAdd fails with errSecDuplicateItem rather than
        // overwriting, and a silent failure here means the user appears signed
        // out on next launch.
        clear()

        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: Data(token.utf8),
            // Not synced to iCloud, and unavailable until the device has been
            // unlocked once after boot.
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstThisDeviceOnly,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// For previews and tests.
struct InMemoryTokenStore: TokenStore {
    private final class Box: @unchecked Sendable {
        var value: String?
        init(_ value: String?) { self.value = value }
    }
    private let box: Box

    init(token: String? = nil) { box = Box(token) }

    func token() -> String? { box.value }
    func save(_ token: String) { box.value = token }
    func clear() { box.value = nil }
}
