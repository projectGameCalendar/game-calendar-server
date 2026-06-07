package com.projectgc.calendar.repository.api

import com.projectgc.calendar.model.api.GameDetailResponse
import com.projectgc.calendar.model.api.ReleaseItemResponse
import java.time.Instant

interface ServiceApiRepository {
    fun findReleases(
        startInclusive: Instant,
        endExclusive: Instant,
        platformIds: Set<Long>,
    ): List<ReleaseItemResponse>

    fun findGame(gameId: Long): GameDetailResponse?
}
