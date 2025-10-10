package com.projectgc.batch.service

import org.springframework.stereotype.Service

/**
 * 게임 출시 정보를 외부 소스에서 동기화하거나 배치 가공하는 로직을 담당할 예정입니다.
 */
@Service
class GameReleaseBatchService {
    fun syncUpcomingReleases() {
        // TODO 향후 외부 API 연동과 DB 반영 로직을 구현합니다.
    }

    fun purgeExpiredEntries() {
        // TODO 일정 기준 이후의 데이터 정리 로직을 구현합니다.
    }
}
