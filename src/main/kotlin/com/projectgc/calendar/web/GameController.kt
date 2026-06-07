package com.projectgc.calendar.web

import com.projectgc.calendar.model.api.GameDetailResponse
import com.projectgc.calendar.service.GameDetailService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/games")
class GameController(
    private val gameDetailService: GameDetailService,
) {
    @GetMapping("/{gameId}")
    fun game(@PathVariable gameId: Long): GameDetailResponse =
        gameDetailService.findGame(gameId)
}
