package com.projectgc.batch.repository.ingest

import com.projectgc.batch.model.entity.ingest.IngestArtworkEntity
import com.projectgc.batch.model.entity.ingest.IngestScreenshotEntity
import com.projectgc.batch.model.entity.ingest.IngestGameVideoEntity
import com.projectgc.batch.model.entity.ingest.IngestWebsiteEntity
import com.projectgc.batch.model.entity.ingest.IngestAlternativeNameEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IngestArtworkRepository : JpaRepository<IngestArtworkEntity, Long>
interface IngestScreenshotRepository : JpaRepository<IngestScreenshotEntity, Long>
interface IngestGameVideoRepository : JpaRepository<IngestGameVideoEntity, Long>
interface IngestWebsiteRepository : JpaRepository<IngestWebsiteEntity, Long>
interface IngestAlternativeNameRepository : JpaRepository<IngestAlternativeNameEntity, Long>
