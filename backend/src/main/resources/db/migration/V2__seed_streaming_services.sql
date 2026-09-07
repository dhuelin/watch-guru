-- Services we intend to build a direct account integration for.
--
-- tmdb_provider_id is deliberately left NULL: the provider list is reconciled
-- against TMDB's /watch/providers endpoint at runtime, which fills the id in by
-- name and inserts any other provider it returns. Seeding guessed ids here
-- would risk mapping availability data onto the wrong service.
INSERT INTO streaming_service (slug, name, supports_sync)
VALUES ('netflix', 'Netflix', TRUE),
       ('disney-plus', 'Disney Plus', TRUE),
       ('amazon-prime-video', 'Amazon Prime Video', FALSE),
       ('apple-tv-plus', 'Apple TV Plus', FALSE),
       ('max', 'Max', FALSE),
       ('paramount-plus', 'Paramount Plus', FALSE),
       ('hulu', 'Hulu', FALSE)
ON CONFLICT (slug) DO NOTHING;
