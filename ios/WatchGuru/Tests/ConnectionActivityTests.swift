import Foundation
import Testing
import WatchGuruAPI
@testable import WatchGuru

/// What a connection screen says about a connection.
///
/// The distinction these pin down is the one both integrations rest on: an
/// integration that silently stops is worse than one that was never offered, so
/// "connected but nothing has arrived yet" and "connected and the last attempt
/// failed" must not read the same.
struct ConnectionActivityTests {

    private let now = Date(timeIntervalSince1970: 1_789_000_000)
    private let plexWaiting = "Waiting for your server."
    private let traktWaiting = "Approve the connection in your browser, then come back here."

    @Test("nothing connected is not connected")
    func notConnected() {
        #expect(ConnectionActivity.health(connected: false, account: nil) == .notConnected)
        #expect(ConnectionActivity.summary(connected: false, account: nil, waiting: plexWaiting)
            == "Not connected.")
    }

    @Test("a connection that has never heard anything is waiting, not broken")
    func waitingIsNotAnError() {
        // Plex only calls when something finishes playing, and a Trakt sync
        // runs every couple of hours: silence right after connecting is normal,
        // and each service says so in its own words.
        let account = account(lastSyncAt: nil)

        #expect(ConnectionActivity.health(connected: true, account: account) == .waiting)
        #expect(ConnectionActivity.summary(connected: true, account: account, waiting: plexWaiting)
            == plexWaiting)
        #expect(ConnectionActivity.summary(connected: true, account: account, waiting: traktWaiting)
            == traktWaiting)
    }

    @Test("activity arriving cleanly reads as working")
    func working() {
        let account = account(lastSyncAt: now)

        #expect(ConnectionActivity.health(connected: true, account: account) == .working)
        #expect(ConnectionActivity.summary(connected: true, account: account, waiting: traktWaiting)
            == "Connected and recording.")
    }

    @Test("the last error is shown verbatim, because it says what to do")
    func showsTheError() {
        let note = "Trakt access has expired. Connect Trakt again to resume syncing."
        let account = account(lastSyncAt: now, lastSyncError: note)

        #expect(ConnectionActivity.health(connected: true, account: account) == .needsAttention)
        #expect(ConnectionActivity.summary(connected: true, account: account, waiting: traktWaiting)
            == note)
    }

    @Test("a link the server marked ERROR needs attention even with no message")
    func errorWithoutAMessage() {
        let account = account(lastSyncAt: now, status: .error)

        #expect(ConnectionActivity.health(connected: true, account: account) == .needsAttention)
        #expect(!ConnectionActivity.summary(
            connected: true, account: account, waiting: traktWaiting).isEmpty)
    }

    @Test("a run describes what became of it")
    func describesARun() {
        #expect(ConnectionActivity.describe(run(imported: 1)) == "Recorded 1")
        #expect(ConnectionActivity.describe(run(imported: 12)) == "Recorded 12")
        #expect(ConnectionActivity.describe(run(skipped: 3)) == "Nothing new")
        #expect(
            ConnectionActivity.describe(
                run(failed: 1, error: "Nothing in your catalogue matches \"Heat\"."))
                == "Nothing in your catalogue matches \"Heat\".")
        #expect(ConnectionActivity.describe(run()) == "Nothing to record")
    }

    private func account(
        lastSyncAt: Date?,
        lastSyncError: String? = nil,
        status: LinkedAccountResponse.Status = .connected
    ) -> LinkedAccountResponse {
        LinkedAccountResponse(
            accountLabel: nil,
            id: 1,
            lastSyncAt: lastSyncAt,
            lastSyncError: lastSyncError,
            service: StreamingServiceResponse(id: 9, name: "Trakt", slug: "trakt", supportsSync: true),
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
