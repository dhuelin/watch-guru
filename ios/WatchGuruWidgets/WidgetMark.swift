import Foundation

/// Marking an episode watched from the home screen.
///
/// The widget does not link the app's networking, so this is the one request it
/// makes — deliberately the smallest possible: one POST, one bearer token, no
/// generated client, no session refresh.
///
/// Refresh is the interesting omission. When the stored access token has
/// expired, the widget does **not** try to renew it: that is a second endpoint,
/// a rotating refresh token, and a write to the Keychain that would race the app
/// doing the same thing. Instead the mark goes into the queue the app already
/// replays. The user sees the episode advance either way, which is the only part
/// they can observe.
///
/// That works because the mark carries a `clientRef`, and the server treats a
/// repeat of one as the same event (#21). So a mark that reached the server and
/// *also* got queued — the case where the reply is lost rather than the request
/// — is filed once, not twice. Without that reference this design would turn a
/// flaky connection into rewatches.
enum WidgetMark {

    /// The outcome and the status-code mapping both live in `WidgetSharing`, so
    /// the app compiles them too and they can be tested without an extension.
    typealias Outcome = WidgetMarkOutcome

    static func markEpisodeWatched(
        episodeId: Int64,
        titleId: Int64,
        tokens: TokenStore = KeychainTokenStore(),
        queue: MutationQueue = MutationQueue(store: FileOfflineStore()),
        feed: WidgetFeedStore = WidgetFeedStore(),
        session: URLSession = .shared,
        now: Date = .now
    ) async -> Outcome {
        // Minted once and used by both paths, so the queued copy and the one
        // that may already have arrived are the same event to the server.
        let clientRef = UUID().uuidString

        let outcome = await send(
            episodeId: episodeId,
            clientRef: clientRef,
            watchedAt: now,
            tokens: tokens,
            session: session
        )

        switch outcome {
        case .refused:
            // Nothing local changed and nothing is worth retrying: the server
            // answered, and it said no.
            return .refused

        case .sent, .queued:
            if outcome == .queued {
                queue.enqueue { id in
                    .markEpisodeWatched(
                        id: id,
                        episodeId: episodeId,
                        titleId: titleId,
                        watchedAt: now,
                        clientRef: clientRef
                    )
                }
            }
            // Advance the home screen now rather than waiting for the app to
            // refetch. Without this the widget reloads and redraws the episode
            // the user just marked, which reads as the tap having done nothing.
            feed.advance(past: episodeId)
            return outcome
        }
    }

    /// One POST, or a reason it could not be made.
    private static func send(
        episodeId: Int64,
        clientRef: String,
        watchedAt: Date,
        tokens: TokenStore,
        session: URLSession
    ) async -> Outcome {
        // No session, or one too old to use: queue it without spending a
        // request that is certain to come back 401.
        guard let current = tokens.tokens(), !current.isExpired(at: watchedAt) else {
            return .queued
        }

        var request = URLRequest(
            url: WidgetSharing.defaultBaseURL.appending(path: "api/v1/me/watch-events/episode")
        )
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(current.accessToken)", forHTTPHeaderField: "Authorization")
        // A widget's whole run is short-lived; a request that hangs is worse
        // than one that queues.
        request.timeoutInterval = 10
        request.httpBody = try? JSONEncoder().encode(
            Body(clientRef: clientRef, episodeId: episodeId, watchedAt: Self.format(watchedAt))
        )

        do {
            let (_, response) = try await session.data(for: request)
            guard let status = (response as? HTTPURLResponse)?.statusCode else { return .queued }
            return Outcome(status: status)
        } catch {
            // Offline, timed out, DNS — all the same thing here.
            return .queued
        }
    }

    /// The shape the server's `LogEpisodeWatched` expects.
    private struct Body: Encodable {
        let clientRef: String
        let episodeId: Int64
        let watchedAt: String
    }

    /// `2026-09-27T16:30:00Z` — an ISO-8601 instant, which is what the server's
    /// `Instant watchedAt` parses. Not the device's local offset: the server
    /// decides what day that is.
    private static func format(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: date)
    }
}
