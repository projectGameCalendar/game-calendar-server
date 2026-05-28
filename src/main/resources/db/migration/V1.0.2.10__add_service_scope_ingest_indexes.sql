-- Service ETL scope filters:
--   - Korea/worldwide release rows on supported platforms
--   - Korean/English localization and language support
--   - trusted service API media/link projections

CREATE INDEX IF NOT EXISTS idx_ingest_release_date_service_scope
    ON ingest.release_date (release_region, status, platform, game, date);

CREATE INDEX IF NOT EXISTS idx_ingest_release_date_korea_fallback
    ON ingest.release_date (game, platform, release_region, status, date);

CREATE INDEX IF NOT EXISTS idx_ingest_game_localization_game_region
    ON ingest.game_localization (game, region);

CREATE INDEX IF NOT EXISTS idx_ingest_language_support_game_language
    ON ingest.language_support (game, language);

CREATE INDEX IF NOT EXISTS idx_ingest_involved_company_game_developer_company
    ON ingest.involved_company (game, developer, company);

CREATE INDEX IF NOT EXISTS idx_ingest_website_game_trusted_type
    ON ingest.website (game, trusted, type);
