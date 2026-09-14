package dev.dhuelin.watchguru.data.offline

import kotlinx.serialization.Serializable

/**
 * A change the user made that the server has not accepted yet.
 *
 * Only changes worth surviving a restart are here. Reads are not queued -- a
 * search that failed offline is repeated by the user, not replayed behind their
 * back -- and neither is anything whose meaning depends on when it runs.
 *
 * Every one of these is safe to replay, which matters because the app cannot
 * tell "the server never saw it" from "the server saw it and the reply was
 * lost". Most are idempotent by nature -- unmarking an episode twice is
 * unmarking it. Logging a viewing is not: a second one is a *rewatch*, which is
 * a real thing people record. Those carry a `clientRef` instead, which the
 * server files the viewing under, so the replay of a send that did arrive
 * returns that same viewing rather than inventing a second one.
 *
 * @param id monotonic, assigned on enqueue. Replay is strictly in this order:
 *   marking an episode and then unmarking it must not arrive the other way
 *   round, or the user's library ends up in a state they never asked for.
 */
@Serializable
sealed interface PendingMutation {

    val id: Long

    /** The thing this mutation acts on, used to collapse redundant work. */
    val target: String

    /**
     * @param watchedAtEpochSecond when the user actually watched it.
     *
     * Carried rather than recomputed on replay. Sending "now" when the train
     * reaches signal would file three episodes watched last night as watched
     * this morning, which is wrong on the history screen and wrong in the
     * streak calculation.
     *
     * @param clientRef names the viewing to the server, so a send whose reply
     *   was lost and the retry that follows it are one viewing rather than a
     *   viewing and a rewatch. Nullable only so that anything queued by an
     *   older build still deserializes; those replay as they used to.
     */
    @Serializable
    data class MarkEpisodeWatched(
        override val id: Long,
        val episodeId: Long,
        val titleId: Long,
        val watchedAtEpochSecond: Long,
        val clientRef: String? = null,
    ) : PendingMutation {
        override val target: String get() = "episode:$episodeId"
    }

    /**
     * A film the user logged, possibly for a past date.
     *
     * Not collapsible with anything: two viewings of the same film are a film
     * and its rewatch, which is a thing people do and record on purpose. The
     * target is the reference rather than the title for exactly that reason.
     */
    @Serializable
    data class LogFilmWatched(
        override val id: Long,
        val titleId: Long,
        val watchedAtEpochSecond: Long,
        val clientRef: String,
    ) : PendingMutation {
        override val target: String get() = "film-viewing:$clientRef"
    }

    @Serializable
    data class UnmarkEpisode(
        override val id: Long,
        val episodeId: Long,
    ) : PendingMutation {
        override val target: String get() = "episode:$episodeId"
    }

    @Serializable
    data class MarkWatchedUpTo(
        override val id: Long,
        val episodeId: Long,
    ) : PendingMutation {
        // Not collapsible against single-episode marks: it covers a range, and
        // treating it as the same target would drop marks it does not include.
        override val target: String get() = "up-to:$episodeId"
    }

    /**
     * Adding a title the local catalogue may not have yet.
     *
     * Carries the *provider* id and type rather than our title id, because
     * that is what the endpoint takes: a title the user found in search may
     * not exist in our catalogue until this call imports it, so there is no
     * local id to store.
     *
     * @param status the enum name, or null to let the server choose its default
     */
    @Serializable
    data class AddToLibrary(
        override val id: Long,
        val providerId: Long,
        val titleType: String,
        val status: String? = null,
    ) : PendingMutation {
        override val target: String get() = "library:$titleType:$providerId"
    }

    /**
     * @param status the enum name, or null to leave it alone
     * @param rating 0 to 10, or null to leave it alone. Carried because a
     *   rating queued offline is otherwise dropped on replay -- the mutation
     *   would be sent with only its status and quietly forget what the user
     *   actually typed.
     */
    @Serializable
    data class UpdateLibraryItem(
        override val id: Long,
        val itemId: Long,
        val status: String? = null,
        val rating: Double? = null,
    ) : PendingMutation {
        override val target: String get() = "item:$itemId"
    }

    @Serializable
    data class RemoveFromLibrary(
        override val id: Long,
        val itemId: Long,
    ) : PendingMutation {
        override val target: String get() = "item:$itemId"
    }
}
