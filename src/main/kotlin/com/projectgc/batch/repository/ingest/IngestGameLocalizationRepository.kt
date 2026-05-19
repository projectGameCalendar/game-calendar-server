package com.projectgc.batch.repository.ingest

import com.projectgc.batch.model.entity.ingest.IngestGameLocalizationEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IngestGameLocalizationRepository : JpaRepository<IngestGameLocalizationEntity, Long>
