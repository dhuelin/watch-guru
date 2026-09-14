import Foundation
import Testing
@testable import WatchGuru

/// What the queue on disk has to survive.
///
/// The queue is dropped wholesale when it cannot be decoded — the alternative,
/// retrying for ever against a decoder that cannot read it, wedges every later
/// sync. That makes its encoding a compatibility surface: a change that stops
/// an older build's entries decoding does not fail loudly, it silently throws
/// away the marks this layer exists to keep.
struct PendingMutationTests {

    private func encoded(_ mutations: [PendingMutation]) throws -> Data {
        try JSONEncoder().encode(mutations)
    }

    /// Re-encodes without one associated value, whatever it was called.
    ///
    /// Editing the JSON as text does not survive contact with the encoder:
    /// it writes the fields in no order this test may assume, so cutting a
    /// key out of the middle of the string leaves a stray comma and the
    /// result is not JSON at all. Removing it from the parsed object asks
    /// nothing of the spelling or the ordering.
    private func removing(_ field: String, from data: Data) throws -> Data {
        var entries = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] ?? []
        for index in entries.indices {
            // One key per entry, named for the case; taken as found rather
            // than hardcoded, since the name is the compiler's business.
            for (caseName, payload) in entries[index] {
                guard var fields = payload as? [String: Any] else { continue }
                fields.removeValue(forKey: field)
                entries[index][caseName] = fields
            }
        }
        return try JSONSerialization.data(withJSONObject: entries)
    }

    @Test("a mark queued before references existed still decodes")
    func olderEntriesStillDecode() throws {
        // The old shape is derived by removing the field rather than written
        // out by hand, so this stays a test about compatibility rather than
        // about how the compiler spells an enum's keys.
        let current = try encoded([
            .markEpisodeWatched(id: 1, episodeId: 4, titleId: 9, watchedAt: .now, clientRef: "dropped")
        ])

        let decoded = try JSONDecoder().decode(
            [PendingMutation].self, from: removing("clientRef", from: current))

        #expect(decoded.count == 1)
        guard case .markEpisodeWatched(_, let episodeId, _, _, let clientRef) = decoded[0] else {
            Issue.record("expected a mark, got \(decoded[0])")
            return
        }
        #expect(episodeId == 4)
        #expect(clientRef == nil)
    }

    @Test("a queued viewing keeps its reference across a relaunch")
    func referencesSurviveARelaunch() throws {
        // The reference is the whole mechanism: it is what lets the server
        // recognise a replay of a send that did arrive. One minted afresh on
        // each attempt would be the same as having none, and the viewing would
        // land twice — the second time as a rewatch.
        let data = try encoded([
            .logFilmWatched(id: 1, titleId: 9, watchedAt: .now, clientRef: "ref-1")
        ])

        let decoded = try JSONDecoder().decode([PendingMutation].self, from: data)

        guard case .logFilmWatched(_, let titleId, _, let clientRef) = decoded[0] else {
            Issue.record("expected a film viewing, got \(decoded[0])")
            return
        }
        #expect(titleId == 9)
        #expect(clientRef == "ref-1")
    }

    @Test("two viewings of one film are two entries")
    func viewingsAreNotCollapsed() {
        // The one place collapsing by title would be wrong: a film and its
        // rewatch are two things the user did, and the history is a record of
        // viewings rather than of films.
        let first = PendingMutation.logFilmWatched(id: 1, titleId: 9, watchedAt: .now, clientRef: "a")
        let second = PendingMutation.logFilmWatched(id: 2, titleId: 9, watchedAt: .now, clientRef: "b")

        #expect(first.target != second.target)
    }

    @Test("marking the same episode twice collapses to one entry")
    func marksOnOneEpisodeStillCollapse() {
        // Unchanged by the reference: the target is still the episode, so
        // toggling one on a train leaves one entry rather than forty.
        let first = PendingMutation.markEpisodeWatched(
            id: 1, episodeId: 4, titleId: 9, watchedAt: .now, clientRef: "a")
        let second = PendingMutation.markEpisodeWatched(
            id: 2, episodeId: 4, titleId: 9, watchedAt: .now, clientRef: "b")

        #expect(first.target == second.target)
    }

    @Test("a library update carries the rating as well as the status")
    func libraryUpdatesCarryTheirRating() throws {
        // Before the rating travelled with it, a rating chosen offline reached
        // the server as an update that set only the status -- silently losing
        // the thing the user had just chosen.
        let data = try encoded([
            .updateLibraryItem(id: 1, itemId: 7, status: "COMPLETED", rating: 8)
        ])

        let decoded = try JSONDecoder().decode([PendingMutation].self, from: data)

        guard case .updateLibraryItem(_, let itemId, let status, let rating) = decoded[0] else {
            Issue.record("expected a library update, got \(decoded[0])")
            return
        }
        #expect(itemId == 7)
        #expect(status == "COMPLETED")
        #expect(rating == 8)
    }

    @Test("a library update queued before ratings existed still decodes")
    func olderLibraryUpdatesStillDecode() throws {
        let current = try encoded([
            .updateLibraryItem(id: 1, itemId: 7, status: "COMPLETED", rating: 8)
        ])

        let decoded = try JSONDecoder().decode(
            [PendingMutation].self, from: removing("rating", from: current))

        guard case .updateLibraryItem(_, _, let status, let rating) = decoded[0] else {
            Issue.record("expected a library update, got \(decoded[0])")
            return
        }
        #expect(status == "COMPLETED")
        #expect(rating == nil)
    }
}
