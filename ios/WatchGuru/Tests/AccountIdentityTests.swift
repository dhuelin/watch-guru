import Foundation
import Testing
@testable import WatchGuru

/// What the profile shows as the person's identity, and how it names their
/// settings.
struct AccountIdentityTests {

    @Test("a synthetic relay address is not shown as the user's email")
    func hidesRelayAddresses() {
        #expect(AccountIdentity.visibleEmail(
            "9732794a2c0607a589e5d464f9c460d7@users.noreply.watch-guru.invalid") == nil)
        #expect(AccountIdentity.visibleEmail("someone@example.invalid") == nil)
        #expect(AccountIdentity.visibleEmail("bounce@no-reply.example.com") == nil)
    }

    @Test("a real address is shown as it is")
    func showsRealAddresses() {
        #expect(AccountIdentity.visibleEmail("ada@example.com") == "ada@example.com")
        #expect(AccountIdentity.visibleEmail("  ada@example.com ") == "ada@example.com")
    }

    @Test("an empty address shows nothing rather than a blank line")
    func hidesEmpty() {
        #expect(AccountIdentity.visibleEmail("") == nil)
    }

    @Test("a language tag is named in the reader's language")
    func namesLanguage() {
        let name = AccountIdentity.languageName("en-US", locale: Locale(identifier: "en_US"))
        #expect(name.hasPrefix("English"))
    }

    @Test("a time zone keeps its identifier beside its name")
    func namesTimeZone() {
        let name = AccountIdentity.timeZoneName("Europe/Berlin", locale: Locale(identifier: "en_US"))
        #expect(name.contains("Europe/Berlin"))
        #expect(name != "Europe/Berlin")
    }

    @Test("an unknown time zone falls back to what the server sent")
    func unknownTimeZone() {
        #expect(AccountIdentity.timeZoneName("Mars/Olympus") == "Mars/Olympus")
    }
}
