import Foundation
import WatchGuruAPI

/// What a connection to another service has been doing, in words.
///
/// Shared by Plex and Trakt, and here rather than in either view because it is
/// the part worth testing: an integration that silently stops is worse than one
/// never offered, and the whole difference between "working" and "stopped" is
/// which of these sentences the user is shown.
///
/// The two services differ in exactly one place — what a connection that has
/// never heard anything should say — so that sentence is passed in rather than
/// decided here.
enum ConnectionActivity {

    /// How a connection is doing, as a view needs to present it.
    enum Health: Equatable {
        /// Nothing is connected.
        case notConnected
        /// Connected, but nothing has arrived yet.
        case waiting
        /// Viewings are arriving and being recorded.
        case working
        /// Connected, and the last attempt could not be completed.
        case needsAttention
    }

    static func health(connected: Bool, account: LinkedAccountResponse?) -> Health {
        guard connected else { return .notConnected }
        guard let account else { return .waiting }

        // A connection that has never heard anything is not broken, and saying
        // so would send somebody to redo setup that is already right: Plex only
        // calls when something finishes playing, and a Trakt sync runs every
        // couple of hours.
        guard account.lastSyncAt != nil else { return .waiting }

        if let error = account.lastSyncError, !error.isEmpty { return .needsAttention }
        if account.status == .error { return .needsAttention }
        return .working
    }

    /// One delivery or sync run, in one line.
    ///
    /// The error message is the server's own and is written for the person
    /// reading it — "open the series once so its episodes are fetched" is
    /// something they can act on, which "PARTIAL" is not.
    static func describe(_ run: SyncRunResponse) -> String {
        if run.itemsImported > 0 { return "Recorded \(run.itemsImported)" }
        if run.itemsSkipped > 0 { return "Nothing new" }
        if let error = run.errorMessage, !error.isEmpty { return error }
        if run.status == .running { return "In progress" }
        return "Nothing to record"
    }

    /// The headline sentence for a connection.
    ///
    /// Takes the last error verbatim where there is one: a generic "something
    /// went wrong" would hide the only part that tells the user what to do.
    ///
    /// - Parameter waiting: what to say when nothing has arrived yet, the one
    ///   sentence that differs between a webhook and a scheduled sync.
    static func summary(
        connected: Bool,
        account: LinkedAccountResponse?,
        waiting: String
    ) -> String {
        switch health(connected: connected, account: account) {
        case .notConnected:
            "Not connected."
        case .waiting:
            waiting
        case .working:
            "Connected and recording."
        case .needsAttention:
            account?.lastSyncError.flatMap { $0.isEmpty ? nil : $0 }
                ?? "Connected, but the last attempt could not be completed."
        }
    }
}
