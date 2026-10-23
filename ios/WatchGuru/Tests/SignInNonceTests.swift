import Testing
@testable import WatchGuru

struct SignInNonceTests {

    @Test func hashIsLowercaseHexSHA256() {
        // The backend compares against exactly this encoding.
        #expect(SignInNonce.sha256Hex("abc")
            == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test func everyNonceIsFresh() {
        let first = SignInNonce.make()
        let second = SignInNonce.make()
        #expect(first != second)
        #expect(first.raw.count >= 16)
        #expect(!first.raw.contains("="))
    }
}
