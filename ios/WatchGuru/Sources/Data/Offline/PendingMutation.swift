import Foundation

/// A change the user made that the server has not accepted yet.
///
/// Only changes worth surviving a relaunch are here. Reads are not queued — a
/// search that failed offline is repeated by the user, not replayed behind
/// their back.
///
/// Every one is safe to replay, which matters because the app cannot tell "the
/// server never saw it" from "the server saw it and the reply was lost". Most
/// are idempotent by nature — unmarking an episode twice is unmarking it.
/// Logging a viewing is not: a second one is a *rewatch*, which is a real thing
/// people record. Those carry a `clientRef` the server files the viewing under,
/// so replaying a send that did arrive returns that same viewing instead of
/// inventing a second one.
enum PendingMutation: Codable, Equatable, Sendable {

    /// - Parameter watchedAt: when the user actually watched it. Carried rather
    ///   than recomputed on replay — sending "now" when the train reaches
    ///   signal would file three episodes watched last night as watched this
    ///   morning, which is wrong on the history screen and in the streak.
    ///
    /// - Parameter clientRef: names the viewing to the server. Optional only so
    ///   that anything queued by an older build still decodes — dropping the
    ///   queue on upgrade would lose exactly the marks this layer exists to
    ///   keep. Those replay as they used to.
    case markEpisodeWatched(id: Int64, episodeId: Int64, titleId: Int64, watchedAt: Date, clientRef: String?)

    /// A film the user logged, possibly for a past date.
    ///
    /// Never collapsed with anything: two viewings of one film are a film and
    /// its rewatch, which is a thing people do on purpose. Hence a target built
    /// from the reference rather than the title.
    case logFilmWatched(id: Int64, titleId: Int64, watchedAt: Date, clientRef: String)

    case unmarkEpisode(id: Int64, episodeId: Int64)
    case markWatchedUpTo(id: Int64, episodeId: Int64)
    case addToLibrary(id: Int64, providerId: Int64, titleType: String, status: String?)
    case updateLibraryItem(id: Int64, itemId: Int64, status: String?)
    case removeFromLibrary(id: Int64, itemId: Int64)

    /// Monotonic, assigned on enqueue.
    ///
    /// Replay is strictly in this order: marking an episode and then unmarking
    /// it must not arrive the other way round, or the library ends up in a
    /// state the user never asked for.
    var id: Int64 {
        switch self {
        case .markEpisodeWatched(let id, _, _, _, _),
             .logFilmWatched(let id, _, _, _),
             .unmarkEpisode(let id, _),
             .markWatchedUpTo(let id, _),
             .addToLibrary(let id, _, _, _),
             .updateLibraryItem(let id, _, _),
             .removeFromLibrary(let id, _):
            return id
        }
    }

    /// The thing this acts on, used to collapse redundant work.
    var target: String {
        switch self {
        case .markEpisodeWatched(_, let episodeId, _, _, _): "episode:\(episodeId)"
        case .logFilmWatched(_, _, _, let clientRef): "film-viewing:\(clientRef)"
        case .unmarkEpisode(_, let episodeId): "episode:\(episodeId)"
        // Not collapsible against single-episode marks: it covers a range, and
        // sharing their target would drop marks it does not include.
        case .markWatchedUpTo(_, let episodeId): "up-to:\(episodeId)"
        case .addToLibrary(_, let providerId, let titleType, _): "library:\(titleType):\(providerId)"
        case .updateLibraryItem(_, let itemId, _): "item:\(itemId)"
        case .removeFromLibrary(_, let itemId): "item:\(itemId)"
        }
    }
}
