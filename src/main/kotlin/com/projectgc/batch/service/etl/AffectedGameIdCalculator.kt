package com.projectgc.batch.service.etl

import com.projectgc.batch.repository.etl.AlternativeNameProjectionRow
import com.projectgc.batch.repository.etl.ArtworkProjectionRow
import com.projectgc.batch.repository.etl.CoverProjectionRow
import com.projectgc.batch.repository.etl.GameCompanyProjectionRow
import com.projectgc.batch.repository.etl.GameVideoProjectionRow
import com.projectgc.batch.repository.etl.GameDimensionProjectionRow
import com.projectgc.batch.repository.etl.GameLanguageProjectionRow
import com.projectgc.batch.repository.etl.GameLocalizationProjectionRow
import com.projectgc.batch.repository.etl.GameProjectionRow
import com.projectgc.batch.repository.etl.GameRelationProjectionRow
import com.projectgc.batch.repository.etl.GameReleaseProjectionRow
import com.projectgc.batch.repository.etl.IngestEtlReadJdbcRepository
import com.projectgc.batch.repository.etl.ScreenshotProjectionRow
import com.projectgc.batch.repository.etl.ServiceEtlJdbcRepository
import com.projectgc.batch.repository.etl.WebsiteProjectionRow
import com.projectgc.batch.repository.etl.resolveAlternativeNameReferences
import com.projectgc.batch.repository.etl.resolveArtworkReferences
import com.projectgc.batch.repository.etl.resolveCoverReferences
import com.projectgc.batch.repository.etl.resolveGameCompanyReferences
import com.projectgc.batch.repository.etl.resolveGameDimensionReferences
import com.projectgc.batch.repository.etl.resolveGameLanguageReferences
import com.projectgc.batch.repository.etl.resolveGameLocalizationReferences
import com.projectgc.batch.repository.etl.resolveGameReferences
import com.projectgc.batch.repository.etl.resolveGameRelationReferences
import com.projectgc.batch.repository.etl.resolveGameReleaseReferences
import com.projectgc.batch.repository.etl.resolveGameVideoReferences
import com.projectgc.batch.repository.etl.resolveScreenshotReferences
import com.projectgc.batch.repository.etl.resolveWebsiteReferences
import org.springframework.stereotype.Service

@Service
class AffectedGameIdCalculator(
    private val ingestEtlReadJdbcRepository: IngestEtlReadJdbcRepository,
    private val serviceEtlJdbcRepository: ServiceEtlJdbcRepository,
) {
    companion object {
        private const val GAME_PROJECTION_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.game core and bridge projections"
        private const val GAME_RELEASE_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.game_release projection"
        private const val INVOLVED_COMPANY_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.game_company projection"
        private const val LANGUAGE_SUPPORT_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.game_language projection"
        private const val GAME_LOCALIZATION_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.game_localization projection"
        private const val COVER_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.cover projection"
        private const val ARTWORK_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.artwork projection"
        private const val SCREENSHOT_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.screenshot projection"
        private const val GAME_VIDEO_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.game_video projection"
        private const val WEBSITE_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.website projection"
        private const val ALTERNATIVE_NAME_DIFF_NOTE =
            "slice6 affected game_id diff calculated from service.alternative_name projection"
    }

    fun prepare(): PreparedAffectedGameIdInputs {
        val allGameIds = ingestEtlReadJdbcRepository.findServiceCandidateGameIds().toSet()
        return PreparedAffectedGameIdInputs(
            allGameIds = allGameIds,
            gameRows = ingestEtlReadJdbcRepository.loadGameProjectionRows(allGameIds),
            gameReleaseRows = ingestEtlReadJdbcRepository.loadServiceGameReleaseProjectionRows(allGameIds),
            gameLocalizationRows = ingestEtlReadJdbcRepository.loadServiceGameLocalizationProjectionRows(allGameIds),
            gameLanguageRows = ingestEtlReadJdbcRepository.loadServiceGameLanguageProjectionRows(allGameIds),
            gameGenreRows = ingestEtlReadJdbcRepository.loadGameArrayProjectionRows(allGameIds, "genres"),
            gameThemeRows = emptyList(),
            gamePlayerPerspectiveRows = emptyList(),
            gameModeRows = emptyList(),
            gameKeywordRows = emptyList(),
            gameCompanyRows = ingestEtlReadJdbcRepository.loadServiceGameCompanyProjectionRows(allGameIds),
            gameRelationRows = emptyList(),
            coverRows = ingestEtlReadJdbcRepository.loadCoverProjectionRows(allGameIds),
            artworkRows = emptyList(),
            screenshotRows = emptyList(),
            gameVideoRows = ingestEtlReadJdbcRepository.loadServiceGameVideoProjectionRows(allGameIds),
            websiteRows = ingestEtlReadJdbcRepository.loadServiceWebsiteProjectionRows(allGameIds),
            alternativeNameRows = emptyList(),
        )
    }

    fun calculate(): AffectedGameIdCalculationResult = calculate(prepare())

    fun calculate(preparedInputs: PreparedAffectedGameIdInputs): AffectedGameIdCalculationResult {
        val allGameIds = preparedInputs.allGameIds
        // service 현재 상태를 한 번만 로드해 모든 diff 계산이 공유한다.
        // 결과에도 담아 호출자(ServiceEtlService)의 차원 삭제 영향 계산이 재로드 없이 재사용한다
        // — calculate와 그 계산 사이에는 게임 프로젝션 테이블 쓰기가 없어 동일 상태가 보장됨.
        val currentRows = loadCurrentServiceProjectionRows()
        val availableRegionIds = serviceEtlJdbcRepository.loadIds("service.region")
        val sourceResults = listOf(
            projectionDiffResult(
                tableName = "game",
                note = GAME_PROJECTION_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromGameProjectionDiff(preparedInputs, currentRows),
            ),
            projectionDiffResult(
                tableName = "release_date",
                note = GAME_RELEASE_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromGameReleaseProjectionDiff(
                    ingestGameIds = allGameIds,
                    releaseRows = preparedInputs.gameReleaseRows,
                    actualRows = currentRows.gameReleaseRows,
                ),
            ),
            projectionDiffResult(
                tableName = "involved_company",
                note = INVOLVED_COMPANY_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromInvolvedCompanyProjectionDiff(
                    ingestGameIds = allGameIds,
                    companyRows = preparedInputs.gameCompanyRows,
                    actualRows = currentRows.gameCompanyRows,
                ),
            ),
            projectionDiffResult(
                tableName = "language_support",
                note = LANGUAGE_SUPPORT_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromLanguageSupportProjectionDiff(
                    ingestGameIds = allGameIds,
                    languageRows = preparedInputs.gameLanguageRows,
                    actualRows = currentRows.gameLanguageRows,
                ),
            ),
            projectionDiffResult(
                tableName = "game_localization",
                note = GAME_LOCALIZATION_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromGameLocalizationProjectionDiff(
                    ingestGameIds = allGameIds,
                    localizationRows = preparedInputs.gameLocalizationRows,
                    availableRegionIds = availableRegionIds,
                    actualRows = currentRows.gameLocalizationRows,
                ),
            ),
            projectionDiffResult(
                tableName = "cover",
                note = COVER_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromCoverProjectionDiff(
                    ingestGameIds = allGameIds,
                    preparedInputs = preparedInputs,
                    availableRegionIds = availableRegionIds,
                    actualRows = currentRows.coverRows,
                ),
            ),
            projectionDiffResult(
                tableName = "artwork",
                note = ARTWORK_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromArtworkProjectionDiff(
                    ingestGameIds = allGameIds,
                    artworkRows = preparedInputs.artworkRows,
                    actualRows = currentRows.artworkRows,
                ),
            ),
            projectionDiffResult(
                tableName = "screenshot",
                note = SCREENSHOT_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromScreenshotProjectionDiff(
                    ingestGameIds = allGameIds,
                    screenshotRows = preparedInputs.screenshotRows,
                    actualRows = currentRows.screenshotRows,
                ),
            ),
            projectionDiffResult(
                tableName = "game_video",
                note = GAME_VIDEO_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromGameVideoProjectionDiff(
                    ingestGameIds = allGameIds,
                    gameVideoRows = preparedInputs.gameVideoRows,
                    actualRows = currentRows.gameVideoRows,
                ),
            ),
            projectionDiffResult(
                tableName = "website",
                note = WEBSITE_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromWebsiteProjectionDiff(
                    ingestGameIds = allGameIds,
                    websiteRows = preparedInputs.websiteRows,
                    actualRows = currentRows.websiteRows,
                ),
            ),
            projectionDiffResult(
                tableName = "alternative_name",
                note = ALTERNATIVE_NAME_DIFF_NOTE,
                affectedGameIds = findAffectedGameIdsFromAlternativeNameProjectionDiff(
                    ingestGameIds = allGameIds,
                    alternativeNameRows = preparedInputs.alternativeNameRows,
                    actualRows = currentRows.alternativeNameRows,
                ),
            ),
        )

        val affectedGameIds = linkedSetOf<Long>()
        sourceResults.forEach { affectedGameIds += it.affectedGameIds }

        return AffectedGameIdCalculationResult(
            affectedGameIds = affectedGameIds,
            sourceResults = sourceResults,
            currentRows = currentRows,
        )
    }

    private fun loadCurrentServiceProjectionRows() = CurrentServiceProjectionRows(
        gameRows = serviceEtlJdbcRepository.loadCurrentGameProjectionRows(),
        gameLocalizationRows = serviceEtlJdbcRepository.loadCurrentGameLocalizationProjectionRows(),
        gameReleaseRows = serviceEtlJdbcRepository.loadCurrentGameReleaseProjectionRows(),
        gameLanguageRows = serviceEtlJdbcRepository.loadCurrentGameLanguageProjectionRows(),
        gameGenreRows = serviceEtlJdbcRepository.loadCurrentGameDimensionProjectionRows("game_genre", "genre_id"),
        gameThemeRows = serviceEtlJdbcRepository.loadCurrentGameDimensionProjectionRows("game_theme", "theme_id"),
        gamePlayerPerspectiveRows = serviceEtlJdbcRepository.loadCurrentGameDimensionProjectionRows(
            "game_player_perspective",
            "player_perspective_id",
        ),
        gameModeRows = serviceEtlJdbcRepository.loadCurrentGameDimensionProjectionRows("game_game_mode", "game_mode_id"),
        gameKeywordRows = serviceEtlJdbcRepository.loadCurrentGameDimensionProjectionRows("game_keyword", "keyword_id"),
        gameCompanyRows = serviceEtlJdbcRepository.loadCurrentGameCompanyProjectionRows(),
        gameRelationRows = serviceEtlJdbcRepository.loadCurrentGameRelationProjectionRows(),
        coverRows = serviceEtlJdbcRepository.loadCurrentCoverProjectionRows(),
        artworkRows = serviceEtlJdbcRepository.loadCurrentArtworkProjectionRows(),
        screenshotRows = serviceEtlJdbcRepository.loadCurrentScreenshotProjectionRows(),
        gameVideoRows = serviceEtlJdbcRepository.loadCurrentGameVideoProjectionRows(),
        websiteRows = serviceEtlJdbcRepository.loadCurrentWebsiteProjectionRows(),
        alternativeNameRows = serviceEtlJdbcRepository.loadCurrentAlternativeNameProjectionRows(),
    )

    private fun projectionDiffResult(
        tableName: String,
        note: String,
        affectedGameIds: Set<Long>,
    ) = AffectedGameIdSourceResult(
        tableName = tableName,
        affectedGameIds = affectedGameIds,
        note = note,
    )

    private fun findAffectedGameIdsFromGameProjectionDiff(
        preparedInputs: PreparedAffectedGameIdInputs,
        currentRows: CurrentServiceProjectionRows,
    ): Set<Long> =
        linkedSetOf<Long>().apply {
            addAll(findAffectedGameIdsFromCoreGameProjectionDiff(preparedInputs.gameRows, currentRows.gameRows))
            addAll(findAffectedGameIdsFromGameBridgeProjectionDiff(preparedInputs, currentRows))
        }

    private fun findAffectedGameIdsFromCoreGameProjectionDiff(
        gameRows: List<GameProjectionRow>,
        actualRows: List<GameProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveGameReferences(
            rows = gameRows,
            availableStatusIds = serviceEtlJdbcRepository.loadIds("service.game_status"),
            availableTypeIds = serviceEtlJdbcRepository.loadIds("service.game_type"),
        )
        val actualById = actualRows.associateBy { it.id }
        return expectedRows
            .filter { actualById[it.id] != it }
            .mapTo(linkedSetOf()) { it.id }
    }

    private fun findAffectedGameIdsFromGameBridgeProjectionDiff(
        preparedInputs: PreparedAffectedGameIdInputs,
        currentRows: CurrentServiceProjectionRows,
    ): Set<Long> =
        linkedSetOf<Long>().apply {
            addAll(
                findAffectedGameIdsFromGameArrayProjectionDiff(
                    ingestGameIds = preparedInputs.allGameIds,
                    expectedRows = preparedInputs.gameGenreRows,
                    dimensionTable = "genre",
                    actualRows = currentRows.gameGenreRows,
                )
            )
            addAll(
                findAffectedGameIdsFromGameArrayProjectionDiff(
                    ingestGameIds = preparedInputs.allGameIds,
                    expectedRows = preparedInputs.gameThemeRows,
                    dimensionTable = "theme",
                    actualRows = currentRows.gameThemeRows,
                )
            )
            addAll(
                findAffectedGameIdsFromGameArrayProjectionDiff(
                    ingestGameIds = preparedInputs.allGameIds,
                    expectedRows = preparedInputs.gamePlayerPerspectiveRows,
                    dimensionTable = "player_perspective",
                    actualRows = currentRows.gamePlayerPerspectiveRows,
                )
            )
            addAll(
                findAffectedGameIdsFromGameArrayProjectionDiff(
                    ingestGameIds = preparedInputs.allGameIds,
                    expectedRows = preparedInputs.gameModeRows,
                    dimensionTable = "game_mode",
                    actualRows = currentRows.gameModeRows,
                )
            )
            addAll(
                findAffectedGameIdsFromGameArrayProjectionDiff(
                    ingestGameIds = preparedInputs.allGameIds,
                    expectedRows = preparedInputs.gameKeywordRows,
                    dimensionTable = "keyword",
                    actualRows = currentRows.gameKeywordRows,
                )
            )
            addAll(
                findAffectedGameIdsFromGameRelationProjectionDiff(
                    ingestGameIds = preparedInputs.allGameIds,
                    relationRows = preparedInputs.gameRelationRows,
                    actualRows = currentRows.gameRelationRows,
                )
            )
        }

    private fun findAffectedGameIdsFromGameArrayProjectionDiff(
        ingestGameIds: Set<Long>,
        expectedRows: List<GameDimensionProjectionRow>,
        dimensionTable: String,
        actualRows: List<GameDimensionProjectionRow>,
    ): Set<Long> {
        val resolvedRows = resolveGameDimensionReferences(
            rows = expectedRows,
            availableGameIds = ingestGameIds,
            availableDimensionIds = serviceEtlJdbcRepository.loadIds("service.$dimensionTable"),
        )
        return findAffectedGameIdsByKey(
            expectedRows = resolvedRows,
            actualRows = actualRows,
            keySelector = { it.gameId to it.dimensionId },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromGameRelationProjectionDiff(
        ingestGameIds: Set<Long>,
        relationRows: List<GameRelationProjectionRow>,
        actualRows: List<GameRelationProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveGameRelationReferences(
            rows = relationRows,
            availableGameIds = ingestGameIds,
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { Triple(it.gameId, it.relatedGameId, it.relationType) },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromGameReleaseProjectionDiff(
        ingestGameIds: Set<Long>,
        releaseRows: List<GameReleaseProjectionRow>,
        actualRows: List<GameReleaseProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveGameReleaseReferences(
            rows = releaseRows,
            availableGameIds = ingestGameIds,
            availablePlatformIds = serviceEtlJdbcRepository.loadIds("service.platform"),
            availableRegionIds = serviceEtlJdbcRepository.loadIds("service.release_region"),
            availableStatusIds = serviceEtlJdbcRepository.loadIds("service.release_status"),
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.id },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromInvolvedCompanyProjectionDiff(
        ingestGameIds: Set<Long>,
        companyRows: List<GameCompanyProjectionRow>,
        actualRows: List<GameCompanyProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveGameCompanyReferences(
            rows = companyRows,
            availableGameIds = ingestGameIds,
            availableCompanyIds = serviceEtlJdbcRepository.loadIds("service.company"),
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.gameId to it.companyId },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromLanguageSupportProjectionDiff(
        ingestGameIds: Set<Long>,
        languageRows: List<GameLanguageProjectionRow>,
        actualRows: List<GameLanguageProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveGameLanguageReferences(
            rows = languageRows,
            availableGameIds = ingestGameIds,
            availableLanguageIds = serviceEtlJdbcRepository.loadIds("service.language"),
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.gameId to it.languageId },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromGameLocalizationProjectionDiff(
        ingestGameIds: Set<Long>,
        localizationRows: List<GameLocalizationProjectionRow>,
        availableRegionIds: Set<Long>,
        actualRows: List<GameLocalizationProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveGameLocalizationReferences(
            rows = localizationRows,
            availableGameIds = ingestGameIds,
            availableRegionIds = availableRegionIds,
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.id },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromCoverProjectionDiff(
        ingestGameIds: Set<Long>,
        preparedInputs: PreparedAffectedGameIdInputs,
        availableRegionIds: Set<Long>,
        actualRows: List<CoverProjectionRow>,
    ): Set<Long> {
        val expectedLocalizationsById = resolveGameLocalizationReferences(
            rows = preparedInputs.gameLocalizationRows,
            availableGameIds = ingestGameIds,
            availableRegionIds = availableRegionIds,
        ).associate { it.id to it.gameId }
        val expectedRows = resolveCoverReferences(
            rows = preparedInputs.coverRows,
            availableGameIds = ingestGameIds,
            availableGameLocalizationsById = expectedLocalizationsById,
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.id },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromArtworkProjectionDiff(
        ingestGameIds: Set<Long>,
        artworkRows: List<ArtworkProjectionRow>,
        actualRows: List<ArtworkProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveArtworkReferences(
            rows = artworkRows,
            availableGameIds = ingestGameIds,
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.id },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromScreenshotProjectionDiff(
        ingestGameIds: Set<Long>,
        screenshotRows: List<ScreenshotProjectionRow>,
        actualRows: List<ScreenshotProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveScreenshotReferences(
            rows = screenshotRows,
            availableGameIds = ingestGameIds,
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.id },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromGameVideoProjectionDiff(
        ingestGameIds: Set<Long>,
        gameVideoRows: List<GameVideoProjectionRow>,
        actualRows: List<GameVideoProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveGameVideoReferences(
            rows = gameVideoRows,
            availableGameIds = ingestGameIds,
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.id },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromWebsiteProjectionDiff(
        ingestGameIds: Set<Long>,
        websiteRows: List<WebsiteProjectionRow>,
        actualRows: List<WebsiteProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveWebsiteReferences(
            rows = websiteRows,
            availableGameIds = ingestGameIds,
            availableTypeIds = serviceEtlJdbcRepository.loadIds("service.website_type"),
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.id },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun findAffectedGameIdsFromAlternativeNameProjectionDiff(
        ingestGameIds: Set<Long>,
        alternativeNameRows: List<AlternativeNameProjectionRow>,
        actualRows: List<AlternativeNameProjectionRow>,
    ): Set<Long> {
        val expectedRows = resolveAlternativeNameReferences(
            rows = alternativeNameRows,
            availableGameIds = ingestGameIds,
        )
        return findAffectedGameIdsByKey(
            expectedRows = expectedRows,
            actualRows = actualRows,
            keySelector = { it.id },
            gameIdSelector = { it.gameId },
            includeActualGameId = { it in ingestGameIds },
        )
    }

    private fun <T, K> findAffectedGameIdsByKey(
        expectedRows: List<T>,
        actualRows: List<T>,
        keySelector: (T) -> K,
        gameIdSelector: (T) -> Long,
        includeActualGameId: (Long) -> Boolean = { true },
    ): Set<Long> {
        val expectedByKey = expectedRows.associateBy(keySelector)
        val actualByKey = actualRows.associateBy(keySelector)
        val affectedGameIds = linkedSetOf<Long>()

        expectedRows.forEach { expectedRow ->
            val key = keySelector(expectedRow)
            val actualRow = actualByKey[key]
            if (actualRow != expectedRow) {
                affectedGameIds += gameIdSelector(expectedRow)
                if (actualRow != null) {
                    val actualGameId = gameIdSelector(actualRow)
                    if (includeActualGameId(actualGameId)) {
                        affectedGameIds += actualGameId
                    }
                }
            }
        }
        actualRows.forEach { actualRow ->
            val key = keySelector(actualRow)
            val gameId = gameIdSelector(actualRow)
            if (!expectedByKey.containsKey(key) && includeActualGameId(gameId)) {
                affectedGameIds += gameId
            }
        }

        return affectedGameIds
    }
}

data class PreparedAffectedGameIdInputs(
    val allGameIds: Set<Long>,
    val gameRows: List<GameProjectionRow>,
    val gameReleaseRows: List<GameReleaseProjectionRow>,
    val gameLocalizationRows: List<GameLocalizationProjectionRow>,
    val gameLanguageRows: List<GameLanguageProjectionRow>,
    val gameGenreRows: List<GameDimensionProjectionRow>,
    val gameThemeRows: List<GameDimensionProjectionRow>,
    val gamePlayerPerspectiveRows: List<GameDimensionProjectionRow>,
    val gameModeRows: List<GameDimensionProjectionRow>,
    val gameKeywordRows: List<GameDimensionProjectionRow>,
    val gameCompanyRows: List<GameCompanyProjectionRow>,
    val gameRelationRows: List<GameRelationProjectionRow>,
    val coverRows: List<CoverProjectionRow>,
    val artworkRows: List<ArtworkProjectionRow>,
    val screenshotRows: List<ScreenshotProjectionRow>,
    val gameVideoRows: List<GameVideoProjectionRow>,
    val websiteRows: List<WebsiteProjectionRow>,
    val alternativeNameRows: List<AlternativeNameProjectionRow>,
)

data class AffectedGameIdCalculationResult(
    val affectedGameIds: Set<Long>,
    val sourceResults: List<AffectedGameIdSourceResult>,
    // calculate 시점의 service 프로젝션 스냅샷 — 호출자의 차원 삭제 영향 계산이 재로드 없이 재사용
    val currentRows: CurrentServiceProjectionRows,
)

// service 스키마 게임 프로젝션 테이블들의 현재 상태 스냅샷 (트랜잭션 내 1회 로드)
data class CurrentServiceProjectionRows(
    val gameRows: List<GameProjectionRow> = emptyList(),
    val gameLocalizationRows: List<GameLocalizationProjectionRow> = emptyList(),
    val gameReleaseRows: List<GameReleaseProjectionRow> = emptyList(),
    val gameLanguageRows: List<GameLanguageProjectionRow> = emptyList(),
    val gameGenreRows: List<GameDimensionProjectionRow> = emptyList(),
    val gameThemeRows: List<GameDimensionProjectionRow> = emptyList(),
    val gamePlayerPerspectiveRows: List<GameDimensionProjectionRow> = emptyList(),
    val gameModeRows: List<GameDimensionProjectionRow> = emptyList(),
    val gameKeywordRows: List<GameDimensionProjectionRow> = emptyList(),
    val gameCompanyRows: List<GameCompanyProjectionRow> = emptyList(),
    val gameRelationRows: List<GameRelationProjectionRow> = emptyList(),
    val coverRows: List<CoverProjectionRow> = emptyList(),
    val artworkRows: List<ArtworkProjectionRow> = emptyList(),
    val screenshotRows: List<ScreenshotProjectionRow> = emptyList(),
    val gameVideoRows: List<GameVideoProjectionRow> = emptyList(),
    val websiteRows: List<WebsiteProjectionRow> = emptyList(),
    val alternativeNameRows: List<AlternativeNameProjectionRow> = emptyList(),
)

data class AffectedGameIdSourceResult(
    val tableName: String,
    val affectedGameIds: Set<Long>,
    val note: String,
)
