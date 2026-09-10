-- Session tokens issued by this service, replacing the provider ID token as
-- the bearer credential (#26).
--
-- Only the refresh token is stored. Access tokens are self-signed JWTs and are
-- deliberately not persisted: they are short-lived, and a table of live access
-- tokens is a table worth stealing.
CREATE TABLE refresh_token (
    id             BIGSERIAL PRIMARY KEY,

    -- SHA-256 of the token, hex. The token itself is never stored: a database
    -- leak must not hand out live sessions. SHA-256 rather than bcrypt on
    -- purpose -- the input is 256 bits of CSPRNG output, so there is no
    -- guessable password to slow an attacker down, and refresh happens on
    -- every app launch.
    --
    -- VARCHAR rather than CHAR even though the hex is always 64 characters:
    -- Postgres pads CHAR with spaces, which turns a lookup by hash into a
    -- comparison that can silently miss.
    token_hash     VARCHAR(64) NOT NULL UNIQUE,

    user_id        BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,

    -- Every token descended from one sign-in shares a family. Rotation issues
    -- a new token in the same family; presenting an already-used token means
    -- either a stolen token or a client bug, and the whole family is revoked
    -- because there is no way to tell which holder is the legitimate one.
    family_id      UUID        NOT NULL,

    issued_at      TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,

    -- Set when this token is exchanged. A second exchange of the same token is
    -- the reuse signal.
    used_at        TIMESTAMPTZ,

    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(64)
);

-- Revoking a family, and listing a user's sessions.
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

-- Sweeping expired rows.
CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);
