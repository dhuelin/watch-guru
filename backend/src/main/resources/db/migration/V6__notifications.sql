-- New-episode notifications (#19).
--
-- Three tables and one flag: where to send, whether to send, and what has
-- already been sent.

-- The global off switch. On the user rather than in the preference table so
-- that "notify me about nothing" is one row to read, and so a user who has
-- never opened the settings screen still has an answer.
ALTER TABLE app_user
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Where a user's pushes go. One row per install, not per user: the same person
-- with a phone and a tablet gets both.
CREATE TABLE device_token (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,

    -- The APNs device token or FCM registration token. Unique across users,
    -- deliberately: a device handed to somebody else re-registers with the new
    -- account, and the row must move rather than duplicate -- otherwise the
    -- previous owner keeps receiving pushes on hardware they no longer have.
    token        VARCHAR(512) NOT NULL UNIQUE,

    platform     VARCHAR(16)  NOT NULL,

    created_at   TIMESTAMPTZ  NOT NULL,

    -- Refreshed every time the app registers. A token nobody has confirmed in
    -- months is almost certainly a dead install; this is what makes that
    -- visible without waiting for the push service to say so.
    last_seen_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_device_token_user ON device_token (user_id);

-- Per-series opt-outs. Only rows that differ from the default exist: absence
-- means "notify", so following a new series needs no write at all.
CREATE TABLE notification_preference (
    id           BIGSERIAL   PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title_id     BIGINT      NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    new_episodes BOOLEAN     NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,

    UNIQUE (user_id, title_id)
);

-- What has already been sent. This table is the acceptance criterion "exactly
-- one notification per new episode": the unique constraint is what enforces
-- it, rather than the scheduler being careful. A job that runs twice, or two
-- instances running at once, cannot produce a second push.
CREATE TABLE notification_delivery (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    episode_id BIGINT      NOT NULL REFERENCES episode (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,

    UNIQUE (user_id, episode_id)
);

-- The daily cap counts rows per user per day.
CREATE INDEX idx_notification_delivery_user_created ON notification_delivery (user_id, created_at);
