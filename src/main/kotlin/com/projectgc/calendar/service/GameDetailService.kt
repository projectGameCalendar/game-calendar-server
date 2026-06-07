package com.projectgc.calendar.service

import com.projectgc.calendar.model.api.GameDetailResponse
import com.projectgc.calendar.repository.api.ServiceApiRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class GameDetailService(
    private val repository: ServiceApiRepository,
) {
    fun findGame(gameId: Long): GameDetailResponse =
        repository.findGame(gameId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "game not found")
}
