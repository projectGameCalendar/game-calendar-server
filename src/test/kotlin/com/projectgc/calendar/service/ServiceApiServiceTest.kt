package com.projectgc.calendar.service

import com.projectgc.calendar.model.api.GameDetailResponse
import com.projectgc.calendar.model.api.DateReleasesResponse
import com.projectgc.calendar.model.api.ReleaseItemResponse
import com.projectgc.calendar.repository.api.ServiceApiRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceApiServiceTest {

    @Test
    fun `date query uses seoul day range and platform group ids`() {
        val repository = FakeRepository(releases = listOf(releaseItem(1L)))
        val service = GameReleaseService(repository)

        val response = service.findReleases(
            date = "2026-05-10",
            from = null,
            to = null,
            platformGroupParams = listOf("PC,NINTENDO"),
        )

        response as DateReleasesResponse
        assertEquals("2026-05-10", response.date)
        assertEquals(1, response.count)
        assertEquals(Instant.parse("2026-05-09T15:00:00Z"), repository.startInclusive)
        assertEquals(Instant.parse("2026-05-10T15:00:00Z"), repository.endExclusive)
        assertEquals(
            setOf(3L, 4L, 6L, 14L, 18L, 19L, 20L, 21L, 37L, 41L, 130L, 163L, 508L),
            repository.platformIds,
        )
    }

    @Test
    fun `invalid release query is rejected before repository access`() {
        val service = GameReleaseService(FakeRepository())

        assertInvalidRequest {
            service.findReleases("2026-05-10", "2026-05-01", "2026-05-31", null)
        }
        assertInvalidRequest {
            service.findReleases(null, "2026-05-10", null, null)
        }
        assertInvalidRequest {
            service.findReleases(null, "2026-05-11", "2026-05-10", null)
        }
        assertInvalidRequest {
            service.findReleases("20260510", null, null, null)
        }
        assertInvalidRequest {
            service.findReleases("2026-05-10", null, null, listOf("unknown"))
        }
    }

    @Test
    fun `game detail returns not found for missing game`() {
        val service = GameDetailService(FakeRepository())

        val exception = assertFailsWith<ResponseStatusException> {
            service.findGame(999L)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    private fun assertInvalidRequest(block: () -> Unit) {
        val exception = assertFailsWith<ResponseStatusException>(block = block)
        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    private class FakeRepository(
        private val releases: List<ReleaseItemResponse> = emptyList(),
        private val game: GameDetailResponse? = null,
    ) : ServiceApiRepository {
        var startInclusive: Instant? = null
        var endExclusive: Instant? = null
        var platformIds: Set<Long> = emptySet()

        override fun findReleases(
            startInclusive: Instant,
            endExclusive: Instant,
            platformIds: Set<Long>,
        ): List<ReleaseItemResponse> {
            this.startInclusive = startInclusive
            this.endExclusive = endExclusive
            this.platformIds = platformIds
            return releases
        }

        override fun findGame(gameId: Long): GameDetailResponse? = game
    }

    private companion object {
        fun releaseItem(releaseId: Long) = ReleaseItemResponse(
            releaseIds = listOf(releaseId),
            gameId = 10L,
            date = "2026-05-10",
            title = "Game",
            defaultTitle = "Game",
            gameType = null,
            region = null,
            releaseStatus = null,
            platforms = emptyList(),
            coverThumbnailUrl = null,
            koreanLanguageSupport = null,
        )
    }
}
