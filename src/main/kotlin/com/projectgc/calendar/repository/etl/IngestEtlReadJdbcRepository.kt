package com.projectgc.calendar.repository.etl

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet

@Repository
class IngestEtlReadJdbcRepository(
    @Qualifier("ingestReadJdbcTemplate")
    private val jdbc: JdbcTemplate,
) {
    companion object {
        private const val PROJECTION_QUERY_CHUNK_SIZE = 500
        private val SERVICE_RELEASE_REGION_IDS_SQL =
            ServiceEtlDataScope.idList(ServiceEtlDataScope.serviceReleaseRegionIds)
        private val SERVICE_PLATFORM_IDS_SQL =
            ServiceEtlDataScope.idList(ServiceEtlDataScope.servicePlatformIds)
        private val FIXED_LOCALIZATION_REGION_IDS_SQL =
            ServiceEtlDataScope.idList(ServiceEtlDataScope.fixedLocalizationRegionIds)
        private val FIXED_LANGUAGE_IDS_SQL =
            ServiceEtlDataScope.idList(ServiceEtlDataScope.fixedLanguageIds)
    }

    fun findServiceCandidateGameIds(): List<Long> =
        jdbc.query(
            """
            SELECT DISTINCT g.id
            FROM ingest.game g
            JOIN ingest.release_date rd ON rd.game = g.id
            WHERE ${serviceReleaseDatePredicate("rd")}
              AND NULLIF(BTRIM(g.name), '') IS NOT NULL
            ORDER BY g.id
            """.trimIndent(),
        ) { rs, _ -> rs.getLong("id") }

    fun findAffectedGameIdsFromGames(cursorFrom: Long): Set<Long> =
        jdbc.query(
            """
            SELECT id
            FROM ingest.game
            WHERE updated_at > ?
            ORDER BY id
            """.trimIndent(),
            { rs, _ -> rs.getLong("id") },
            cursorFrom,
        ).toCollection(linkedSetOf())

    fun findAffectedGameIdsFromReleaseDates(cursorFrom: Long): Set<Long> =
        findDistinctGameIdsByUpdatedAt("release_date", cursorFrom)

    fun findAffectedGameIdsFromInvolvedCompanies(cursorFrom: Long): Set<Long> =
        findDistinctGameIdsByUpdatedAt("involved_company", cursorFrom)

    fun findAffectedGameIdsFromLanguageSupports(cursorFrom: Long): Set<Long> =
        findDistinctGameIdsByUpdatedAt("language_support", cursorFrom)

    fun findAffectedGameIdsFromGameLocalizations(cursorFrom: Long): Set<Long> =
        findDistinctGameIdsByUpdatedAt("game_localization", cursorFrom)

    fun findAffectedGameIdsFromGameUpdatedAt(cursorFrom: Long): Set<Long> =
        findAffectedGameIdsFromGames(cursorFrom)

    fun loadServiceGameStatuses(): List<NamedDimensionRow> =
        loadNamedDimensionRowsByCandidateGameColumn(
            sourceTable = "game_status",
            sourceValueColumn = "status",
            gameColumn = "game_status",
        )

    fun loadServiceGameTypes(): List<NamedDimensionRow> =
        loadNamedDimensionRowsByCandidateGameColumn(
            sourceTable = "game_type",
            sourceValueColumn = "type",
            gameColumn = "game_type",
        )

    fun loadServiceLanguages(): List<LanguageRow> =
        jdbc.query(
            """
            SELECT id, locale, name, native_name
            FROM ingest.language l
            WHERE ${serviceLanguagePredicate("l")}
            ORDER BY id
            """.trimIndent(),
        ) { rs, _ ->
            LanguageRow(
                id = rs.getLong("id"),
                locale = rs.getString("locale"),
                name = rs.getString("name"),
                nativeName = rs.getString("native_name"),
            )
        }

    fun loadServiceRegions(): List<RegionRow> =
        jdbc.query(
            """
            SELECT id, name, identifier
            FROM ingest.region r
            WHERE ${serviceLocalizationRegionPredicate("r")}
            ORDER BY id
            """.trimIndent(),
        ) { rs, _ ->
            RegionRow(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                identifier = rs.getString("identifier"),
            )
        }

    fun loadServiceReleaseRegions(): List<NamedDimensionRow> =
        jdbc.query(
            """
            SELECT id, region AS value
            FROM ingest.release_date_region
            WHERE id IN ($SERVICE_RELEASE_REGION_IDS_SQL)
               OR LOWER(COALESCE(region, '')) IN ('worldwide', 'korea')
            ORDER BY id
            """.trimIndent(),
        ) { rs, _ ->
            NamedDimensionRow(
                id = rs.getLong("id"),
                value = rs.getString("value"),
            )
        }

    fun loadServiceReleaseStatuses(): List<ReleaseStatusRow> =
        jdbc.query(
            """
            SELECT id, name, description
            FROM ingest.release_date_status
            WHERE id IN (
                SELECT DISTINCT rd.status
                FROM ingest.release_date rd
                WHERE ${serviceReleaseDatePredicate("rd")}
                  AND rd.status IS NOT NULL
            )
            ORDER BY id
            """.trimIndent(),
        ) { rs, _ ->
            ReleaseStatusRow(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                description = rs.getString("description"),
            )
        }

    fun loadServiceGenres(): List<NamedDimensionRow> =
        loadNamedDimensionRowsByCandidateGameArray(
            sourceTable = "genre",
            sourceValueColumn = "name",
            gameArrayColumn = "genres",
        )

    fun loadServiceWebsiteTypes(): List<NamedDimensionRow> =
        jdbc.query(
            """
            WITH candidate_games AS (
                ${serviceCandidateGameIdsSql()}
            ),
            referenced AS (
                SELECT DISTINCT w.type AS id
                FROM ingest.website w
                JOIN candidate_games cg ON cg.id = w.game
                WHERE w.type IS NOT NULL
                  AND COALESCE(w.trusted, FALSE) = TRUE
                  AND NULLIF(BTRIM(w.url), '') IS NOT NULL
            )
            SELECT wt.id, wt.type AS value
            FROM ingest.website_type wt
            JOIN referenced r ON r.id = wt.id
            ORDER BY wt.id
            """.trimIndent(),
        ) { rs, _ ->
            NamedDimensionRow(
                id = rs.getLong("id"),
                value = rs.getString("value"),
            )
        }

    fun loadServicePlatforms(): List<PlatformSyncRow> =
        jdbc.query(
            """
            WITH service_releases AS (
                SELECT DISTINCT rd.platform AS id
                FROM ingest.release_date rd
                WHERE ${serviceReleaseDatePredicate("rd")}
                  AND rd.platform IS NOT NULL
            )
            SELECT p.id, p.name, p.abbreviation, p.alternative_name, p.platform_logo, p.platform_type
            FROM ingest.platform p
            JOIN service_releases sr ON sr.id = p.id
            ORDER BY p.id
            """.trimIndent(),
        ) { rs, _ ->
            PlatformSyncRow(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                abbreviation = rs.getString("abbreviation"),
                alternativeName = rs.getString("alternative_name"),
                logoId = null,
                typeId = null,
            )
        }

    fun loadServiceCompanies(): List<CompanySyncRow> =
        jdbc.query(
            """
            WITH candidate_games AS (
                ${serviceCandidateGameIdsSql()}
            ),
            referenced AS (
                SELECT DISTINCT ic.company AS id
                FROM ingest.involved_company ic
                JOIN candidate_games cg ON cg.id = ic.game
                WHERE ic.company IS NOT NULL
                  AND COALESCE(ic.developer, FALSE) = TRUE
            )
            SELECT c.id, c.name, c.parent, c.changed_company_id
            FROM ingest.company c
            JOIN referenced r ON r.id = c.id
            ORDER BY c.id
            """.trimIndent(),
        ) { rs, _ ->
            CompanySyncRow(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                parentCompanyId = null,
                mergedIntoCompanyId = null,
            )
        }

    fun loadGameProjectionRows(gameIds: Set<Long>): List<GameProjectionRow> =
        queryByLongIdChunks(
            ids = gameIds,
            sqlBuilder = { placeholders ->
                """
                SELECT id, slug, name, summary, storyline, first_release_date, game_status, game_type, updated_at, tags
                FROM ingest.game
                WHERE id IN ($placeholders)
                ORDER BY id
                """.trimIndent()
            },
            rowMapper = { rs, _ -> rs.toGameProjectionRow() },
        )

    fun loadServiceGameLocalizationProjectionRows(gameIds: Set<Long>): List<GameLocalizationProjectionRow> =
        queryByLongIdChunks(
            ids = gameIds,
            sqlBuilder = { placeholders ->
                """
                SELECT gl.id, gl.game, gl.region, gl.name
                FROM ingest.game_localization gl
                JOIN ingest.region r ON r.id = gl.region
                WHERE gl.game IN ($placeholders)
                  AND ${serviceLocalizationRegionPredicate("r")}
                ORDER BY gl.game, gl.id
                """.trimIndent()
            },
            rowMapper = { rs, _ -> rs.toGameLocalizationProjectionRow() },
        )

    fun loadServiceGameReleaseProjectionRows(gameIds: Set<Long>): List<GameReleaseProjectionRow> =
        queryByLongIdChunks(
            ids = gameIds,
            sqlBuilder = { placeholders ->
                """
                SELECT rd.id, rd.game, rd.platform, rd.release_region, rd.status, rd.date, rd.y, rd.m, rd.human
                FROM ingest.release_date rd
                WHERE rd.game IN ($placeholders)
                  AND ${serviceReleaseDatePredicate("rd")}
                ORDER BY rd.game, rd.id
                """.trimIndent()
            },
            rowMapper = { rs, _ -> rs.toGameReleaseProjectionRow() },
        )

    fun loadServiceGameLanguageProjectionRows(gameIds: Set<Long>): List<GameLanguageProjectionRow> =
        loadGameLanguageProjectionRowsInternal(
            gameIds = gameIds,
            languageFilter = serviceLanguagePredicate("l"),
        )

    fun loadGameArrayProjectionRows(
        gameIds: Set<Long>,
        sourceColumn: String,
    ): List<GameDimensionProjectionRow> =
        loadGameArrayProjectionRowsInternal(
            gameIds = gameIds,
            sourceColumn = sourceColumn,
        )

    fun loadServiceGameCompanyProjectionRows(gameIds: Set<Long>): List<GameCompanyProjectionRow> =
        loadGameCompanyProjectionRowsInternal(
            gameIds = gameIds,
            developerOnly = true,
        )

    fun loadGameRelationProjectionRows(gameIds: Set<Long>): List<GameRelationProjectionRow> =
        loadGameRelationProjectionRowsInternal(gameIds)

    fun loadCoverProjectionRows(gameIds: Set<Long>): List<CoverProjectionRow> =
        loadCoverProjectionRowsInternal(gameIds)

    fun loadServiceGameVideoProjectionRows(gameIds: Set<Long>): List<GameVideoProjectionRow> =
        loadGameVideoProjectionRowsInternal(
            gameIds = gameIds,
            requireVideoId = true,
        )

    fun loadServiceWebsiteProjectionRows(gameIds: Set<Long>): List<WebsiteProjectionRow> =
        loadWebsiteProjectionRowsInternal(
            gameIds = gameIds,
            trustedOnly = true,
        )

    private fun loadNamedDimensionRowsByCandidateGameColumn(
        sourceTable: String,
        sourceValueColumn: String,
        gameColumn: String,
    ): List<NamedDimensionRow> =
        jdbc.query(
            """
            WITH candidate_games AS (
                ${serviceCandidateGameIdsSql()}
            ),
            referenced AS (
                SELECT DISTINCT g.$gameColumn AS id
                FROM ingest.game g
                JOIN candidate_games cg ON cg.id = g.id
                WHERE g.$gameColumn IS NOT NULL
            )
            SELECT d.id, d.$sourceValueColumn AS value
            FROM ingest.$sourceTable d
            JOIN referenced r ON r.id = d.id
            ORDER BY d.id
            """.trimIndent(),
        ) { rs, _ ->
            NamedDimensionRow(
                id = rs.getLong("id"),
                value = rs.getString("value"),
            )
        }

    private fun loadNamedDimensionRowsByCandidateGameArray(
        sourceTable: String,
        sourceValueColumn: String,
        gameArrayColumn: String,
    ): List<NamedDimensionRow> =
        jdbc.query(
            """
            WITH candidate_games AS (
                ${serviceCandidateGameIdsSql()}
            ),
            referenced AS (
                SELECT DISTINCT ref.dimension_id AS id
                FROM ingest.game g
                JOIN candidate_games cg ON cg.id = g.id
                CROSS JOIN LATERAL unnest(COALESCE(g.$gameArrayColumn, ARRAY[]::BIGINT[])) AS ref(dimension_id)
            )
            SELECT d.id, d.$sourceValueColumn AS value
            FROM ingest.$sourceTable d
            JOIN referenced r ON r.id = d.id
            ORDER BY d.id
            """.trimIndent(),
        ) { rs, _ ->
            NamedDimensionRow(
                id = rs.getLong("id"),
                value = rs.getString("value"),
            )
        }

    private fun findDistinctGameIdsByUpdatedAt(tableName: String, cursorFrom: Long): Set<Long> =
        jdbc.query(
            """
            SELECT DISTINCT game
            FROM ingest.$tableName
            WHERE updated_at > ?
              AND game IS NOT NULL
            ORDER BY game
            """.trimIndent(),
            { rs, _ -> rs.getLong("game") },
            cursorFrom,
        ).toCollection(linkedSetOf())

    private fun loadGameLanguageProjectionRowsInternal(
        gameIds: Set<Long>?,
        languageFilter: String? = null,
    ): List<GameLanguageProjectionRow> =
        queryByOptionalGameIds(
            gameIds = gameIds,
            sqlBuilder = { filterClause ->
                val languageFilterClause = languageFilter?.let { "AND $it" }.orEmpty()
                """
                SELECT
                    ls.game AS game_id,
                    ls.language AS language_id,
                    bool_or(lower(COALESCE(lst.name, '')) = 'audio') AS supports_audio,
                    bool_or(lower(COALESCE(lst.name, '')) IN ('subtitles', 'subtitle')) AS supports_subtitles,
                    bool_or(lower(COALESCE(lst.name, '')) = 'interface') AS supports_interface
                FROM ingest.language_support ls
                LEFT JOIN ingest.language_support_type lst ON lst.id = ls.language_support_type
                LEFT JOIN ingest.language l ON l.id = ls.language
                WHERE ls.language IS NOT NULL
                  $filterClause
                  $languageFilterClause
                GROUP BY ls.game, ls.language
                HAVING bool_or(lower(COALESCE(lst.name, '')) = 'audio')
                    OR bool_or(lower(COALESCE(lst.name, '')) IN ('subtitles', 'subtitle'))
                    OR bool_or(lower(COALESCE(lst.name, '')) = 'interface')
                ORDER BY ls.game, ls.language
                """.trimIndent()
            },
        ) { rs, _ ->
            GameLanguageProjectionRow(
                gameId = rs.getLong("game_id"),
                languageId = rs.getLong("language_id"),
                supportsAudio = rs.getBoolean("supports_audio"),
                supportsSubtitles = rs.getBoolean("supports_subtitles"),
                supportsInterface = rs.getBoolean("supports_interface"),
            )
        }

    private fun loadGameArrayProjectionRowsInternal(
        gameIds: Set<Long>?,
        sourceColumn: String,
    ): List<GameDimensionProjectionRow> =
        queryByOptionalGameIds(
            gameIds = gameIds,
            filterColumn = "id",
            sqlBuilder = { filterClause ->
                """
                WITH filtered_games AS (
                    SELECT id, $sourceColumn
                    FROM ingest.game
                    WHERE 1 = 1
                      $filterClause
                )
                SELECT DISTINCT game_id, dimension_id
                FROM (
                    SELECT fg.id AS game_id, ref.dimension_id
                    FROM filtered_games fg
                    CROSS JOIN LATERAL unnest(COALESCE(fg.$sourceColumn, ARRAY[]::BIGINT[])) AS ref(dimension_id)
                ) rows
                ORDER BY game_id, dimension_id
                """.trimIndent()
            },
        ) { rs, _ ->
            GameDimensionProjectionRow(
                gameId = rs.getLong("game_id"),
                dimensionId = rs.getLong("dimension_id"),
            )
        }

    private fun loadGameCompanyProjectionRowsInternal(
        gameIds: Set<Long>?,
        developerOnly: Boolean = false,
    ): List<GameCompanyProjectionRow> =
        queryByOptionalGameIds(
            gameIds = gameIds,
            sqlBuilder = { filterClause ->
                val developerFilterClause = if (developerOnly) {
                    "AND COALESCE(ic.developer, FALSE) = TRUE"
                } else {
                    ""
                }
                """
                SELECT
                    ic.game AS game_id,
                    ic.company AS company_id,
                    bool_or(COALESCE(ic.developer, FALSE)) AS is_developer,
                    bool_or(COALESCE(ic.publisher, FALSE)) AS is_publisher,
                    bool_or(COALESCE(ic.porting, FALSE)) AS is_porting,
                    bool_or(COALESCE(ic.supporting, FALSE)) AS is_supporting
                FROM ingest.involved_company ic
                WHERE ic.company IS NOT NULL
                  $filterClause
                  $developerFilterClause
                GROUP BY ic.game, ic.company
                HAVING bool_or(COALESCE(ic.developer, FALSE))
                    OR bool_or(COALESCE(ic.publisher, FALSE))
                    OR bool_or(COALESCE(ic.porting, FALSE))
                    OR bool_or(COALESCE(ic.supporting, FALSE))
                ORDER BY ic.game, ic.company
                """.trimIndent()
            },
        ) { rs, _ ->
            GameCompanyProjectionRow(
                gameId = rs.getLong("game_id"),
                companyId = rs.getLong("company_id"),
                isDeveloper = rs.getBoolean("is_developer"),
                isPublisher = rs.getBoolean("is_publisher"),
                isPorting = rs.getBoolean("is_porting"),
                isSupporting = rs.getBoolean("is_supporting"),
            )
        }

    private fun loadGameRelationProjectionRowsInternal(gameIds: Set<Long>?): List<GameRelationProjectionRow> =
        queryByOptionalGameIds(
            gameIds = gameIds,
            filterColumn = "id",
            sqlBuilder = { filterClause ->
                """
                WITH filtered_games AS (
                    SELECT
                        id,
                        parent_game,
                        remakes,
                        remasters,
                        ports,
                        standalone_expansions,
                        similar_games
                    FROM ingest.game
                    WHERE 1 = 1
                      $filterClause
                )
                SELECT game_id, related_game_id, relation_type
                FROM (
                    SELECT id AS game_id, parent_game AS related_game_id, 'PARENT' AS relation_type
                    FROM filtered_games
                    WHERE parent_game IS NOT NULL
                    UNION
                    SELECT fg.id AS game_id, rel.related_game_id, 'REMAKE' AS relation_type
                    FROM filtered_games fg
                    CROSS JOIN LATERAL unnest(COALESCE(fg.remakes, ARRAY[]::BIGINT[])) AS rel(related_game_id)
                    UNION
                    SELECT fg.id AS game_id, rel.related_game_id, 'REMASTER' AS relation_type
                    FROM filtered_games fg
                    CROSS JOIN LATERAL unnest(COALESCE(fg.remasters, ARRAY[]::BIGINT[])) AS rel(related_game_id)
                    UNION
                    SELECT fg.id AS game_id, rel.related_game_id, 'PORT' AS relation_type
                    FROM filtered_games fg
                    CROSS JOIN LATERAL unnest(COALESCE(fg.ports, ARRAY[]::BIGINT[])) AS rel(related_game_id)
                    UNION
                    SELECT fg.id AS game_id, rel.related_game_id, 'STANDALONE_EXPANSION' AS relation_type
                    FROM filtered_games fg
                    CROSS JOIN LATERAL unnest(COALESCE(fg.standalone_expansions, ARRAY[]::BIGINT[])) AS rel(related_game_id)
                    UNION
                    SELECT fg.id AS game_id, rel.related_game_id, 'SIMILAR' AS relation_type
                    FROM filtered_games fg
                    CROSS JOIN LATERAL unnest(COALESCE(fg.similar_games, ARRAY[]::BIGINT[])) AS rel(related_game_id)
                ) rows
                ORDER BY game_id, relation_type, related_game_id
                """.trimIndent()
            },
        ) { rs, _ ->
            GameRelationProjectionRow(
                gameId = rs.getLong("game_id"),
                relatedGameId = rs.getLong("related_game_id"),
                relationType = rs.getString("relation_type"),
            )
        }

    private fun loadCoverProjectionRowsInternal(gameIds: Set<Long>?): List<CoverProjectionRow> =
        if (gameIds == null) {
            jdbc.query(
                """
                WITH cover_rows AS (
                    SELECT
                        c.id,
                        COALESCE(c.game, gl.game) AS game_id,
                        c.game_localization AS game_localization_id,
                        c.image_id,
                        c.url,
                        COALESCE(g.cover = c.id, FALSE) AS is_main
                    FROM ingest.cover c
                    LEFT JOIN ingest.game_localization gl ON gl.id = c.game_localization
                    LEFT JOIN ingest.game g ON g.id = COALESCE(c.game, gl.game)
                )
                SELECT id, game_id, game_localization_id, image_id, url, is_main
                FROM cover_rows
                WHERE game_id IS NOT NULL
                ORDER BY game_id, id
                """.trimIndent(),
            ) { rs, _ -> rs.toCoverProjectionRow() }
        } else {
            queryByLongIdChunks(
                ids = gameIds,
                sqlBuilder = { placeholders ->
                    """
                    WITH cover_rows AS (
                        SELECT
                            c.id,
                            COALESCE(c.game, gl.game) AS game_id,
                            c.game_localization AS game_localization_id,
                            c.image_id,
                            c.url,
                            COALESCE(g.cover = c.id, FALSE) AS is_main
                        FROM ingest.cover c
                        LEFT JOIN ingest.game_localization gl ON gl.id = c.game_localization
                        LEFT JOIN ingest.game g ON g.id = COALESCE(c.game, gl.game)
                    )
                    SELECT id, game_id, game_localization_id, image_id, url, is_main
                    FROM cover_rows
                    WHERE game_id IN ($placeholders)
                    ORDER BY game_id, id
                    """.trimIndent()
                },
                rowMapper = { rs, _ -> rs.toCoverProjectionRow() },
            )
        }

    private fun loadGameVideoProjectionRowsInternal(
        gameIds: Set<Long>?,
        requireVideoId: Boolean = false,
    ): List<GameVideoProjectionRow> =
        queryByOptionalGameIds(
            gameIds = gameIds,
            sqlBuilder = { filterClause ->
                val videoIdFilterClause = if (requireVideoId) {
                    "AND NULLIF(BTRIM(video_id), '') IS NOT NULL"
                } else {
                    ""
                }
                """
                SELECT id, game AS game_id, name, video_id
                FROM ingest.game_video
                WHERE game IS NOT NULL
                  $filterClause
                  $videoIdFilterClause
                ORDER BY game, id
                """.trimIndent()
            },
        ) { rs, _ -> rs.toGameVideoProjectionRow() }

    private fun loadWebsiteProjectionRowsInternal(
        gameIds: Set<Long>?,
        trustedOnly: Boolean = false,
    ): List<WebsiteProjectionRow> =
        queryByOptionalGameIds(
            gameIds = gameIds,
            sqlBuilder = { filterClause ->
                val trustedFilterClause = if (trustedOnly) {
                    "AND COALESCE(trusted, FALSE) = TRUE AND NULLIF(BTRIM(url), '') IS NOT NULL"
                } else {
                    ""
                }
                """
                SELECT id, game AS game_id, type AS type_id, url, COALESCE(trusted, FALSE) AS is_trusted
                FROM ingest.website
                WHERE game IS NOT NULL
                  $filterClause
                  $trustedFilterClause
                ORDER BY game, id
                """.trimIndent()
            },
        ) { rs, _ -> rs.toWebsiteProjectionRow() }

    private fun serviceCandidateGameIdsSql(): String =
        """
        SELECT DISTINCT g.id
        FROM ingest.game g
        JOIN ingest.release_date rd ON rd.game = g.id
        WHERE ${serviceReleaseDatePredicate("rd")}
          AND NULLIF(BTRIM(g.name), '') IS NOT NULL
        """.trimIndent()

    private fun serviceReleaseDatePredicate(alias: String): String =
        """
        $alias.game IS NOT NULL
          AND $alias.date IS NOT NULL
          AND $alias.release_region IN ($SERVICE_RELEASE_REGION_IDS_SQL)
          AND $alias.status IS DISTINCT FROM ${ServiceEtlDataScope.CANCELLED_RELEASE_STATUS_ID}
          AND ($alias.platform IS NULL OR $alias.platform IN ($SERVICE_PLATFORM_IDS_SQL))
          AND (
              $alias.release_region = ${ServiceEtlDataScope.KOREA_RELEASE_REGION_ID}
              OR NOT EXISTS (
                  SELECT 1
                  FROM ingest.release_date kr
                  WHERE kr.game = $alias.game
                    AND kr.platform IS NOT DISTINCT FROM $alias.platform
                    AND kr.date IS NOT NULL
                    AND kr.release_region = ${ServiceEtlDataScope.KOREA_RELEASE_REGION_ID}
                    AND kr.status IS DISTINCT FROM ${ServiceEtlDataScope.CANCELLED_RELEASE_STATUS_ID}
                    AND (kr.platform IS NULL OR kr.platform IN ($SERVICE_PLATFORM_IDS_SQL))
              )
          )
        """.trimIndent()

    private fun serviceLanguagePredicate(alias: String): String =
        """
        (
            $alias.id IN ($FIXED_LANGUAGE_IDS_SQL)
            OR LOWER(COALESCE($alias.locale, '')) LIKE 'ko%'
            OR LOWER(COALESCE($alias.locale, '')) LIKE 'en%'
            OR LOWER(COALESCE($alias.name, '')) IN ('korean', 'english')
            OR LOWER(COALESCE($alias.native_name, '')) IN ('korean', 'english')
        )
        """.trimIndent()

    private fun serviceLocalizationRegionPredicate(alias: String): String =
        """
        (
            $alias.id IN ($FIXED_LOCALIZATION_REGION_IDS_SQL)
            OR LOWER(COALESCE($alias.identifier, '')) LIKE 'ko%'
            OR LOWER(COALESCE($alias.identifier, '')) LIKE 'en%'
            OR LOWER(COALESCE($alias.name, '')) IN (
                'korea',
                'south korea',
                'korean',
                'english',
                'united states',
                'united kingdom'
            )
        )
        """.trimIndent()

    private fun ResultSet.toGameProjectionRow() =
        GameProjectionRow(
            id = getLong("id"),
            slug = getString("slug"),
            name = getString("name"),
            summary = getString("summary"),
            storyline = getString("storyline"),
            firstReleaseDateEpochSecond = getLong("first_release_date").takeIf { !wasNull() },
            statusId = getLong("game_status").takeIf { !wasNull() },
            typeId = getLong("game_type").takeIf { !wasNull() },
            sourceUpdatedAtEpochSecond = getLong("updated_at").takeIf { !wasNull() },
            tags = getNullableLongList("tags"),
        )

    private fun ResultSet.toGameLocalizationProjectionRow() =
        GameLocalizationProjectionRow(
            id = getLong("id"),
            gameId = getLong("game"),
            regionId = getLong("region").takeIf { !wasNull() },
            name = getString("name"),
        )

    private fun ResultSet.toGameReleaseProjectionRow() =
        GameReleaseProjectionRow(
            id = getLong("id"),
            gameId = getLong("game"),
            platformId = getLong("platform").takeIf { !wasNull() },
            regionId = getLong("release_region").takeIf { !wasNull() },
            statusId = getLong("status").takeIf { !wasNull() },
            releaseDateEpochSecond = getLong("date").takeIf { !wasNull() },
            year = getInt("y").takeIf { !wasNull() },
            month = getInt("m").takeIf { !wasNull() },
            dateHuman = getString("human"),
        )

    private fun ResultSet.toCoverProjectionRow() =
        CoverProjectionRow(
            id = getLong("id"),
            gameId = getLong("game_id"),
            gameLocalizationId = getLong("game_localization_id").takeIf { !wasNull() },
            imageId = getString("image_id"),
            url = getString("url"),
            isMain = getBoolean("is_main"),
        )

    private fun ResultSet.toGameVideoProjectionRow() =
        GameVideoProjectionRow(
            id = getLong("id"),
            gameId = getLong("game_id"),
            name = getString("name"),
            videoId = getString("video_id"),
        )

    private fun ResultSet.toWebsiteProjectionRow() =
        WebsiteProjectionRow(
            id = getLong("id"),
            gameId = getLong("game_id"),
            typeId = getLong("type_id").takeIf { !wasNull() },
            url = getString("url"),
            isTrusted = getBoolean("is_trusted"),
        )

    private fun <T> queryByOptionalGameIds(
        gameIds: Set<Long>?,
        filterColumn: String = "game",
        sqlBuilder: (String) -> String,
        rowMapper: (ResultSet, Int) -> T,
    ): List<T> =
        if (gameIds == null) {
            jdbc.query(sqlBuilder(""), rowMapper)
        } else {
            queryByLongIdChunks(
                ids = gameIds,
                sqlBuilder = { placeholders -> sqlBuilder("AND $filterColumn IN ($placeholders)") },
                rowMapper = rowMapper,
            )
        }

    private fun <T> queryByLongIdChunks(
        ids: Set<Long>,
        sqlBuilder: (String) -> String,
        rowMapper: (ResultSet, Int) -> T,
    ): List<T> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        val rows = mutableListOf<T>()
        ids.toList().chunked(PROJECTION_QUERY_CHUNK_SIZE).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            rows += jdbc.query(
                sqlBuilder(placeholders),
                rowMapper,
                *chunk.toTypedArray(),
            )
        }
        return rows
    }

    private fun ResultSet.getNullableLongList(columnName: String): List<Long>? =
        getArray(columnName)
            ?.array
            ?.let { raw ->
                when (raw) {
                    is Array<*> -> raw.mapNotNull { (it as? Number)?.toLong() }
                    else -> null
                }
            }
}
