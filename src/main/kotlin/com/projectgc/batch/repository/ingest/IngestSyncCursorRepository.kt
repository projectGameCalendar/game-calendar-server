package com.projectgc.batch.repository.ingest

import com.projectgc.batch.model.entity.ingest.IngestSyncCursorEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IngestSyncCursorRepository : JpaRepository<IngestSyncCursorEntity, String>
