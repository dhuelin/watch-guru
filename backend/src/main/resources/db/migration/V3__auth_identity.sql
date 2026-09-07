-- ---------------------------------------------------------------------------
-- Authentication identity.
--
-- Until now app_user.auth_subject was reserved but unused, and the API
-- identified callers by a user id in the URL. These columns carry what an
-- OIDC sign-in actually needs to be safe.
-- ---------------------------------------------------------------------------

-- The issuer that vouched for auth_subject. auth_subject is stored namespaced
-- as '<issuer>|<sub>' so two providers cannot collide on an opaque subject
-- string, but the issuer is kept separately as well: it is what decides
-- whether an email assertion may be trusted for account linking, and querying
-- for "all Apple accounts" should not mean a LIKE against a composite key.
ALTER TABLE app_user
    ADD COLUMN auth_issuer VARCHAR(255);

-- Whether the issuer asserted that this address is verified. Account linking
-- across providers keys on the email address, so an unverified address must
-- never be usable to reach an existing account.
ALTER TABLE app_user
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE app_user
    ADD COLUMN last_login_at TIMESTAMP(6) WITH TIME ZONE;

-- Sign-in resolves a token subject to a user on every authenticated request,
-- so this lookup is the hottest query in the system. uq_app_user_auth_subject
-- already provides the index; this one supports the linking path, which looks
-- users up by verified email.
CREATE INDEX idx_app_user_email_verified ON app_user (email) WHERE email_verified;
