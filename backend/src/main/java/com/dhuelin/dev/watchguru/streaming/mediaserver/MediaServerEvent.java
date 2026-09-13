package com.dhuelin.dev.watchguru.streaming.mediaserver;

import com.dhuelin.dev.watchguru.imports.domain.ImportRow;

/**
 * One playback event from a media server, in the terms this codebase
 * understands.
 *
 * <p>Plex, Jellyfin and Emby describe the same evening in three different
 * shapes. Each adapter turns its own into this, and everything after that --
 * whose viewing it is, whether it has been recorded already, what it matches --
 * is the same code for all three.
 *
 * @param accountName    who played it, as the server names them. What keeps a
 *                       housemate's evening out of this user's history
 * @param webhookOwners  whether the server says this is the webhook owner's own
 *                       account. Plex sends this; Jellyfin and Emby have no
 *                       equivalent, and send null -- which is why they insist
 *                       on a username instead
 * @param row            what was watched, in the shape the importer matches,
 *                       with {@code sourceRef} already set to this viewing's
 *                       identity
 */
public record MediaServerEvent(String accountName, Boolean webhookOwners, ImportRow row) {
}
