-- When a title's streaming availability was last asked about, per region (#18).
--
-- The title already carries an availability_fetched_at, but availability rows
-- are per region, and one timestamp cannot answer "have we asked about this
-- title in Switzerland". Worse, it cannot record the useful negative: a title
-- on no service in a country produces no rows at all, so freshness inferred
-- from the rows themselves says "never checked" forever and every request to
-- that screen goes back to the provider.
CREATE TABLE availability_check (
    id         BIGSERIAL   PRIMARY KEY,
    title_id   BIGINT      NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    region     VARCHAR(2)  NOT NULL,

    -- The answer's age, whatever the answer was. An empty result is a result.
    checked_at TIMESTAMPTZ NOT NULL,

    UNIQUE (title_id, region)
);
