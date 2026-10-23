-- ---------------------------------------------------------------------------
-- V11: binding sealed credentials to their row, and single-use sign-in nonces.
-- ---------------------------------------------------------------------------

-- How a credential was sealed. Every row written so far is format 0: AES-GCM
-- with no associated data, which means a sealed value copied onto another
-- row still opens there. Format 1 seals with the row's credential_ref as
-- associated data. Existing rows are moved to format 1 by the application at
-- startup (CredentialResealJob), not here: re-sealing needs the key, and the
-- key is deliberately never in the database.
ALTER TABLE streaming_credential
    ADD COLUMN format_version INTEGER NOT NULL DEFAULT 0;

-- Nonces that have already started a session.
--
-- Each sign-in asks Apple or Google for an ID token carrying a nonce the app
-- just made up, and the exchange presents the token together with that nonce.
-- Recording the nonce here makes the token single-use: a token copied out of a
-- log or a proxy cannot be exchanged a second time, even within the hour it
-- is valid. Rows are kept until the token they came from has expired, after
-- which the token is refused on its own and the row is no longer needed.
CREATE TABLE auth_nonce
(
    nonce_hash VARCHAR(64)  NOT NULL PRIMARY KEY,
    used_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_auth_nonce_expires ON auth_nonce (expires_at);
