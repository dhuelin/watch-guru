import Foundation
import Testing
@testable import WatchGuru

/// What the home screen is handed.
///
/// The feed is the only thing the widget can read: it is a separate process with
/// no access to the app's memory, its client or its cache. So the file *is* the
/// contract, and these are the parts of it a change could break silently — the
/// widget has no screen to show an error on.
struct WidgetFeedTests {

    private func temporaryStore() -> WidgetFeedStore {
        WidgetFeedStore(
            directory: URL.temporaryDirectory.appending(path: "feed-\(UUID().uuidString)")
        )
    }

    private func episode(
        id: Int64 = 1,
        code: String = "S2E4",
        name: String? = "Woe's Hollow"
    ) -> WidgetFeed.Episode {
        WidgetFeed.Episode(
            titleId: 9, episodeId: id, title: "Severance",
            episodeCode: code, episodeName: name,
            watchedEpisodes: 13, airedEpisodes: 19
        )
    }

    @Test("a written feed reads back as it was written")
    func roundTrips() {
        let store = temporaryStore()
        let feed = WidgetFeed(entries: [episode()], storedAt: .now)

        store.write(feed)

        #expect(store.read() == feed)
    }

    @Test("no feed is nil rather than an empty one")
    func missingIsNil() {
        // The widget shows different words for the two: an empty feed means
        // nothing is waiting, and no feed at all means the app has never
        // fetched. Collapsing them would tell a new user their library is empty.
        #expect(temporaryStore().read() == nil)
    }

    @Test("a feed that cannot be decoded is treated as absent")
    func corruptIsAbsent() throws {
        let directory = URL.temporaryDirectory.appending(path: "feed-\(UUID().uuidString)")
        let store = WidgetFeedStore(directory: directory)
        try Data("not json".utf8).write(to: directory.appending(path: "widget-feed.json"))

        // Unreadable is the same as absent. A widget cannot report a parse
        // error to anybody, so the only useful behaviour is to say nothing.
        #expect(store.read() == nil)
    }

    @Test("clearing removes the feed")
    func clears() {
        // Sign-out has to take this with it: the feed is one user's viewing,
        // sitting on a home screen the next one is looking at.
        let store = temporaryStore()
        store.write(WidgetFeed(entries: [episode()], storedAt: .now))

        store.clear()

        #expect(store.read() == nil)
    }

    @Test("a row reads as the code and the episode name")
    func subtitleNamesTheEpisode() {
        #expect(episode().subtitle == "S2E4 · Woe's Hollow")
    }

    @Test("a row with no episode name is just the code")
    func subtitleFallsBackToTheCode() {
        // Not "S2E4 · " with a dangling separator, which is what string
        // concatenation gives you when the optional is empty.
        #expect(episode(name: nil).subtitle == "S2E4")
    }

    @Test("marking advances past that episode and leaves the others")
    func advancesPastOneEpisode() {
        let store = temporaryStore()
        store.write(
            WidgetFeed(
                entries: [episode(id: 1), episode(id: 2), episode(id: 3)],
                storedAt: .now
            )
        )

        store.advance(past: 2)

        #expect(store.read()?.entries.map(\.episodeId) == [1, 3])
    }

    @Test("advancing does not make the feed look fresher than it is")
    func advanceKeepsTheTimestamp() {
        // Otherwise marking an episode would silence the "as of" note on a feed
        // that is still a week old, which is the one thing that note is for.
        let store = temporaryStore()
        let storedAt = Date(timeIntervalSince1970: 1_700_000_000)
        store.write(WidgetFeed(entries: [episode(id: 1), episode(id: 2)], storedAt: storedAt))

        store.advance(past: 1)

        #expect(store.read()?.storedAt == storedAt)
    }

    @Test("advancing an unknown episode changes nothing")
    func advanceIsIdempotent() {
        // The widget and the app can both advance past the same mark — one from
        // the button, one when the app notices. The second must be a no-op
        // rather than dropping somebody else's row.
        let store = temporaryStore()
        store.write(WidgetFeed(entries: [episode(id: 1)], storedAt: .now))

        store.advance(past: 99)

        #expect(store.read()?.entries.map(\.episodeId) == [1])
    }
}

/// Which answers from the server are worth keeping, and which are final.
///
/// This is the only judgement the widget makes on its own, and getting it wrong
/// is expensive in both directions: treat a retryable answer as final and a mark
/// the user was told had landed is gone, treat a final answer as retryable and
/// the queue replays a rejected request for ever.
struct WidgetMarkOutcomeTests {

    @Test("a 2xx is sent", arguments: [200, 201, 204])
    func successIsSent(status: Int) {
        #expect(WidgetMarkOutcome(status: status) == .sent)
    }

    @Test("an expired session queues rather than failing", arguments: [401])
    func unauthorisedQueues(status: Int) {
        // The widget deliberately cannot refresh a session — that is the app's
        // job, and racing it over the Keychain is worse than waiting. So a 401
        // is not a refusal, it is "not yet".
        #expect(WidgetMarkOutcome(status: status) == .queued)
    }

    @Test("a server having a bad minute queues", arguments: [408, 429, 500, 502, 503])
    func temporaryFailuresQueue(status: Int) {
        #expect(WidgetMarkOutcome(status: status) == .queued)
    }

    @Test("a request the server disagrees with is refused", arguments: [400, 403, 404, 409, 422])
    func permanentFailuresAreRefused(status: Int) {
        // Queueing these would replay them until the queue is cleared by hand.
        #expect(WidgetMarkOutcome(status: status) == .refused)
    }
}
