package com.projectgc.batch.repository.ingest

import com.projectgc.batch.model.entity.ingest.IngestPlatformEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IngestPlatformRepository : JpaRepository<IngestPlatformEntity, Long>
