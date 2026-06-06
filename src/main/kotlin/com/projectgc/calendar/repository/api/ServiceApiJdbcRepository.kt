package com.projectgc.calendar.repository.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.projectgc.calendar.model.api.GameDetailResponse
import com.projectgc.calendar.model.api.GameStatusResponse
import com.projectgc.calendar.model.api.GameTypeResponse
import com.projectgc.calendar.model.api.KoreanLanguageSupportResponse
import com.projectgc.calendar.model.api.PlatformResponse
import com.projectgc.calendar.model.api.ReleaseItemResponse
import com.projectgc.calendar.model.api.ReleaseRegionResponse
import com.projectgc.calendar.model.api.ReleaseStatusResponse
import com.projectgc.calendar.model.api.VideoResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Array
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Repository
class ServiceApiJdbcRepository(
    @Qualifier("serviceJdbcTemplate")
    jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : ServiceApiRepository {
    private val jdbc = NamedParameterJdbcTemplate(jdbcTemplate)

    override fun findReleases(
        startInclusive: Instant,
        endExclusive: Instant,
        platformIds: Set<Long>,
    ): List<ReleaseItemResponse> {
        // service ETL이 region∈{KOREA, GLOBAL}, status≠CANCELLED, platform∈servicePlatformIds,
        // 한국판 우선 fallback(같은 game/platform에 한국 row가 있으면 글로벌 row 제외)을 이미 적용한 뒤
        // service.game_release에 적재한다 (IngestEtlReadJdbcRepository.serviceReleaseDatePredicate).
        // 여기서 같은 필터를 다시 적용할 필요 없음.
        val params = MapSqlParameterSource()
            .addValue("startInclusive", Timestamp.from(startInclusive))
            .addValue("endExclusive", Timestamp.from(endExclusive))
            .addValue("koreaLocalizationRegionId", KOREA_LOCALIZATION_REGION_ID)
            .addValue("koreanLanguageId", KOREAN_LANGUAGE_ID)

        val platformFilterSql = if (platformIds.isEmpty()) {
            ""
        } else {
            params.addValue("platformIds", platformIds)
            "AND p.id IN (:platformIds)"
        }

        return jdbc.query(
            """
            SELECT
                ARRAY_AGG(gr.id ORDER BY gr.id) AS release_ids,
                gr.game_id,
                (gr.release_date AT TIME ZONE 'Asia/Seoul')::date AS release_date,
                COALESCE(BTRIM(ko_title.name), BTRIM(g.name)) AS title,
                BTRIM(g.name) AS default_title,
                gt.id AS game_type_id,
                gt.type AS game_type,
                rr.id AS region_id,
                rr.name AS region_name,
                rs.id AS release_status_id,
                rs.name AS release_status_name,
                g.hypes,
                g.follows,
                COALESCE(
                    JSONB_AGG(
                        DISTINCT JSONB_BUILD_OBJECT(
                            'id', p.id,
                            'name', p.name,
                            'abbreviation', p.abbreviation
                        )
                    ) FILTER (WHERE p.id IS NOT NULL),
                    '[]'::jsonb
                )::text AS platforms_json,
                cover.image_id AS cover_image_id,
                cover.url AS cover_url,
                BOOL_OR(ko_lang.language_id IS NOT NULL) AS has_korean_language_support,
                BOOL_OR(COALESCE(ko_lang.supports_audio, FALSE)) AS supports_audio,
                BOOL_OR(COALESCE(ko_lang.supports_subtitles, FALSE)) AS supports_subtitles,
                BOOL_OR(COALESCE(ko_lang.supports_interface, FALSE)) AS supports_interface
            FROM service.game_release gr
                JOIN service.game g ON g.id = gr.game_id
                LEFT JOIN service.game_type gt ON gt.id = g.type_id
                LEFT JOIN service.release_region rr ON rr.id = gr.region_id
                LEFT JOIN service.release_status rs ON rs.id = gr.status_id
                LEFT JOIN service.platform p ON p.id = gr.platform_id
                LEFT JOIN service.game_localization ko_title
                  ON ko_title.game_id = g.id
                 AND ko_title.region_id = :koreaLocalizationRegionId
                 AND NULLIF(BTRIM(ko_title.name), '') IS NOT NULL
                LEFT JOIN service.game_language ko_lang
                  ON ko_lang.game_id = g.id
                 AND ko_lang.language_id = :koreanLanguageId
                LEFT JOIN LATERAL (
                    SELECT c.image_id, c.url
                    FROM service.cover c
                        LEFT JOIN service.game_localization cgl ON cgl.id = c.game_localization_id
                    WHERE c.game_id = g.id
                      AND (NULLIF(BTRIM(c.image_id), '') IS NOT NULL OR NULLIF(BTRIM(c.url), '') IS NOT NULL)
                    ORDER BY
                        CASE
                            WHEN cgl.region_id = :koreaLocalizationRegionId THEN 0
                            WHEN c.is_main THEN 1
                            ELSE 2
                        END,
                        c.id
                    LIMIT 1
                ) cover ON TRUE
            WHERE gr.release_date >= :startInclusive
              AND gr.release_date < :endExclusive
              $platformFilterSql
            GROUP BY
                gr.game_id,
                (gr.release_date AT TIME ZONE 'Asia/Seoul')::date,
                COALESCE(BTRIM(ko_title.name), BTRIM(g.name)),
                BTRIM(g.name),
                gt.id,
                gt.type,
                rr.id,
                rr.name,
                rs.id,
                rs.name,
                g.hypes,
                g.follows,
                cover.image_id,
                cover.url
            ORDER BY release_date, title, MIN(gr.id)
            """.trimIndent(),
            params,
        ) { rs, _ -> rs.toReleaseItem() }
    }

    override fun findGame(gameId: Long): GameDetailResponse? =
        jdbc.query(
            """
            SELECT
                g.id AS game_id,
                g.slug,
                COALESCE(BTRIM(ko_title.name), BTRIM(g.name)) AS title,
                BTRIM(g.name) AS default_title,
                g.summary,
                (g.first_release_date AT TIME ZONE 'Asia/Seoul')::date AS first_release_date,
                gt.id AS game_type_id,
                gt.type AS game_type,
                gs.id AS game_status_id,
                gs.status AS game_status,
                cover.image_id AS cover_image_id,
                cover.url AS cover_url,
                platforms.items::text AS platforms_json,
                genres.items::text AS genres_json,
                developers.items::text AS developers_json,
                ko_lang.language_id IS NOT NULL AS has_korean_language_support,
                COALESCE(ko_lang.supports_audio, FALSE) AS supports_audio,
                COALESCE(ko_lang.supports_subtitles, FALSE) AS supports_subtitles,
                COALESCE(ko_lang.supports_interface, FALSE) AS supports_interface,
                websites.items::text AS websites_json,
                video.id AS video_row_id,
                video.name AS video_name,
                video.video_key AS video_key
            FROM service.game g
            LEFT JOIN service.game_type gt ON gt.id = g.type_id
            LEFT JOIN service.game_status gs ON gs.id = g.status_id
            LEFT JOIN service.game_localization ko_title
              ON ko_title.game_id = g.id
             AND ko_title.region_id = :koreaLocalizationRegionId
             AND NULLIF(BTRIM(ko_title.name), '') IS NOT NULL
            LEFT JOIN LATERAL (
                SELECT c.image_id, c.url
                FROM service.cover c
                LEFT JOIN service.game_localization cgl ON cgl.id = c.game_localization_id
                WHERE c.game_id = g.id
                  AND (NULLIF(BTRIM(c.image_id), '') IS NOT NULL OR NULLIF(BTRIM(c.url), '') IS NOT NULL)
                ORDER BY
                    CASE
                        WHEN cgl.region_id = :koreaLocalizationRegionId THEN 0
                        WHEN c.is_main THEN 1
                        ELSE 2
                    END,
                    c.id
                LIMIT 1
            ) cover ON TRUE
            LEFT JOIN LATERAL (
                SELECT COALESCE(
                    JSONB_AGG(
                        JSONB_BUILD_OBJECT(
                            'id', selected.id,
                            'name', selected.name,
                            'abbreviation', selected.abbreviation
                        )
                        ORDER BY selected.name NULLS LAST, selected.id
                    ),
                    '[]'::jsonb
                ) AS items
                FROM (
                    SELECT DISTINCT p.id, p.name, p.abbreviation
                    FROM service.game_release gr
                    JOIN service.platform p ON p.id = gr.platform_id
                    WHERE gr.game_id = g.id
                ) selected
            ) platforms ON TRUE
            LEFT JOIN LATERAL (
                SELECT COALESCE(
                    JSONB_AGG(JSONB_BUILD_OBJECT('id', ge.id, 'name', ge.name) ORDER BY ge.name NULLS LAST, ge.id),
                    '[]'::jsonb
                ) AS items
                FROM service.game_genre gg
                JOIN service.genre ge ON ge.id = gg.genre_id
                WHERE gg.game_id = g.id
            ) genres ON TRUE
            LEFT JOIN LATERAL (
                SELECT COALESCE(
                    JSONB_AGG(JSONB_BUILD_OBJECT('id', c.id, 'name', c.name) ORDER BY c.name NULLS LAST, c.id),
                    '[]'::jsonb
                ) AS items
                FROM service.game_company gc
                JOIN service.company c ON c.id = gc.company_id
                WHERE gc.game_id = g.id
                  AND gc.is_developer = TRUE
            ) developers ON TRUE
            LEFT JOIN service.game_language ko_lang
              ON ko_lang.game_id = g.id
             AND ko_lang.language_id = :koreanLanguageId
            LEFT JOIN LATERAL (
                SELECT COALESCE(
                    JSONB_AGG(
                        JSONB_BUILD_OBJECT(
                            'id', w.id,
                            'url', w.url,
                            'type', CASE
                                WHEN wt.id IS NULL THEN NULL
                                ELSE JSONB_BUILD_OBJECT('id', wt.id, 'type', wt.type)
                            END
                        )
                        ORDER BY w.url NULLS LAST, w.id
                    ),
                    '[]'::jsonb
                ) AS items
                FROM service.website w
                LEFT JOIN service.website_type wt ON wt.id = w.type_id
                WHERE w.game_id = g.id
            ) websites ON TRUE
            LEFT JOIN LATERAL (
                SELECT gv.id, gv.name, NULLIF(BTRIM(gv.video_id), '') AS video_key
                FROM service.game_video gv
                WHERE gv.game_id = g.id
                ORDER BY gv.id
                LIMIT 1
            ) video ON TRUE
            WHERE g.id = :gameId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("gameId", gameId)
                .addValue("koreaLocalizationRegionId", KOREA_LOCALIZATION_REGION_ID)
                .addValue("koreanLanguageId", KOREAN_LANGUAGE_ID),
            ResultSetExtractor { rs -> if (rs.next()) rs.toGameDetail() else null },
        )

    private fun ResultSet.toReleaseItem(): ReleaseItemResponse =
        ReleaseItemResponse(
            releaseIds = getLongList("release_ids"),
            gameId = getLong("game_id"),
            date = getString("release_date"),
            title = getString("title"),
            defaultTitle = getString("default_title"),
            gameType = getNullableLong("game_type_id")?.let {
                GameTypeResponse(id = it, type = getString("game_type"))
            },
            region = getNullableLong("region_id")?.let {
                ReleaseRegionResponse(id = it, name = getString("region_name"))
            },
            releaseStatus = getNullableLong("release_status_id")?.let {
                ReleaseStatusResponse(id = it, name = getString("release_status_name"))
            },
            platforms = readJson<List<PlatformResponse>>(getString("platforms_json")).sortedBy { it.id },
            coverThumbnailUrl = imageUrl(
                imageId = getString("cover_image_id"),
                url = getString("cover_url"),
                size = IGDB_COVER_THUMBNAIL_SIZE,
            ),
            koreanLanguageSupport = getKoreanLanguageSupport(),
            hypes = getNullableInt("hypes"),
            follows = getNullableInt("follows"),
        )

    private fun ResultSet.toGameDetail(): GameDetailResponse {
        val coverImageId = getString("cover_image_id")
        val coverUrl = getString("cover_url")
        val videoKey = getString("video_key")

        return GameDetailResponse(
            gameId = getLong("game_id"),
            slug = getString("slug").trimToNull(),
            title = getString("title"),
            defaultTitle = getString("default_title"),
            summary = getString("summary"),
            firstReleaseDate = getString("first_release_date"),
            gameType = getNullableLong("game_type_id")?.let {
                GameTypeResponse(id = it, type = getString("game_type"))
            },
            gameStatus = getNullableLong("game_status_id")?.let {
                GameStatusResponse(id = it, status = getString("game_status"))
            },
            coverThumbnailUrl = imageUrl(coverImageId, coverUrl, IGDB_COVER_THUMBNAIL_SIZE),
            coverUrl = imageUrl(coverImageId, coverUrl, IGDB_COVER_SIZE),
            platforms = readJson(getString("platforms_json")),
            genres = readJson(getString("genres_json")),
            developers = readJson(getString("developers_json")),
            koreanLanguageSupport = getKoreanLanguageSupport(),
            websites = readJson(getString("websites_json")),
            video = videoKey?.let {
                VideoResponse(
                    id = getLong("video_row_id"),
                    name = getString("video_name"),
                    videoId = it,
                    url = youtubeUrl(it),
                    thumbnailUrl = youtubeThumbnailUrl(it),
                )
            },
        )
    }

    private fun ResultSet.getKoreanLanguageSupport(): KoreanLanguageSupportResponse? {
        if (!getBoolean("has_korean_language_support")) {
            return null
        }

        return KoreanLanguageSupportResponse(
            audio = getBoolean("supports_audio"),
            subtitles = getBoolean("supports_subtitles"),
            interfaceSupported = getBoolean("supports_interface"),
        )
    }

    private inline fun <reified T> readJson(value: String): T =
        objectMapper.readValue(value)

    private fun ResultSet.getNullableLong(columnName: String): Long? {
        val value = getLong(columnName)
        return value.takeIf { !wasNull() }
    }

    private fun ResultSet.getNullableInt(columnName: String): Int? {
        val value = getInt(columnName)
        return value.takeIf { !wasNull() }
    }

    private fun ResultSet.getLongList(columnName: String): List<Long> =
        getArray(columnName)?.toLongList().orEmpty()

    private fun Array.toLongList(): List<Long> =
        (array as kotlin.Array<*>).map { (it as Number).toLong() }

    private fun imageUrl(imageId: String?, url: String?, size: String): String? {
        val normalizedImageId = imageId.trimToNull()
        if (normalizedImageId != null) {
            return "https://images.igdb.com/igdb/image/upload/$size/$normalizedImageId.jpg"
        }

        return url.trimToNull()?.let {
            val absoluteUrl = if (it.startsWith("//")) "https:$it" else it
            absoluteUrl.replace(Regex("/t_[^/]+/"), "/$size/")
        }
    }

    private fun youtubeUrl(videoId: String): String =
        "https://www.youtube.com/watch?v=$videoId"

    private fun youtubeThumbnailUrl(videoId: String): String =
        "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    private fun String?.trimToNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        private const val KOREA_LOCALIZATION_REGION_ID = 2L
        private const val KOREAN_LANGUAGE_ID = 17L
        private const val IGDB_COVER_THUMBNAIL_SIZE = "t_cover_small"
        private const val IGDB_COVER_SIZE = "t_cover_big"
    }
}
