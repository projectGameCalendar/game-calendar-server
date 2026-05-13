package com.projectgc.calendar.repository.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class ServiceApiJdbcRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }

    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: ServiceApiJdbcRepository

    @BeforeEach
    fun setUp() {
        val dataSource = DriverManagerDataSource(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        )
        jdbc = JdbcTemplate(dataSource)
        repository = ServiceApiJdbcRepository(jdbc, ObjectMapper().registerKotlinModule())
        resetSchema()
        seedReferenceRows()
    }

    @Test
    fun `release query uses fixed codes for korea fallback and korean metadata`() {
        seedGame()
        jdbc.update(
            "INSERT INTO service.game_release (id, game_id, platform_id, region_id, status_id, release_date) VALUES (100, 10, 6, 9, 6, TIMESTAMPTZ '2026-05-10 00:00:00+00')",
        )
        jdbc.update(
            "INSERT INTO service.game_release (id, game_id, platform_id, region_id, status_id, release_date) VALUES (101, 10, 6, 8, 6, TIMESTAMPTZ '2026-06-01 00:00:00+00')",
        )
        jdbc.update(
            "INSERT INTO service.game_release (id, game_id, platform_id, region_id, status_id, release_date) VALUES (102, 10, 167, 8, 6, TIMESTAMPTZ '2026-06-01 00:00:00+00')",
        )
        jdbc.update(
            "INSERT INTO service.game_release (id, game_id, platform_id, region_id, status_id, release_date) VALUES (103, 10, 130, 9, 5, TIMESTAMPTZ '2026-06-01 00:00:00+00')",
        )

        val releases = repository.findReleases(
            startInclusive = Instant.parse("2026-06-01T00:00:00Z"),
            endExclusive = Instant.parse("2026-06-02T00:00:00Z"),
            platformIds = emptySet(),
        )

        assertEquals(1, releases.size)
        assertEquals(listOf(102L), releases.single().releaseIds)
        assertEquals("한국어 제목", releases.single().title)
        assertEquals(8L, releases.single().region?.id)
        assertEquals(listOf(167L), releases.single().platforms.map { it.id })
        assertEquals(true, releases.single().koreanLanguageSupport?.subtitles)
    }

    @Test
    fun `game detail aggregates related rows without row multiplication`() {
        seedGame()
        jdbc.update(
            "INSERT INTO service.game_release (id, game_id, platform_id, region_id, status_id, release_date) VALUES (100, 10, 6, 9, 6, TIMESTAMPTZ '2026-05-10 00:00:00+00')",
        )
        jdbc.update(
            "INSERT INTO service.game_release (id, game_id, platform_id, region_id, status_id, release_date) VALUES (101, 10, 167, 8, 6, TIMESTAMPTZ '2026-06-01 00:00:00+00')",
        )
        jdbc.update("INSERT INTO service.game_genre (game_id, genre_id) VALUES (10, 1)")
        jdbc.update("INSERT INTO service.game_company (game_id, company_id, is_developer) VALUES (10, 20, TRUE)")
        jdbc.update("INSERT INTO service.website (id, game_id, type_id, url, is_trusted) VALUES (30, 10, 40, 'https://example.com', TRUE)")
        jdbc.update("INSERT INTO service.game_video (id, game_id, name, video_id) VALUES (50, 10, 'Trailer', 'abc123')")

        val game = repository.findGame(10L)

        assertNotNull(game)
        assertEquals("한국어 제목", game.title)
        assertEquals(listOf(6L, 167L), game.platforms.map { it.id })
        assertEquals(listOf("RPG"), game.genres.map { it.name })
        assertEquals(listOf("Studio"), game.developers.map { it.name })
        assertEquals("https://example.com", game.websites.single().url)
        assertEquals("https://www.youtube.com/watch?v=abc123", game.video?.url)
    }

    private fun seedGame() {
        jdbc.update(
            "INSERT INTO service.game (id, slug, name, summary, first_release_date, type_id, status_id) VALUES (10, 'default-game', 'Default Game', 'Summary', TIMESTAMPTZ '2026-05-10 00:00:00+00', 1, 1)",
        )
        jdbc.update("INSERT INTO service.game_localization (id, game_id, region_id, name) VALUES (200, 10, 2, '한국어 제목')")
        jdbc.update("INSERT INTO service.cover (id, game_id, game_localization_id, image_id, url, is_main) VALUES (300, 10, 200, 'co-kr', NULL, FALSE)")
        jdbc.update("INSERT INTO service.game_language (game_id, language_id, supports_audio, supports_subtitles, supports_interface) VALUES (10, 17, FALSE, TRUE, TRUE)")
    }

    private fun seedReferenceRows() {
        jdbc.update("INSERT INTO service.game_type (id, type) VALUES (1, 'Main Game')")
        jdbc.update("INSERT INTO service.game_status (id, status) VALUES (1, 'Released')")
        jdbc.update("INSERT INTO service.release_region (id, name) VALUES (8, 'worldwide')")
        jdbc.update("INSERT INTO service.release_region (id, name) VALUES (9, 'korea')")
        jdbc.update("INSERT INTO service.release_status (id, name) VALUES (5, 'Cancelled')")
        jdbc.update("INSERT INTO service.release_status (id, name) VALUES (6, 'Full Release')")
        jdbc.update("INSERT INTO service.region (id, name, identifier) VALUES (2, 'Korea', 'ko-KR')")
        jdbc.update("INSERT INTO service.platform (id, name, abbreviation) VALUES (6, 'PC (Microsoft Windows)', 'PC')")
        jdbc.update("INSERT INTO service.platform (id, name, abbreviation) VALUES (130, 'Nintendo Switch', 'Switch')")
        jdbc.update("INSERT INTO service.platform (id, name, abbreviation) VALUES (167, 'PlayStation 5', 'PS5')")
        jdbc.update("INSERT INTO service.language (id, locale, name, native_name) VALUES (17, 'ko-KR', 'Korean', '한국어')")
        jdbc.update("INSERT INTO service.genre (id, name) VALUES (1, 'RPG')")
        jdbc.update("INSERT INTO service.company (id, name) VALUES (20, 'Studio')")
        jdbc.update("INSERT INTO service.website_type (id, type) VALUES (40, 'official')")
    }

    private fun resetSchema() {
        executeAll(
            "DROP SCHEMA IF EXISTS service CASCADE",
            "CREATE SCHEMA service",
            "CREATE TABLE service.game (id BIGINT PRIMARY KEY, slug TEXT, name TEXT, summary TEXT, first_release_date TIMESTAMPTZ, type_id BIGINT, status_id BIGINT)",
            "CREATE TABLE service.game_type (id BIGINT PRIMARY KEY, type TEXT)",
            "CREATE TABLE service.game_status (id BIGINT PRIMARY KEY, status TEXT)",
            "CREATE TABLE service.release_region (id BIGINT PRIMARY KEY, name TEXT)",
            "CREATE TABLE service.release_status (id BIGINT PRIMARY KEY, name TEXT)",
            "CREATE TABLE service.platform (id BIGINT PRIMARY KEY, name TEXT, abbreviation TEXT)",
            "CREATE TABLE service.game_release (id BIGINT PRIMARY KEY, game_id BIGINT NOT NULL, platform_id BIGINT, region_id BIGINT, status_id BIGINT, release_date TIMESTAMPTZ)",
            "CREATE TABLE service.region (id BIGINT PRIMARY KEY, name TEXT, identifier TEXT)",
            "CREATE TABLE service.game_localization (id BIGINT PRIMARY KEY, game_id BIGINT NOT NULL, region_id BIGINT, name TEXT)",
            "CREATE TABLE service.cover (id BIGINT PRIMARY KEY, game_id BIGINT NOT NULL, game_localization_id BIGINT, image_id TEXT, url TEXT, is_main BOOLEAN NOT NULL DEFAULT FALSE)",
            "CREATE TABLE service.language (id BIGINT PRIMARY KEY, locale TEXT, name TEXT, native_name TEXT)",
            "CREATE TABLE service.game_language (game_id BIGINT NOT NULL, language_id BIGINT NOT NULL, supports_audio BOOLEAN NOT NULL DEFAULT FALSE, supports_subtitles BOOLEAN NOT NULL DEFAULT FALSE, supports_interface BOOLEAN NOT NULL DEFAULT FALSE, PRIMARY KEY (game_id, language_id))",
            "CREATE TABLE service.genre (id BIGINT PRIMARY KEY, name TEXT)",
            "CREATE TABLE service.game_genre (game_id BIGINT NOT NULL, genre_id BIGINT NOT NULL, PRIMARY KEY (game_id, genre_id))",
            "CREATE TABLE service.company (id BIGINT PRIMARY KEY, name TEXT)",
            "CREATE TABLE service.game_company (game_id BIGINT NOT NULL, company_id BIGINT NOT NULL, is_developer BOOLEAN NOT NULL DEFAULT FALSE, PRIMARY KEY (game_id, company_id))",
            "CREATE TABLE service.website_type (id BIGINT PRIMARY KEY, type TEXT)",
            "CREATE TABLE service.website (id BIGINT PRIMARY KEY, game_id BIGINT NOT NULL, type_id BIGINT, url TEXT, is_trusted BOOLEAN NOT NULL DEFAULT FALSE)",
            "CREATE TABLE service.game_video (id BIGINT PRIMARY KEY, game_id BIGINT NOT NULL, name TEXT, video_id TEXT)",
        )
    }

    private fun executeAll(vararg statements: String) {
        statements.forEach(jdbc::execute)
    }
}
