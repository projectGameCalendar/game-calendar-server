package com.projectgc.config

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DataSourceConfigurationTest {

    private val configuration = DataSourceConfiguration()

    @Test
    fun `creates distinct datasource beans for ingest read and service write`() {
        val baseProperties = baseProperties()
        val overrides = DataSourceOverrideProperties()

        val defaultDataSource = configuration.dataSource(baseProperties) as HikariDataSource
        val ingestDataSource = configuration.ingestReadDataSource(baseProperties, overrides) as HikariDataSource
        val serviceDataSource = configuration.serviceDataSource(baseProperties, overrides) as HikariDataSource

        try {
            assertNotSame(defaultDataSource, ingestDataSource)
            assertNotSame(defaultDataSource, serviceDataSource)
            assertNotSame(ingestDataSource, serviceDataSource)
            assertSame(ingestDataSource, configuration.ingestReadJdbcTemplate(ingestDataSource).dataSource)
            assertSame(serviceDataSource, configuration.serviceJdbcTemplate(serviceDataSource).dataSource)
        } finally {
            defaultDataSource.close()
            ingestDataSource.close()
            serviceDataSource.close()
        }
    }

    @Test
    fun `limits ingest read pool to etl-only footprint`() {
        val ingestDataSource =
            configuration.ingestReadDataSource(baseProperties(), DataSourceOverrideProperties()) as HikariDataSource

        try {
            assertEquals(2, ingestDataSource.maximumPoolSize)
            assertEquals(0, ingestDataSource.minimumIdle)
        } finally {
            ingestDataSource.close()
        }
    }

    @Test
    fun `applies ingest and service datasource overrides independently`() {
        val overrides = DataSourceOverrideProperties(
            ingestRead = DataSourceOverride(
                url = "jdbc:postgresql://localhost:5432/ingest_read",
                username = "ingest_user",
            ),
            service = DataSourceOverride(
                url = "jdbc:postgresql://localhost:5432/service_write",
                username = "service_user",
            ),
        )

        val ingestDataSource = configuration.ingestReadDataSource(baseProperties(), overrides) as HikariDataSource
        val serviceDataSource = configuration.serviceDataSource(baseProperties(), overrides) as HikariDataSource

        try {
            assertEquals("jdbc:postgresql://localhost:5432/ingest_read", ingestDataSource.jdbcUrl)
            assertEquals("ingest_user", ingestDataSource.username)
            assertEquals("jdbc:postgresql://localhost:5432/service_write", serviceDataSource.jdbcUrl)
            assertEquals("service_user", serviceDataSource.username)
        } finally {
            ingestDataSource.close()
            serviceDataSource.close()
        }
    }

    private fun baseProperties() = DataSourceProperties().apply {
        url = "jdbc:postgresql://localhost:5432/personal"
        username = "postgres"
        password = "postgres"
        driverClassName = "org.postgresql.Driver"
    }
}
