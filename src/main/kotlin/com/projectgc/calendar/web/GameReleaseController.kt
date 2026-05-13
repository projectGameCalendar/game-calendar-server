package com.projectgc.calendar.web

import com.projectgc.calendar.model.api.ReleasesResponse
import com.projectgc.calendar.service.GameReleaseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/releases")
class GameReleaseController(
    private val gameReleaseService: GameReleaseService,
) {

    @GetMapping
    fun releases(
        @RequestParam("date", required = false) date: String?,
        @RequestParam("from", required = false) from: String?,
        @RequestParam("to", required = false) to: String?,
        @RequestParam("platformGroup", required = false) platformGroups: List<String>?,
    ): ReleasesResponse = gameReleaseService.findReleases(
        date = date,
        from = from,
        to = to,
        platformGroupParams = platformGroups,
    )
}
