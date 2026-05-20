package com.projectgc.calendar.repository.etl

internal object ServiceEtlDataScope {
    const val GLOBAL_RELEASE_REGION_ID = 8L
    const val KOREA_RELEASE_REGION_ID = 9L
    const val CANCELLED_RELEASE_STATUS_ID = 5L
    const val KOREA_LOCALIZATION_REGION_ID = 2L
    const val KOREAN_LANGUAGE_ID = 17L

    val serviceReleaseRegionIds = setOf(GLOBAL_RELEASE_REGION_ID, KOREA_RELEASE_REGION_ID)
    val fixedLocalizationRegionIds = setOf(KOREA_LOCALIZATION_REGION_ID)
    val fixedLanguageIds = setOf(KOREAN_LANGUAGE_ID)

    val servicePlatformIds = setOf(
        3L, 6L, 14L, 163L,
        7L, 8L, 9L, 38L, 46L, 48L, 165L, 167L, 390L,
        4L, 18L, 19L, 20L, 21L, 37L, 41L, 130L, 508L,
        11L, 12L, 49L, 169L,
        34L, 39L,
    )

    fun idList(ids: Set<Long>): String =
        ids.sorted().joinToString(", ")
}
