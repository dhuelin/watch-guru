import CryptoKit
import Foundation
import Security

/// The one-time value that ties a provider sign-in to this exchange.
///
/// The provider is given only ``hashed`` and puts it in the identity token; the
/// raw value goes to Watch Guru alongside the token and nowhere else. A token
/// that leaks without its nonce cannot start a session, and the backend accepts
/// each nonce once, so a token cannot be replayed even with it.
struct SignInNonce: Sendable, Equatable {

    let raw: String

    /// What the provider is handed: SHA-256 of ``raw``, as lowercase hex. The
    /// backend computes the same and compares it with the token's claim.
    var hashed: String { Self.sha256Hex(raw) }

    /// 32 random bytes, base64url without padding.
    static func make() -> SignInNonce {
        var bytes = [UInt8](repeating: 0, count: 32)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        precondition(status == errSecSuccess, "No system randomness for a sign-in nonce")
        let encoded = Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return SignInNonce(raw: encoded)
    }

    static func sha256Hex(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}
