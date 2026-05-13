package com.projectgc.calendar.web

import com.projectgc.calendar.model.api.GameDetailResponse
import com.projectgc.calendar.model.api.ReleaseItemResponse
import com.projectgc.calendar.repository.api.ServiceApiRepository
import com.projectgc.calendar.service.GameDetailService
import com.projectgc.calendar.service.GameReleaseService
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class ServiceApiControllerTest {

    @Test
    fun `GET releases returns date wrapper`() {
        val mockMvc = mockMvc(FakeRepository(releases = listOf(releaseItem(1L))))

        mockMvc.get("/api/releases") {
            param("date", "2026-05-10")
        }.andExpect {
            status { isOk() }
            jsonPath("$.date") { value("2026-05-10") }
            jsonPath("$.count") { value(1) }
            jsonPath("$.releases[0].releaseIds[0]") { value(1) }
        }
    }

    @Test
    fun `GET releases invalid query returns bad request`() {
        val mockMvc = mockMvc(FakeRepository())

        mockMvc.get("/api/releases") {
            param("from", "")
            param("to", "")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET game not found returns not found`() {
        val mockMvc = mockMvc(FakeRepository())

        mockMvc.get("/api/games/999").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET game id type mismatch returns bad request`() {
        val mockMvc = mockMvc(FakeRepository())

        mockMvc.get("/api/games/not-a-number").andExpect {
            status { isBadRequest() }
        }
    }

    private fun mockMvc(repository: ServiceApiRepository) =
        MockMvcBuilders
            .standaloneSetup(
                GameReleaseController(GameReleaseService(repository)),
                GameController(GameDetailService(repository)),
            )
            .build()

    private class FakeRepository(
        private val releases: List<ReleaseItemResponse> = emptyList(),
        private val game: GameDetailResponse? = null,
    ) : ServiceApiRepository {
        override fun findReleases(
            startInclusive: Instant,
            endExclusive: Instant,
            platformIds: Set<Long>,
        ): List<ReleaseItemResponse> = releases

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
