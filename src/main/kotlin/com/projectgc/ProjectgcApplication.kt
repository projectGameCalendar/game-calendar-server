package com.projectgc

import com.projectgc.batch.config.IgdbProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EntityScan(basePackages = ["com.projectgc.batch.model.entity.ingest"])
@EnableConfigurationProperties(IgdbProperties::class)
class ProjectgcApplication

fun main(args: Array<String>) {
	runApplication<ProjectgcApplication>(*args)
}
