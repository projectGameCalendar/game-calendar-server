package com.projectgc.batch.service.etl

import java.util.UUID

interface ServiceEtlRunner {
    fun run(runId: UUID, trigger: ServiceEtlTrigger)
}
