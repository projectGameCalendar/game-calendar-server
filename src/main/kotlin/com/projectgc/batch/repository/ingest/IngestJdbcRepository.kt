package com.projectgc.batch.repository.ingest

import com.projectgc.batch.model.entity.ingest.*
import com.projectgc.batch.service.TableSyncStats
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

@Repository
class IngestJdbcRepository(private val jdbc: JdbcTemplate) {

    companion object {
        private const val BATCH_SIZE = 50
    }

    // ── Generic helper ──────────────────────────────────────────────────────────

    private fun <T> batchUpsert(sql: String, items: List<T>, setter: PreparedStatement.(T) -> Unit) {
        if (items.isEmpty()) return
        jdbc.batchUpdate(sql, items, BATCH_SIZE) { ps, item -> ps.setter(item) }
    }

    // ── PreparedStatement extensions ────────────────────────────────────────────

    private fun PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value != null) setLong(index, value) else setNull(index, Types.BIGINT)
    }
    private fun PreparedStatement.setNullableInt(index: Int, value: Int?) {
        if (value != null) setInt(index, value) else setNull(index, Types.INTEGER)
    }
    private fun PreparedStatement.setNullableBoolean(index: Int, value: Boolean?) {
        if (value != null) setBoolean(index, value) else setNull(index, Types.BOOLEAN)
    }
    private fun PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value != null) setString(index, value) else setNull(index, Types.VARCHAR)
    }
    private fun PreparedStatement.setUUID(index: Int, value: UUID?) {
        if (value != null) setObject(index, value) else setNull(index, Types.OTHER)
    }
    private fun PreparedStatement.setLongArray(index: Int, value: Collection<Long>?) {
        if (value != null) setArray(index, connection.createArrayOf("bigint", value.toTypedArray()))
        else setNull(index, Types.ARRAY)
    }
    private fun PreparedStatement.setJsonb(index: Int, value: String?) {
        if (value != null) setObject(index, PGobject().apply { type = "jsonb"; this.value = value })
        else setNull(index, Types.OTHER)
    }

    // ── Named entity helper (id, <value_col>, checksum, updated_at) ─────────────

    private data class NamedRow(val id: Long, val value: String?, val checksum: UUID?, val updatedAt: Long?)

    private fun upsertNamedTable(table: String, column: String, rows: List<NamedRow>) = batchUpsert(
        """
        INSERT INTO ingest.$table (id, $column, checksum, updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            $column = EXCLUDED.$column, checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
        """.trimIndent(), rows
    ) { r ->
        setLong(1, r.id); setNullableString(2, r.value); setUUID(3, r.checksum); setNullableLong(4, r.updatedAt)
    }

    // ── Reference: named (name column) ──────────────────────────────────────────

    fun upsertGenres(entities: List<IngestGenreEntity>) =
        upsertNamedTable("genre", "name", entities.map { NamedRow(it.id, it.name, it.checksum, it.updatedAt) })
    fun upsertThemes(entities: List<IngestThemeEntity>) =
        upsertNamedTable("theme", "name", entities.map { NamedRow(it.id, it.name, it.checksum, it.updatedAt) })
    fun upsertPlayerPerspectives(entities: List<IngestPlayerPerspectiveEntity>) =
        upsertNamedTable("player_perspective", "name", entities.map { NamedRow(it.id, it.name, it.checksum, it.updatedAt) })
    fun upsertGameModes(entities: List<IngestGameModeEntity>) =
        upsertNamedTable("game_mode", "name", entities.map { NamedRow(it.id, it.name, it.checksum, it.updatedAt) })
    fun upsertKeywords(entities: List<IngestKeywordEntity>) =
        upsertNamedTable("keyword", "name", entities.map { NamedRow(it.id, it.name, it.checksum, it.updatedAt) })
    fun upsertLanguageSupportTypes(entities: List<IngestLanguageSupportTypeEntity>) =
        upsertNamedTable("language_support_type", "name", entities.map { NamedRow(it.id, it.name, it.checksum, it.updatedAt) })
    fun upsertPlatformTypes(entities: List<IngestPlatformTypeEntity>) =
        upsertNamedTable("platform_type", "name", entities.map { NamedRow(it.id, it.name, it.checksum, it.updatedAt) })

    // ── Reference: named (status/type column) ───────────────────────────────────

    fun upsertGameStatuses(entities: List<IngestGameStatusEntity>) =
        upsertNamedTable("game_status", "status", entities.map { NamedRow(it.id, it.status, it.checksum, it.updatedAt) })
    fun upsertGameTypes(entities: List<IngestGameTypeEntity>) =
        upsertNamedTable("game_type", "type", entities.map { NamedRow(it.id, it.type, it.checksum, it.updatedAt) })
    fun upsertWebsiteTypes(entities: List<IngestWebsiteTypeEntity>) =
        upsertNamedTable("website_type", "type", entities.map { NamedRow(it.id, it.type, it.checksum, it.updatedAt) })

    // ── language ─────────────────────────────────────────────────────────────────

    fun upsertLanguages(entities: List<IngestLanguageEntity>) = batchUpsert("""
        INSERT INTO ingest.language (id, locale, name, native_name, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            locale = EXCLUDED.locale, name = EXCLUDED.name, native_name = EXCLUDED.native_name,
            checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableString(2, e.locale); setNullableString(3, e.englishName)
        setNullableString(4, e.nativeName); setUUID(5, e.checksum); setNullableLong(6, e.updatedAt)
    }

    // ── region ───────────────────────────────────────────────────────────────────

    fun upsertRegions(entities: List<IngestRegionEntity>) = batchUpsert("""
        INSERT INTO ingest.region (id, name, identifier, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, identifier = EXCLUDED.identifier,
            checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableString(2, e.name); setNullableString(3, e.identifier)
        setUUID(4, e.checksum); setNullableLong(5, e.updatedAt)
    }

    // ── release_date_region ──────────────────────────────────────────────────────

    fun upsertReleaseDateRegions(entities: List<IngestReleaseDateRegionEntity>) = batchUpsert("""
        INSERT INTO ingest.release_date_region (id, region, checksum, updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            region = EXCLUDED.region, checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableString(2, e.regionName); setUUID(3, e.checksum); setNullableLong(4, e.updatedAt)
    }

    // ── release_date_status ──────────────────────────────────────────────────────

    fun upsertReleaseDateStatuses(entities: List<IngestReleaseDateStatusEntity>) = batchUpsert("""
        INSERT INTO ingest.release_date_status (id, name, description, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, description = EXCLUDED.description,
            checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableString(2, e.name); setNullableString(3, e.description)
        setUUID(4, e.checksum); setNullableLong(5, e.updatedAt)
    }

    // ── platform_logo ────────────────────────────────────────────────────────────

    fun upsertPlatformLogos(entities: List<IngestPlatformLogoEntity>) = batchUpsert("""
        INSERT INTO ingest.platform_logo (id, image_id, url, checksum)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            image_id = EXCLUDED.image_id, url = EXCLUDED.url, checksum = EXCLUDED.checksum
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableString(2, e.imageId); setNullableString(3, e.url); setUUID(4, e.checksum)
    }

    // ── game ─────────────────────────────────────────────────────────────────────

    fun upsertGames(entities: List<IngestGameEntity>) = batchUpsert("""
        INSERT INTO ingest.game (
            id, name, slug, summary, storyline, first_release_date,
            release_dates, platforms, game_status, game_type, language_supports,
            genres, themes, player_perspectives, game_modes, keywords, involved_companies,
            parent_game, remakes, remasters, ports, standalone_expansions, similar_games,
            cover, artworks, screenshots, videos, websites, alternative_names,
            game_localizations, tags, checksum, updated_at
        ) VALUES (
            ?,?,?,?,?,?,  ?,?,?,?,?,  ?,?,?,?,?,?,  ?,?,?,?,?,?,  ?,?,?,?,?,?,  ?,?,?,?
        )
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, slug = EXCLUDED.slug,
            summary = EXCLUDED.summary, storyline = EXCLUDED.storyline,
            first_release_date = EXCLUDED.first_release_date,
            release_dates = EXCLUDED.release_dates, platforms = EXCLUDED.platforms,
            game_status = EXCLUDED.game_status, game_type = EXCLUDED.game_type,
            language_supports = EXCLUDED.language_supports,
            genres = EXCLUDED.genres, themes = EXCLUDED.themes,
            player_perspectives = EXCLUDED.player_perspectives,
            game_modes = EXCLUDED.game_modes, keywords = EXCLUDED.keywords,
            involved_companies = EXCLUDED.involved_companies,
            parent_game = EXCLUDED.parent_game, remakes = EXCLUDED.remakes,
            remasters = EXCLUDED.remasters, ports = EXCLUDED.ports,
            standalone_expansions = EXCLUDED.standalone_expansions,
            similar_games = EXCLUDED.similar_games,
            cover = EXCLUDED.cover, artworks = EXCLUDED.artworks,
            screenshots = EXCLUDED.screenshots, videos = EXCLUDED.videos,
            websites = EXCLUDED.websites, alternative_names = EXCLUDED.alternative_names,
            game_localizations = EXCLUDED.game_localizations, tags = EXCLUDED.tags,
            checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableString(2, e.name); setNullableString(3, e.slug)
        setNullableString(4, e.summary); setNullableString(5, e.storyline)
        setNullableLong(6, e.firstReleaseDate)
        setLongArray(7, e.releaseDateIds); setLongArray(8, e.platformIds)
        setNullableLong(9, e.gameStatusId); setNullableLong(10, e.gameTypeId)
        setLongArray(11, e.languageSupportIds)
        setLongArray(12, e.genreIds); setLongArray(13, e.themeIds)
        setLongArray(14, e.playerPerspectiveIds); setLongArray(15, e.gameModeIds)
        setLongArray(16, e.keywordIds); setLongArray(17, e.involvedCompanyIds)
        setNullableLong(18, e.parentGameId)
        setLongArray(19, e.remakeIds); setLongArray(20, e.remasterIds)
        setLongArray(21, e.portIds); setLongArray(22, e.standaloneExpansionIds)
        setLongArray(23, e.similarGameIds)
        setNullableLong(24, e.coverId)
        setLongArray(25, e.artworkIds); setLongArray(26, e.screenshotIds)
        setLongArray(27, e.videoIds); setLongArray(28, e.websiteIds)
        setLongArray(29, e.alternativeNameIds); setLongArray(30, e.gameLocalizationIds)
        setLongArray(31, e.tagNumbers)
        setUUID(32, e.checksum); setNullableLong(33, e.updatedAt)
    }

    // ── release_date ──────────────────────────────────────────────────────────────

    fun upsertReleaseDates(entities: List<IngestReleaseDateEntity>) = batchUpsert("""
        INSERT INTO ingest.release_date (id, game, platform, release_region, status, date, y, m, human, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, platform = EXCLUDED.platform,
            release_region = EXCLUDED.release_region, status = EXCLUDED.status,
            date = EXCLUDED.date, y = EXCLUDED.y, m = EXCLUDED.m,
            human = EXCLUDED.human, checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId)
        setNullableLong(3, e.platformId); setNullableLong(4, e.releaseRegionId); setNullableLong(5, e.statusId)
        setNullableLong(6, e.releaseTimestamp); setNullableInt(7, e.releaseYear); setNullableInt(8, e.releaseMonth)
        setNullableString(9, e.humanReadableDate); setUUID(10, e.checksum); setNullableLong(11, e.updatedAt)
    }

    // ── platform ──────────────────────────────────────────────────────────────────

    fun upsertPlatforms(entities: List<IngestPlatformEntity>) = batchUpsert("""
        INSERT INTO ingest.platform (id, name, abbreviation, alternative_name, platform_logo, platform_type, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, abbreviation = EXCLUDED.abbreviation,
            alternative_name = EXCLUDED.alternative_name, platform_logo = EXCLUDED.platform_logo,
            platform_type = EXCLUDED.platform_type, checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableString(2, e.name)
        setNullableString(3, e.abbreviation); setNullableString(4, e.alternativeName)
        setNullableLong(5, e.platformLogoId); setNullableLong(6, e.platformTypeId)
        setUUID(7, e.checksum); setNullableLong(8, e.updatedAt)
    }

    // ── company ───────────────────────────────────────────────────────────────────

    fun upsertCompanies(entities: List<IngestCompanyEntity>) = batchUpsert("""
        INSERT INTO ingest.company (id, name, parent, changed_company_id, developed, published, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name, parent = EXCLUDED.parent,
            changed_company_id = EXCLUDED.changed_company_id,
            developed = EXCLUDED.developed, published = EXCLUDED.published,
            checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableString(2, e.name)
        setNullableLong(3, e.parentCompanyId); setNullableLong(4, e.changedCompanyId)
        setLongArray(5, e.developedGameIds); setLongArray(6, e.publishedGameIds)
        setUUID(7, e.checksum); setNullableLong(8, e.updatedAt)
    }

    // ── involved_company ──────────────────────────────────────────────────────────

    fun upsertInvolvedCompanies(entities: List<IngestInvolvedCompanyEntity>) = batchUpsert("""
        INSERT INTO ingest.involved_company (id, game, company, developer, publisher, porting, supporting, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, company = EXCLUDED.company,
            developer = EXCLUDED.developer, publisher = EXCLUDED.publisher,
            porting = EXCLUDED.porting, supporting = EXCLUDED.supporting,
            checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId); setLong(3, e.companyId)
        setNullableBoolean(4, e.developer); setNullableBoolean(5, e.publisher)
        setNullableBoolean(6, e.porting); setNullableBoolean(7, e.supporting)
        setUUID(8, e.checksum); setNullableLong(9, e.updatedAt)
    }

    // ── language_support ──────────────────────────────────────────────────────────

    fun upsertLanguageSupports(entities: List<IngestLanguageSupportEntity>) = batchUpsert("""
        INSERT INTO ingest.language_support (id, game, language, language_support_type, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, language = EXCLUDED.language,
            language_support_type = EXCLUDED.language_support_type,
            checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId); setLong(3, e.languageId)
        setNullableLong(4, e.languageSupportTypeId); setUUID(5, e.checksum); setNullableLong(6, e.updatedAt)
    }

    // ── game_localization ─────────────────────────────────────────────────────────

    fun upsertGameLocalizations(entities: List<IngestGameLocalizationEntity>) = batchUpsert("""
        INSERT INTO ingest.game_localization (id, game, region, name, cover, checksum, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, region = EXCLUDED.region, name = EXCLUDED.name,
            cover = EXCLUDED.cover, checksum = EXCLUDED.checksum, updated_at = EXCLUDED.updated_at
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId); setNullableLong(3, e.regionId); setNullableString(4, e.name)
        setNullableLong(5, e.coverId); setUUID(6, e.checksum); setNullableLong(7, e.updatedAt)
    }

    // ── cover ─────────────────────────────────────────────────────────────────────

    fun upsertCovers(entities: List<IngestCoverEntity>) = batchUpsert("""
        INSERT INTO ingest.cover (id, game, game_localization, image_id, url, checksum)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, game_localization = EXCLUDED.game_localization,
            image_id = EXCLUDED.image_id, url = EXCLUDED.url, checksum = EXCLUDED.checksum
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setNullableLong(2, e.gameId); setNullableLong(3, e.gameLocalizationId)
        setNullableString(4, e.imageId); setNullableString(5, e.url); setUUID(6, e.checksum)
    }

    // ── artwork ───────────────────────────────────────────────────────────────────

    fun upsertArtworks(entities: List<IngestArtworkEntity>) = batchUpsert("""
        INSERT INTO ingest.artwork (id, game, image_id, url, checksum)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, image_id = EXCLUDED.image_id,
            url = EXCLUDED.url, checksum = EXCLUDED.checksum
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId)
        setNullableString(3, e.imageId); setNullableString(4, e.url); setUUID(5, e.checksum)
    }

    // ── screenshot ────────────────────────────────────────────────────────────────

    fun upsertScreenshots(entities: List<IngestScreenshotEntity>) = batchUpsert("""
        INSERT INTO ingest.screenshot (id, game, image_id, url, checksum)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, image_id = EXCLUDED.image_id,
            url = EXCLUDED.url, checksum = EXCLUDED.checksum
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId)
        setNullableString(3, e.imageId); setNullableString(4, e.url); setUUID(5, e.checksum)
    }

    // ── game_video ────────────────────────────────────────────────────────────────

    fun upsertGameVideos(entities: List<IngestGameVideoEntity>) = batchUpsert("""
        INSERT INTO ingest.game_video (id, game, name, video_id, checksum)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, name = EXCLUDED.name,
            video_id = EXCLUDED.video_id, checksum = EXCLUDED.checksum
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId)
        setNullableString(3, e.name); setNullableString(4, e.videoId); setUUID(5, e.checksum)
    }

    // ── website ───────────────────────────────────────────────────────────────────

    fun upsertWebsites(entities: List<IngestWebsiteEntity>) = batchUpsert("""
        INSERT INTO ingest.website (id, game, type, url, trusted, checksum)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, type = EXCLUDED.type, url = EXCLUDED.url,
            trusted = EXCLUDED.trusted, checksum = EXCLUDED.checksum
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId)
        setNullableLong(3, e.typeId); setNullableString(4, e.url)
        setNullableBoolean(5, e.trusted); setUUID(6, e.checksum)
    }

    // ── alternative_name ──────────────────────────────────────────────────────────

    fun upsertAlternativeNames(entities: List<IngestAlternativeNameEntity>) = batchUpsert("""
        INSERT INTO ingest.alternative_name (id, game, name, comment, checksum)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            game = EXCLUDED.game, name = EXCLUDED.name,
            comment = EXCLUDED.comment, checksum = EXCLUDED.checksum
    """.trimIndent(), entities) { e ->
        setLong(1, e.id); setLong(2, e.gameId)
        setNullableString(3, e.name); setNullableString(4, e.comment); setUUID(5, e.checksum)
    }

    // ── sync log ──────────────────────────────────────────────────────────────────

    fun insertSyncLog(syncId: UUID, startedAt: Instant) {
        jdbc.update(
            "INSERT INTO ingest.sync_log (sync_id, started_at) VALUES (?, ?)",
            syncId, Timestamp.from(startedAt)
        )
    }

    fun finishSyncLog(syncId: UUID, finishedAt: Instant, status: String) {
        jdbc.update(
            "UPDATE ingest.sync_log SET finished_at = ?, status = ? WHERE sync_id = ?",
            Timestamp.from(finishedAt), status, syncId
        )
    }

    fun saveSyncTableLog(stats: TableSyncStats) {
        jdbc.update(
            """INSERT INTO ingest.sync_table_log
               (sync_id, table_name, fetched, upserted, errors, started_at, finished_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            stats.syncId, stats.tableName,
            stats.fetched, stats.upserted, stats.parseErrors.size,
            Timestamp.from(stats.startedAt), Timestamp.from(stats.finishedAt ?: Instant.now())
        )
    }

    fun batchSaveSyncParseErrors(stats: TableSyncStats) {
        if (stats.parseErrors.isEmpty()) return
        batchUpsert(
            """INSERT INTO ingest.sync_parse_error
               (sync_id, table_name, record_id, raw_json, error_msg)
               VALUES (?, ?, ?, ?, ?)""",
            stats.parseErrors
        ) { e ->
            setObject(1, stats.syncId)
            setString(2, stats.tableName)
            setNullableLong(3, e.recordId)
            setJsonb(4, e.rawJson)
            setString(5, e.errorMsg)
        }
    }
}
