package com.projectgc.batch.repository.ingest

import com.projectgc.batch.model.entity.ingest.IngestCoverEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IngestCoverRepository : JpaRepository<IngestCoverEntity, Long>
