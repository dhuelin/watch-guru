import Foundation
import Testing
@testable import WatchGuru

/// Why no notification can arrive, and in what order to say so.
///
/// Push has three prerequisites that fail independently: iOS has to permit it,
/// APNs has to issue a token, and the backend has to know that token. The
/// screen can only report one thing at a time, so the order is a decision — and
/// the wrong order sends somebody to the Settings app when the real problem is
/// a server that has never heard of their phone, or the reverse.
@MainActor
struct NotificationObstacleTests {

    private typealias Model = NotificationSettingsModel

    @Test("nothing is in the way when notifications are switched off")
    func disabledHasNoObstacle() {
        // Not ".notAuthorized": the user turned this off, and telling them
        // their permissions are wrong would be answering a question nobody
        // asked.
        #expect(
            Model.obstacle(
                enabled: false, authorized: false, token: nil,
                registeredDevices: 0, failure: nil
            ) == nil
        )
    }

    @Test("permission comes first, even when nothing else is in place either")
    func permissionWins() {
        // All three are wrong here. Permission is the one the user can act on
        // and the one the others depend on, so it is the one to say.
        #expect(
            Model.obstacle(
                enabled: true, authorized: false, token: nil,
                registeredDevices: 0, failure: "no profile"
            ) == .notAuthorized
        )
    }

    @Test("a missing token is reported with the system's reason")
    func missingTokenCarriesTheReason() {
        // The reason is the whole value of this case: "no valid aps-environment
        // entitlement" tells a developer exactly what #30 is for, and a bare
        // "something went wrong" tells them nothing.
        #expect(
            Model.obstacle(
                enabled: true, authorized: true, token: nil,
                registeredDevices: 0, failure: "no valid aps-environment entitlement"
            ) == .noToken(reason: "no valid aps-environment entitlement")
        )
    }

    @Test("a missing token with no reason given is still reported")
    func missingTokenWithoutReason() {
        // Registration that simply has not come back yet produces no error at
        // all, and the screen still has to account for the silence.
        #expect(
            Model.obstacle(
                enabled: true, authorized: true, token: nil,
                registeredDevices: 0, failure: nil
            ) == .noToken(reason: nil)
        )
    }

    @Test("a token the backend has not been told about is its own problem")
    func tokenButNoRegistration() {
        // Distinct from having no token: here the phone is ready and the
        // server is the one that does not know, which is a different fix.
        #expect(
            Model.obstacle(
                enabled: true, authorized: true, token: "abc123",
                registeredDevices: 0, failure: nil
            ) == .notRegistered
        )
    }

    @Test("all three in place is no obstacle at all")
    func readyHasNoObstacle() {
        #expect(
            Model.obstacle(
                enabled: true, authorized: true, token: "abc123",
                registeredDevices: 1, failure: nil
            ) == nil
        )
    }

    @Test("a stale failure is ignored once a token exists")
    func failureIsIgnoredWhenRegistered() {
        // PushTokens clears its failure when a token arrives, but this function
        // must not depend on that: a reason left over from an earlier launch is
        // not an obstacle when everything works now.
        #expect(
            Model.obstacle(
                enabled: true, authorized: true, token: "abc123",
                registeredDevices: 2, failure: "an earlier attempt failed"
            ) == nil
        )
    }
}
