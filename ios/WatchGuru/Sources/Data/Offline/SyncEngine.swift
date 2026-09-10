import Foundation

/// Sends queued changes to the server, oldest first.
///
/// The rules are the whole point, and each is a bug if inverted:
///
/// - **In order, and stop at the first offline failure.** Skipping a stuck
///   mutation to make progress on later ones applies them out of order — an
///   unmark landing before the mark it was meant to undo leaves the episode
///   watched.
/// - **A rejection is permanent; drop it.** A 404 for an episode that no longer
///   exists will not succeed on the tenth attempt, and keeping it blocks every
///   mutation behind it for ever.
/// - **A server error is transient; keep it.** The user's work is not thrown
///   away because the server had a bad minute.
struct SyncEngine: Sendable {

    /// What happened, so a caller can decide whether to refresh or say "still
    /// offline".
    struct Outcome: Equatable {
        var sent = 0
        /// Rejected permanently. Worth surfacing if ever non-zero: it means
        /// something the user did was discarded.
        var dropped = 0
        var stoppedOffline = false
        var stoppedUnauthorised = false
        var stoppedTransient = false

        var finished: Bool { !stoppedOffline && !stoppedUnauthorised && !stoppedTransient }
    }

    let queue: MutationQueue

    /// Performs one mutation. Injected so the rules above are testable without
    /// a network.
    ///
    /// `@Sendable`, and the struct with it: `sync()` is async, so calling it
    /// from an actor sends this value to a nonisolated executor. Without the
    /// conformance that is "sending value of non-Sendable type 'SyncEngine'
    /// risks causing data races" -- which is a fair complaint rather than
    /// pedantry, since the closure is what actually touches shared state.
    let send: @Sendable (PendingMutation) async -> APIFailure?

    func sync() async -> Outcome {
        var outcome = Outcome()

        for mutation in queue.pending() {
            guard let failure = await send(mutation) else {
                queue.remove(id: mutation.id)
                outcome.sent += 1
                continue
            }

            switch failure {
            // Still no network. Everything after this has to wait its turn, so
            // stop rather than reorder.
            case .offline:
                outcome.stoppedOffline = true
                return outcome

            // The session died mid-sync. Nothing queued can succeed until the
            // user signs in again, and continuing would collect 401s for every
            // entry and achieve nothing.
            case .unauthorised:
                outcome.stoppedUnauthorised = true
                return outcome

            // The server understood and said no. Retrying cannot change that,
            // and keeping it blocks the queue.
            case .notFound:
                queue.remove(id: mutation.id)
                outcome.dropped += 1

            case .upstream:
                outcome.stoppedTransient = true
                return outcome

            case .unexpected(let status, _):
                if let status, (400..<500).contains(status) {
                    queue.remove(id: mutation.id)
                    outcome.dropped += 1
                } else {
                    // 5xx, or no status at all. Transient until proven
                    // otherwise.
                    outcome.stoppedTransient = true
                    return outcome
                }
            }
        }
        return outcome
    }
}
