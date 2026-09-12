import Foundation
import WatchGuruAPI

/// What a Plex connection has been doing, in words.
///
/// Here rather than in the view because it is the part worth testing: an
/// integration that silently stops is worse than one never offered, and the
/// whole difference between "working" and "stopped" is which of these
/// sentences the user is shown.
enum PlexActivity {

    /// How a connection is doing, as the screen needs to present it.
    enum Health: Equatable {
        /// No webhook URL is live.
        case notConnected
        /// Connected, but the server has never called. Usually a URL not pasted yet.
        case waiting
        /// Deliveries are arriving and being recorded.
        case working
        /// Deliveries are arriving, and the last one could not be recorded.
        case needsAttention
    }

    static func health(of status: PlexStatusResponse) -> Health {
        guard status.connected else { return .notConnected }
        guard let account = status.account else { return .waiting }

        // A link that has never heard anything is not broken, and saying so
        // would send somebody to re-paste a URL that is fine: Plex only calls
        // when something finishes playing.
        guard account.lastSyncAt != nil else { return .waiting }

        if let error = account.lastSyncError, !error.isEmpty { return .needsAttention }
        if account.status == .error { return .needsAttention }
        return .working
    }

    /// One delivery, in one line.
    ///
    /// The error message is the server's own and is written for the person
    /// reading it — "open the series once so its episodes are fetched" is
    /// something they can act on, which "PARTIAL" is not.
    static func describe(_ run: SyncRunResponse) -> String {
        if run.itemsImported > 0 { return "Recorded" }
        if run.itemsSkipped > 0 { return "Already recorded" }
        if let error = run.errorMessage, !error.isEmpty { return error }
        if run.status == .running { return "In progress" }
        return "Nothing to record"
    }

    /// The headline sentence for the connection.
    ///
    /// Takes the last error verbatim where there is one: a generic "something
    /// went wrong" would hide the only part that tells the user what to do.
    static func summary(of status: PlexStatusResponse) -> String {
        switch health(of: status) {
        case .notConnected:
            "Not connected."
        case .waiting:
            "Waiting for your server. Paste the webhook URL into Plex, then watch something."
        case .working:
            "Connected and recording."
        case .needsAttention:
            status.account?.lastSyncError.flatMap { $0.isEmpty ? nil : $0 }
                ?? "Connected, but the last delivery could not be recorded."
        }
    }
}
