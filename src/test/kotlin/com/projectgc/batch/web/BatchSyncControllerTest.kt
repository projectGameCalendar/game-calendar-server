package com.projectgc.batch.web

import com.projectgc.batch.service.GameReleaseBatchService
import com.projectgc.batch.service.etl.ServiceEtlCoordinator
import com.projectgc.batch.service.etl.ServiceEtlTrigger
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.core.task.TaskRejectedException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchSyncControllerTest {
    private val gameReleaseBatchService = mock(GameReleaseBatchService::class.java)
    private val serviceEtlCoordinator = mock(ServiceEtlCoordinator::class.java)

    private val controller = BatchSyncController(
        gameReleaseBatchService = gameReleaseBatchService,
        serviceEtlCoordinator = serviceEtlCoordinator,
    )

    @Test
    fun `returns accepted when ingest sync starts asynchronously`() {
        val response = controller.triggerSync()

        assertEquals(202, response.statusCode.value())
        verify(gameReleaseBatchService).syncAll()
    }

    @Test
    fun `returns conflict when ingest sync is already running`() {
        doThrow(TaskRejectedException("sync is already running"))
            .`when`(gameReleaseBatchService).syncAll()

        val response = controller.triggerSync()

        assertEquals(409, response.statusCode.value())
    }

    @Test
    fun `returns accepted when service etl starts asynchronously`() {
        val runId = UUID.randomUUID()
        `when`(serviceEtlCoordinator.triggerAsync(anyTrigger())).thenReturn(runId)

        val response = controller.triggerServiceSync()

        assertEquals(202, response.statusCode.value())
        assertTrue(response.body.orEmpty().contains("runId=$runId"))
        verify(serviceEtlCoordinator).triggerAsync(eqValue(ServiceEtlTrigger.manual()))
    }

    @Test
    fun `returns conflict when service etl is already running`() {
        `when`(serviceEtlCoordinator.triggerAsync(anyTrigger()))
            .thenThrow(TaskRejectedException("service ETL is already running"))

        val response = controller.triggerServiceSync()

        assertEquals(409, response.statusCode.value())
    }

    private fun anyTrigger(): ServiceEtlTrigger {
        org.mockito.Mockito.any(ServiceEtlTrigger::class.java)
        return ServiceEtlTrigger.manual()
    }

    private fun <T> eqValue(value: T): T {
        org.mockito.ArgumentMatchers.eq(value)
        return value
    }
}
