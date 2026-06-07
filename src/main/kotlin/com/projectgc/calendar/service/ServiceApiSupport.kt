package com.projectgc.calendar.service

import java.time.ZoneId

internal val SERVICE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

internal enum class PlatformGroup(val platformIds: Set<Long>) {
    PC(setOf(3L, 6L, 14L, 163L)),
    PLAYSTATION(setOf(7L, 8L, 9L, 38L, 46L, 48L, 165L, 167L, 390L)),
    NINTENDO(setOf(4L, 18L, 19L, 20L, 21L, 37L, 41L, 130L, 508L)),
    XBOX(setOf(11L, 12L, 49L, 169L)),
    MOBILE(setOf(34L, 39L)),
}
