package com.projectgc.batch.job

import com.projectgc.batch.service.GameReleaseBatchService
import org.springframework.stereotype.Component

/**
 * 배치 스케줄에 따라 게임 출시 정보를 갱신하는 잡의 엔트리 포인트입니다.
 */
@Component
class GameReleaseSyncJob(
    private val gameReleaseBatchService: GameReleaseBatchService
) {
    fun runNightlySync() {
        // TODO 스케줄러에서 호출하도록 구성하고 배치 서비스 동작을 연결합니다.
    }

    fun runManualSync() {
        // TODO 운영자가 수동 실행할 때 사용할 엔드포인트를 연동합니다.
    }
}
