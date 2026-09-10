package dev.dhuelin.watchguru.data.offline

import kotlinx.serialization.Serializable

/**
 * A change the user made that the server has not accepted yet.
 *
 * Only changes worth surviving a restart are here. Reads are not queued -- a
 * search that failed offline is repeated by the user, not replayed behind their
 * back -- and neither is anything whose meaning depends on when it runs.
 *
 * Every one of these is idempotent server-side, which is what makes replay safe
 * after a dropped response: the app cannot tell "the server never saw it" from
 * "the server saw it and the reply was lost", so it must be harmless to send
 * twice.
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
     */
    @Serializable
    data class MarkEpisodeWatched(
        override val id: Long,
        val episodeId: Long,
        val titleId: Long,
        val watchedAtEpochSecond: Long,
    ) : PendingMutation {
        override val target: String get() = "episode:$episodeId"
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

    @Serializable
    data class UpdateLibraryItem(
        override val id: Long,
        val itemId: Long,
        val status: String? = null,
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
