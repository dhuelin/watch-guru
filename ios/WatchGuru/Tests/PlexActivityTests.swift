import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// What the Plex screen says about a connection.
///
/// The distinction these pin down is the one the whole feature rests on: an
/// integration that silently stops is worse than one that was never offered,
/// so "connected but nothing has arrived yet" and "connected and the last
/// delivery failed" must not read the same.
struct PlexActivityTests {

    private let now = Date(timeIntervalSince1970: 1_789_000_000)

    @Test("no link at all is not connected")
    func notConnected() {
        let status = PlexStatusResponse(account: nil, connected: false, recentRuns: [])

        #expect(PlexActivity.health(of: status) == .notConnected)
        #expect(PlexActivity.summary(of: status) == "Not connected.")
    }

    @Test("a link that has never heard anything is waiting, not broken")
    func waitingIsNotAnError() {
        // Plex only calls when something finishes playing, so silence right
        // after connecting is the normal case. Calling it an error would send
        // somebody to re-paste a URL that is fine.
        let status = status(account(lastSyncAt: nil))

        #expect(PlexActivity.health(of: status) == .waiting)
        #expect(PlexActivity.summary(of: status).contains("Paste the webhook URL"))
    }

    @Test("deliveries arriving cleanly read as working")
    func working() {
        let status = status(account(lastSyncAt: now))

        #expect(PlexActivity.health(of: status) == .working)
        #expect(PlexActivity.summary(of: status) == "Connected and recording.")
    }

    @Test("the last error is shown verbatim, because it says what to do")
    func showsTheError() {
        let note = "Found Severance, but not the episode this row names."
        let status = status(account(lastSyncAt: now, lastSyncError: note))

        #expect(PlexActivity.health(of: status) == .needsAttention)
        #expect(PlexActivity.summary(of: status) == note)
    }

    @Test("a link the server marked ERROR needs attention even with no message")
    func errorWithoutAMessage() {
        let status = status(account(lastSyncAt: now, status: .error))

        #expect(PlexActivity.health(of: status) == .needsAttention)
        #expect(!PlexActivity.summary(of: status).isEmpty)
    }

    @Test("a delivery describes what became of it")
    func describesADelivery() {
        #expect(PlexActivity.describe(run(imported: 1)) == "Recorded")
        #expect(PlexActivity.describe(run(skipped: 1)) == "Already recorded")
        #expect(
            PlexActivity.describe(run(failed: 1, error: "Nothing in your catalogue matches \"Heat\"."))
                == "Nothing in your catalogue matches \"Heat\".")
        #expect(PlexActivity.describe(run()) == "Nothing to record")
    }

    private func status(_ account: LinkedAccountResponse?) -> PlexStatusResponse {
        PlexStatusResponse(account: account, connected: true, recentRuns: [])
    }

    private func account(
        lastSyncAt: Date?,
        lastSyncError: String? = nil,
        status: LinkedAccountResponse.Status = .connected
    ) -> LinkedAccountResponse {
        LinkedAccountResponse(
            accountLabel: "denis",
            id: 1,
            lastSyncAt: lastSyncAt,
            lastSyncError: lastSyncError,
            service: StreamingServiceResponse(id: 9, name: "Plex", slug: "plex", supportsSync: true),
            status: status,
            syncEnabled: true)
    }

    private func run(
        imported: Int = 0,
        skipped: Int = 0,
        failed: Int = 0,
        error: String? = nil
    ) -> SyncRunResponse {
        SyncRunResponse(
            errorMessage: error,
            finishedAt: now,
            id: 1,
            itemsFailed: failed,
            itemsImported: imported,
            itemsSkipped: skipped,
            startedAt: now,
            status: .success)
    }
}
