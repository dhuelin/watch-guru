-- ---------------------------------------------------------------------------
-- V10: Jellyfin and Emby, on the shape Plex already established (#39).
--
-- Nothing new is needed to store them: a webhook link is a webhook link, and
-- linked_streaming_account already holds the hashed token, the account name to
-- filter on and the record of what last arrived. All this migration does is
-- say the two services exist and can be connected.
-- ---------------------------------------------------------------------------

INSERT INTO streaming_service (slug, name, supports_sync)
VALUES ('jellyfin', 'Jellyfin', TRUE),
       ('emby', 'Emby', TRUE)
ON CONFLICT (slug) DO UPDATE SET supports_sync = TRUE;
