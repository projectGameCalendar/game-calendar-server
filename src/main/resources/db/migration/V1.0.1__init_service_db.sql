-- 서비스 스키마
CREATE SCHEMA IF NOT EXISTS service;



-- ============================================================================
-- 1. Game (from ingest.game)
--   - 배열 컬럼 제거
--   - 상태/타입/커버/언어/장르 등은 별도 테이블로 정규화
--   - 타임스탬프형으로 변경, 검색용 tsvector 추가
-- ============================================================================

DROP TABLE IF EXISTS service.game CASCADE;

CREATE TABLE IF NOT EXISTS service.game (
    id                  BIGINT PRIMARY KEY,          -- IGDB game.id
    slug                TEXT NOT NULL UNIQUE,        -- URL-safe slug
    name                TEXT NOT NULL,               -- 기본 이름
    summary             TEXT,                        -- 요약 설명
    storyline           TEXT,                        -- 스토리 라인
    first_release_date  TIMESTAMPTZ,                 -- 최초 출시일 (to_timestamp)
    status_id           BIGINT,                      -- service.game_status.id
    type_id             BIGINT,                      -- service.game_type.id
    source_updated_at   TIMESTAMPTZ,                 -- IGDB updated_at -> TIMESTAMPTZ
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    search_vector       tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(summary, '')), 'B')
    ) STORED
);

CREATE INDEX IF NOT EXISTS idx_game_first_release_date
    ON service.game (first_release_date);

CREATE INDEX IF NOT EXISTS idx_game_search_vector
    ON service.game USING GIN (search_vector);



-- ============================================================================
-- 2. Game 간 관계 (from ingest.game parent_game, remakes, remasters, ports, standalone_expansions, similar_games)
-- ============================================================================

DROP TABLE IF EXISTS service.game_relation CASCADE;

CREATE TABLE IF NOT EXISTS service.game_relation (
    game_id         BIGINT NOT NULL,   -- 기준 게임
    related_game_id BIGINT NOT NULL,   -- 관계 대상 게임
    relation_type   TEXT NOT NULL,     -- PARENT/REMAKE/REMASTER/PORT/STANDALONE_EXPANSION/SIMILAR
    PRIMARY KEY (game_id, related_game_id, relation_type),
    CONSTRAINT chk_game_relation_type CHECK (
        relation_type IN (
            'PARENT',
            'REMAKE',
            'REMASTER',
            'PORT',
            'STANDALONE_EXPANSION',
            'SIMILAR'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_game_relation_related
    ON service.game_relation (related_game_id, relation_type);



-- ============================================================================
-- 3. Alternative Name (from ingest.alternative_name)
--   - game.alternative_names 배열 제거하고 본 테이블만 사용
-- ============================================================================

DROP TABLE IF EXISTS service.alternative_name CASCADE;

CREATE TABLE IF NOT EXISTS service.alternative_name (
    id        BIGINT PRIMARY KEY,   -- IGDB alternative_name.id
    game_id   BIGINT NOT NULL,      -- service.game.id
    name      TEXT NOT NULL,        -- 대체 이름
    comment   TEXT                  -- 유형 설명 (약칭, 작업 제목 등)
);

CREATE INDEX IF NOT EXISTS idx_alt_name_game
    ON service.alternative_name (game_id);

CREATE INDEX IF NOT EXISTS idx_alt_name_name
    ON service.alternative_name (name);



-- ============================================================================
-- 4. Game Localization (from ingest.game_localization)
--   - game.game_localizations 배열 제거
--   - 커버는 service.cover에서 game_localization_id로 연결
-- ============================================================================

DROP TABLE IF EXISTS service.game_localization CASCADE;

CREATE TABLE IF NOT EXISTS service.game_localization (
    id        BIGINT PRIMARY KEY,   -- IGDB game_localization.id
    game_id   BIGINT NOT NULL,      -- service.game.id
    region_id BIGINT,               -- service.region.id
    name      TEXT NOT NULL         -- 로컬라이즈된 이름
);

CREATE INDEX IF NOT EXISTS idx_game_loc_game
    ON service.game_localization (game_id);

CREATE INDEX IF NOT EXISTS idx_game_loc_region
    ON service.game_localization (region_id);



-- ============================================================================
-- 5. Region (from ingest.region)
-- ============================================================================

DROP TABLE IF EXISTS service.region CASCADE;

CREATE TABLE IF NOT EXISTS service.region (
    id         BIGINT PRIMARY KEY,   -- IGDB region.id
    name       TEXT NOT NULL,        -- 지역명
    identifier TEXT                  -- 식별자 (코드 등)
);



-- ============================================================================
-- 6. Release Date Region (from ingest.release_date_region)
--   - 릴리즈 전용 지역 코드
-- ============================================================================

DROP TABLE IF EXISTS service.release_region CASCADE;

CREATE TABLE IF NOT EXISTS service.release_region (
    id    BIGINT PRIMARY KEY,    -- IGDB release_date_region.id
    name  TEXT NOT NULL          -- 지역명
);



-- ============================================================================
-- 7. Release Status (from ingest.release_date_status)
-- ============================================================================

DROP TABLE IF EXISTS service.release_status CASCADE;

CREATE TABLE IF NOT EXISTS service.release_status (
    id          BIGINT PRIMARY KEY,   -- IGDB release_date_status.id
    name        TEXT NOT NULL,        -- 상태명
    description TEXT                  -- 설명
);



-- ============================================================================
-- 8. Game Release (from ingest.release_date)
--   - fact: 게임 x 플랫폼 x 릴리즈 지역 x 출시일
--   - 출시일 BIGINT → TIMESTAMPTZ
-- ============================================================================

DROP TABLE IF EXISTS service.game_release CASCADE;

CREATE TABLE IF NOT EXISTS service.game_release (
    id           BIGINT PRIMARY KEY,   -- IGDB release_date.id
    game_id      BIGINT NOT NULL,      -- service.game.id
    platform_id  BIGINT,               -- service.platform.id
    region_id    BIGINT,               -- service.release_region.id
    status_id    BIGINT,               -- service.release_status.id
    release_date TIMESTAMPTZ,          -- date(BIGINT) → TIMESTAMPTZ
    year         INTEGER,              -- y
    month        INTEGER,              -- m
    date_human   TEXT                  -- human
);

CREATE INDEX IF NOT EXISTS idx_release_game
    ON service.game_release (game_id);

CREATE INDEX IF NOT EXISTS idx_release_date
    ON service.game_release (release_date);

CREATE INDEX IF NOT EXISTS idx_release_platform_date
    ON service.game_release (platform_id, release_date);



-- ============================================================================
-- 9. Platform Logo (from ingest.platform_logo)
-- ============================================================================

DROP TABLE IF EXISTS service.platform_logo CASCADE;

CREATE TABLE IF NOT EXISTS service.platform_logo (
    id       BIGINT PRIMARY KEY,   -- IGDB platform_logo.id
    image_id TEXT NOT NULL,        -- 이미지 ID
    url      TEXT                  -- URL
);



-- ============================================================================
-- 10. Platform Type (from ingest.platform_type)
-- ============================================================================

DROP TABLE IF EXISTS service.platform_type CASCADE;

CREATE TABLE IF NOT EXISTS service.platform_type (
    id   BIGINT PRIMARY KEY,   -- IGDB platform_type.id
    name TEXT NOT NULL         -- 타입명
);



-- ============================================================================
-- 11. Platform (from ingest.platform)
-- ============================================================================

DROP TABLE IF EXISTS service.platform CASCADE;

CREATE TABLE IF NOT EXISTS service.platform (
    id               BIGINT PRIMARY KEY,   -- IGDB platform.id
    name             TEXT NOT NULL,        -- 플랫폼명
    abbreviation     TEXT,                 -- 약칭
    alternative_name TEXT,                 -- 대체명
    logo_id          BIGINT,               -- service.platform_logo.id
    type_id          BIGINT                -- service.platform_type.id
);

CREATE INDEX IF NOT EXISTS idx_platform_name
    ON service.platform (name);



-- ============================================================================
-- 12. Game Status (from ingest.game_status)
-- ============================================================================

DROP TABLE IF EXISTS service.game_status CASCADE;

CREATE TABLE IF NOT EXISTS service.game_status (
    id     BIGINT PRIMARY KEY,   -- IGDB game_status.id
    status TEXT NOT NULL         -- 상태 문자열
);



-- ============================================================================
-- 13. Game Type (from ingest.game_type)
-- ============================================================================

DROP TABLE IF EXISTS service.game_type CASCADE;

CREATE TABLE IF NOT EXISTS service.game_type (
    id   BIGINT PRIMARY KEY,   -- IGDB game_type.id
    type TEXT NOT NULL         -- 유형명 (메인 게임, DLC 등)
);



-- ============================================================================
-- 14. Language (from ingest.language)
-- ============================================================================

DROP TABLE IF EXISTS service.language CASCADE;

CREATE TABLE IF NOT EXISTS service.language (
    id          BIGINT PRIMARY KEY,   -- IGDB language.id
    locale      TEXT NOT NULL,        -- 언어/국가 코드 (en-US 등)
    name        TEXT NOT NULL,        -- 영어명
    native_name TEXT                  -- 자국어명
);

CREATE INDEX IF NOT EXISTS idx_language_locale
    ON service.language (locale);



-- ============================================================================
-- 15. Language Support Type (from ingest.language_support_type)
--   - ETL 시 game_language 변환에 필요 (audio/subtitle/interface 등 매핑)
-- ============================================================================

DROP TABLE IF EXISTS service.language_support_type CASCADE;

CREATE TABLE IF NOT EXISTS service.language_support_type (
    id   BIGINT PRIMARY KEY,   -- IGDB language_support_type.id
    name TEXT NOT NULL         -- 지원 유형명
);



-- ============================================================================
-- 16. Game Language (from ingest.language_support)
--   - game, language, language_support_type를 하나로 집계한 서비스용 구조
--   - supports_* 플래그 컬럼으로 변환
-- ============================================================================

DROP TABLE IF EXISTS service.game_language CASCADE;

CREATE TABLE IF NOT EXISTS service.game_language (
    game_id             BIGINT NOT NULL,  -- service.game.id
    language_id         BIGINT NOT NULL,  -- service.language.id
    supports_audio      BOOLEAN NOT NULL DEFAULT FALSE,
    supports_subtitles  BOOLEAN NOT NULL DEFAULT FALSE,
    supports_interface  BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (game_id, language_id)
);

CREATE INDEX IF NOT EXISTS idx_game_language_language
    ON service.game_language (language_id);



-- ============================================================================
-- 17. Genre (from ingest.genre) + Game-Genre bridge (from game.genres[])
-- ============================================================================

DROP TABLE IF EXISTS service.genre CASCADE;

CREATE TABLE IF NOT EXISTS service.genre (
    id   BIGINT PRIMARY KEY,   -- IGDB genre.id
    name TEXT NOT NULL         -- 장르명
);

DROP TABLE IF EXISTS service.game_genre CASCADE;

CREATE TABLE IF NOT EXISTS service.game_genre (
    game_id  BIGINT NOT NULL,  -- service.game.id
    genre_id BIGINT NOT NULL,  -- service.genre.id
    PRIMARY KEY (game_id, genre_id)
);

CREATE INDEX IF NOT EXISTS idx_game_genre_genre
    ON service.game_genre (genre_id);



-- ============================================================================
-- 18. Theme (from ingest.theme) + Game-Theme bridge (from game.themes[])
-- ============================================================================

DROP TABLE IF EXISTS service.theme CASCADE;

CREATE TABLE IF NOT EXISTS service.theme (
    id   BIGINT PRIMARY KEY,   -- IGDB theme.id
    name TEXT NOT NULL         -- 테마명
);

DROP TABLE IF EXISTS service.game_theme CASCADE;

CREATE TABLE IF NOT EXISTS service.game_theme (
    game_id  BIGINT NOT NULL,  -- service.game.id
    theme_id BIGINT NOT NULL,  -- service.theme.id
    PRIMARY KEY (game_id, theme_id)
);

CREATE INDEX IF NOT EXISTS idx_game_theme_theme
    ON service.game_theme (theme_id);



-- ============================================================================
-- 19. Player Perspective (from ingest.player_perspective)
--     + Game-PlayerPerspective bridge (from game.player_perspectives[])
-- ============================================================================

DROP TABLE IF EXISTS service.player_perspective CASCADE;

CREATE TABLE IF NOT EXISTS service.player_perspective (
    id   BIGINT PRIMARY KEY,   -- IGDB player_perspective.id
    name TEXT NOT NULL         -- 관점명
);

DROP TABLE IF EXISTS service.game_player_perspective CASCADE;

CREATE TABLE IF NOT EXISTS service.game_player_perspective (
    game_id              BIGINT NOT NULL,  -- service.game.id
    player_perspective_id BIGINT NOT NULL, -- service.player_perspective.id
    PRIMARY KEY (game_id, player_perspective_id)
);

CREATE INDEX IF NOT EXISTS idx_game_player_perspective_pp
    ON service.game_player_perspective (player_perspective_id);



-- ============================================================================
-- 20. Game Mode (from ingest.game_mode) + bridge (from game.game_modes[])
-- ============================================================================

DROP TABLE IF EXISTS service.game_mode CASCADE;

CREATE TABLE IF NOT EXISTS service.game_mode (
    id   BIGINT PRIMARY KEY,   -- IGDB game_mode.id
    name TEXT NOT NULL         -- 모드명
);

DROP TABLE IF EXISTS service.game_game_mode CASCADE;

CREATE TABLE IF NOT EXISTS service.game_game_mode (
    game_id      BIGINT NOT NULL,  -- service.game.id
    game_mode_id BIGINT NOT NULL,  -- service.game_mode.id
    PRIMARY KEY (game_id, game_mode_id)
);

CREATE INDEX IF NOT EXISTS idx_game_mode_mode
    ON service.game_game_mode (game_mode_id);



-- ============================================================================
-- 21. Keyword (from ingest.keyword) + bridge (from game.keywords[])
-- ============================================================================

DROP TABLE IF EXISTS service.keyword CASCADE;

CREATE TABLE IF NOT EXISTS service.keyword (
    id   BIGINT PRIMARY KEY,   -- IGDB keyword.id
    name TEXT NOT NULL         -- 키워드명
);

DROP TABLE IF EXISTS service.game_keyword CASCADE;

CREATE TABLE IF NOT EXISTS service.game_keyword (
    game_id    BIGINT NOT NULL,  -- service.game.id
    keyword_id BIGINT NOT NULL,  -- service.keyword.id
    PRIMARY KEY (game_id, keyword_id)
);

CREATE INDEX IF NOT EXISTS idx_game_keyword_keyword
    ON service.game_keyword (keyword_id);



-- ============================================================================
-- 22. Company (from ingest.company)
--     - developed[], published[] 배열 제거 (game_company에서 역추론)
-- ============================================================================

DROP TABLE IF EXISTS service.company CASCADE;

CREATE TABLE IF NOT EXISTS service.company (
    id                    BIGINT PRIMARY KEY,   -- IGDB company.id
    name                  TEXT NOT NULL,        -- 회사명
    parent_company_id     BIGINT,               -- 모회사
    merged_into_company_id BIGINT               -- 통합/변경된 회사
);

CREATE INDEX IF NOT EXISTS idx_company_name
    ON service.company (name);



-- ============================================================================
-- 23. Game-Company (from ingest.involved_company + game.involved_companies[])
--     - 하나의 row에 역할 플래그를 집계
-- ============================================================================

DROP TABLE IF EXISTS service.game_company CASCADE;

CREATE TABLE IF NOT EXISTS service.game_company (
    game_id        BIGINT NOT NULL,  -- service.game.id
    company_id     BIGINT NOT NULL,  -- service.company.id
    is_developer   BOOLEAN NOT NULL DEFAULT FALSE,
    is_publisher   BOOLEAN NOT NULL DEFAULT FALSE,
    is_porting     BOOLEAN NOT NULL DEFAULT FALSE,
    is_supporting  BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (game_id, company_id)
);

CREATE INDEX IF NOT EXISTS idx_game_company_company
    ON service.game_company (company_id);



-- ============================================================================
-- 24. Cover (from ingest.cover + game.cover)
--     - 메인 커버 여부 is_main 플래그
-- ============================================================================

DROP TABLE IF EXISTS service.cover CASCADE;

CREATE TABLE IF NOT EXISTS service.cover (
    id                   BIGINT PRIMARY KEY,   -- IGDB cover.id
    game_id              BIGINT NOT NULL,      -- service.game.id
    game_localization_id BIGINT,               -- service.game_localization.id
    image_id             TEXT NOT NULL,        -- 이미지 식별자
    url                  TEXT,                 -- URL
    is_main              BOOLEAN NOT NULL DEFAULT FALSE  -- 메인 커버 여부
);

CREATE INDEX IF NOT EXISTS idx_cover_game
    ON service.cover (game_id);



-- ============================================================================
-- 25. Artwork (from ingest.artwork)
-- ============================================================================

DROP TABLE IF EXISTS service.artwork CASCADE;

CREATE TABLE IF NOT EXISTS service.artwork (
    id       BIGINT PRIMARY KEY,   -- IGDB artwork.id
    game_id  BIGINT NOT NULL,      -- service.game.id
    image_id TEXT NOT NULL,        -- 이미지 식별자
    url      TEXT                  -- URL
);

CREATE INDEX IF NOT EXISTS idx_artwork_game
    ON service.artwork (game_id);



-- ============================================================================
-- 26. Screenshot (from ingest.screenshot)
-- ============================================================================

DROP TABLE IF EXISTS service.screenshot CASCADE;

CREATE TABLE IF NOT EXISTS service.screenshot (
    id       BIGINT PRIMARY KEY,   -- IGDB screenshot.id
    game_id  BIGINT NOT NULL,      -- service.game.id
    image_id TEXT NOT NULL,        -- 이미지 식별자
    url      TEXT                  -- URL
);

CREATE INDEX IF NOT EXISTS idx_screenshot_game
    ON service.screenshot (game_id);



-- ============================================================================
-- 27. Game Video (from ingest.game_video)
-- ============================================================================

DROP TABLE IF EXISTS service.game_video CASCADE;

CREATE TABLE IF NOT EXISTS service.game_video (
    id       BIGINT PRIMARY KEY,   -- IGDB game_video.id
    game_id  BIGINT NOT NULL,      -- service.game.id
    name     TEXT,                 -- 비디오 이름
    video_id TEXT NOT NULL         -- 외부 비디오 ID (YouTube 등)
);

CREATE INDEX IF NOT EXISTS idx_game_video_game
    ON service.game_video (game_id);



-- ============================================================================
-- 28. Website Type (from ingest.website_type)
-- ============================================================================

DROP TABLE IF EXISTS service.website_type CASCADE;

CREATE TABLE IF NOT EXISTS service.website_type (
    id   BIGINT PRIMARY KEY,   -- IGDB website_type.id
    type TEXT NOT NULL         -- 타입명
);



-- ============================================================================
-- 29. Website (from ingest.website)
-- ============================================================================

DROP TABLE IF EXISTS service.website CASCADE;

CREATE TABLE IF NOT EXISTS service.website (
    id         BIGINT PRIMARY KEY,         -- IGDB website.id
    game_id    BIGINT NOT NULL,            -- service.game.id
    type_id    BIGINT,                     -- service.website_type.id
    url        TEXT NOT NULL,              -- URL
    is_trusted BOOLEAN NOT NULL DEFAULT FALSE  -- trusted
);

CREATE INDEX IF NOT EXISTS idx_website_game
    ON service.website (game_id);

CREATE INDEX IF NOT EXISTS idx_website_type
    ON service.website (type_id);



-- ============================================================================
-- 30. Foreign Keys
--   - 테이블 생성 순서를 유지하기 위해 마지막에 FK 추가
-- ============================================================================

-- game
ALTER TABLE service.game
    ADD CONSTRAINT fk_game_status
        FOREIGN KEY (status_id) REFERENCES service.game_status (id);

ALTER TABLE service.game
    ADD CONSTRAINT fk_game_type
        FOREIGN KEY (type_id) REFERENCES service.game_type (id);

-- game_relation
ALTER TABLE service.game_relation
    ADD CONSTRAINT fk_game_relation_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_relation
    ADD CONSTRAINT fk_game_relation_related_game
        FOREIGN KEY (related_game_id) REFERENCES service.game (id) ON DELETE CASCADE;

-- alternative_name
ALTER TABLE service.alternative_name
    ADD CONSTRAINT fk_alternative_name_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

-- game_localization
ALTER TABLE service.game_localization
    ADD CONSTRAINT fk_game_localization_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_localization
    ADD CONSTRAINT fk_game_localization_region
        FOREIGN KEY (region_id) REFERENCES service.region (id);

-- game_release
ALTER TABLE service.game_release
    ADD CONSTRAINT fk_game_release_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_release
    ADD CONSTRAINT fk_game_release_platform
        FOREIGN KEY (platform_id) REFERENCES service.platform (id);

ALTER TABLE service.game_release
    ADD CONSTRAINT fk_game_release_region
        FOREIGN KEY (region_id) REFERENCES service.release_region (id);

ALTER TABLE service.game_release
    ADD CONSTRAINT fk_game_release_status
        FOREIGN KEY (status_id) REFERENCES service.release_status (id);

-- platform
ALTER TABLE service.platform
    ADD CONSTRAINT fk_platform_logo
        FOREIGN KEY (logo_id) REFERENCES service.platform_logo (id);

ALTER TABLE service.platform
    ADD CONSTRAINT fk_platform_type
        FOREIGN KEY (type_id) REFERENCES service.platform_type (id);

-- company self reference
ALTER TABLE service.company
    ADD CONSTRAINT fk_company_parent
        FOREIGN KEY (parent_company_id) REFERENCES service.company (id);

ALTER TABLE service.company
    ADD CONSTRAINT fk_company_merged_into
        FOREIGN KEY (merged_into_company_id) REFERENCES service.company (id);

-- game_language
ALTER TABLE service.game_language
    ADD CONSTRAINT fk_game_language_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_language
    ADD CONSTRAINT fk_game_language_language
        FOREIGN KEY (language_id) REFERENCES service.language (id);

-- game_genre
ALTER TABLE service.game_genre
    ADD CONSTRAINT fk_game_genre_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_genre
    ADD CONSTRAINT fk_game_genre_genre
        FOREIGN KEY (genre_id) REFERENCES service.genre (id);

-- game_theme
ALTER TABLE service.game_theme
    ADD CONSTRAINT fk_game_theme_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_theme
    ADD CONSTRAINT fk_game_theme_theme
        FOREIGN KEY (theme_id) REFERENCES service.theme (id);

-- game_player_perspective
ALTER TABLE service.game_player_perspective
    ADD CONSTRAINT fk_game_pp_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_player_perspective
    ADD CONSTRAINT fk_game_pp_pp
        FOREIGN KEY (player_perspective_id) REFERENCES service.player_perspective (id);

-- game_game_mode
ALTER TABLE service.game_game_mode
    ADD CONSTRAINT fk_game_mode_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_game_mode
    ADD CONSTRAINT fk_game_mode_mode
        FOREIGN KEY (game_mode_id) REFERENCES service.game_mode (id);

-- game_keyword
ALTER TABLE service.game_keyword
    ADD CONSTRAINT fk_game_keyword_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_keyword
    ADD CONSTRAINT fk_game_keyword_keyword
        FOREIGN KEY (keyword_id) REFERENCES service.keyword (id);

-- game_company
ALTER TABLE service.game_company
    ADD CONSTRAINT fk_game_company_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_company
    ADD CONSTRAINT fk_game_company_company
        FOREIGN KEY (company_id) REFERENCES service.company (id);

-- cover / artwork / screenshot / video
ALTER TABLE service.cover
    ADD CONSTRAINT fk_cover_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.cover
    ADD CONSTRAINT fk_cover_game_localization
        FOREIGN KEY (game_localization_id) REFERENCES service.game_localization (id);

ALTER TABLE service.artwork
    ADD CONSTRAINT fk_artwork_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.screenshot
    ADD CONSTRAINT fk_screenshot_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.game_video
    ADD CONSTRAINT fk_game_video_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

-- website
ALTER TABLE service.website
    ADD CONSTRAINT fk_website_game
        FOREIGN KEY (game_id) REFERENCES service.game (id) ON DELETE CASCADE;

ALTER TABLE service.website
    ADD CONSTRAINT fk_website_type
        FOREIGN KEY (type_id) REFERENCES service.website_type (id);
