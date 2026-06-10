package com.projectgc.batch.web

import com.projectgc.batch.service.GameReleaseBatchService
import com.projectgc.batch.service.etl.ServiceEtlCoordinator
import com.projectgc.batch.service.etl.ServiceEtlTrigger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.core.task.TaskRejectedException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/batch")
class BatchSyncController(
    private val gameReleaseBatchService: GameReleaseBatchService,
    private val serviceEtlCoordinator: ServiceEtlCoordinator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/sync")
    fun triggerSync(): ResponseEntity<String> {
        return try {
            log.info("수동 동기화 요청 수신")
            gameReleaseBatchService.syncAll()  // @Async — 즉시 반환
            ResponseEntity.accepted().body("동기화가 백그라운드에서 시작되었습니다. 로그를 확인하세요.")
        } catch (e: TaskRejectedException) {
            log.warn("동기화 이미 실행 중 — 중복 요청 거부")
            ResponseEntity.status(409).body("동기화가 이미 실행 중입니다.")
        }
    }

    @PostMapping("/service-sync")
    fun triggerServiceSync(): ResponseEntity<String> {
        return try {
            val runId = serviceEtlCoordinator.triggerAsync(ServiceEtlTrigger.manual())
            log.info("수동 service ETL 요청 수신 (runId=$runId)")
            ResponseEntity.accepted().body("service ETL이 백그라운드에서 시작되었습니다. runId=$runId")
        } catch (ex: TaskRejectedException) {
            log.warn("service ETL 이미 실행 중 - 중복 요청 거부")
            ResponseEntity.status(409).body("service ETL이 이미 실행 중입니다.")
        }
    }
}
