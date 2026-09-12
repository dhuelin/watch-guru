-- ---------------------------------------------------------------------------
-- V8: connecting a Plex server, so watching there records a watch here (#39).
--
-- The connection is a webhook the user's own Plex server calls. No Plex
-- password and no Plex token is stored: this service issues a token of its
-- own, embeds it in the URL the user pastes into Plex, and keeps only its
-- SHA-256 hash -- the same treatment a password gets, for the same reason.
-- A stolen database row cannot be turned back into a working webhook URL.
-- ---------------------------------------------------------------------------

ALTER TABLE linked_streaming_account
    ADD COLUMN webhook_token_hash      VARCHAR(64),
    ADD COLUMN webhook_token_issued_at TIMESTAMP(6) WITH TIME ZONE;

-- Two accounts can never share a token, and a lookup by hash is a single
-- index probe rather than a scan of every link in the table.
CREATE UNIQUE INDEX uq_linked_account_webhook_token
    ON linked_streaming_account (webhook_token_hash)
    WHERE webhook_token_hash IS NOT NULL;

-- Plex is not a place to watch something, so it carries no TMDB provider id
-- and will never appear in a "where to watch" answer. It is here because a
-- watch event needs to say where it came from, and "Plex" is the truthful
-- answer for anything a Plex server scrobbled.
INSERT INTO streaming_service (slug, name, supports_sync)
VALUES ('plex', 'Plex', TRUE)
ON CONFLICT (slug) DO UPDATE SET supports_sync = TRUE;

-- A correction to V2, which claimed these two could sync. They cannot, and no
-- amount of work changes that: neither Netflix nor Disney+ offers any public
-- API for viewing activity, and the alternatives are storing the user's
-- password or scraping -- both ruled out in #21 and again in #39. Their honest
-- path is the user-initiated export the import already reads, and a flag that
-- promises otherwise would put a dead "Connect" button in front of everybody.
UPDATE streaming_service
SET supports_sync = FALSE
WHERE slug IN ('netflix', 'disney-plus');
