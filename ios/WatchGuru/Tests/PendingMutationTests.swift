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

    @Test("a mark queued before references existed still decodes")
    func olderEntriesStillDecode() throws {
        // The old shape is derived by removing the field rather than written
        // out by hand, so this stays a test about compatibility rather than
        // about how the compiler spells an enum's keys.
        let current = try encoded([
            .markEpisodeWatched(id: 1, episodeId: 4, titleId: 9, watchedAt: .now, clientRef: "dropped")
        ])
        let withoutRef = String(decoding: current, as: UTF8.self)
            .replacingOccurrences(of: "\"clientRef\":\"dropped\"", with: "")
            .replacingOccurrences(of: ",,", with: ",")
            .replacingOccurrences(of: ",}", with: "}")

        let decoded = try JSONDecoder().decode(
            [PendingMutation].self, from: Data(withoutRef.utf8))

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
}
