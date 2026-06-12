package com.projectgc.batch.config

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import kotlin.test.assertSame

class ServiceEtlJdbcConfigurationTest {

    private val configuration = ServiceEtlJdbcConfiguration()

    @Test
    fun `binds etl transaction beans to the service datasource`() {
        val serviceDataSource = HikariDataSource().apply {
            jdbcUrl = "jdbc:postgresql://localhost:5432/personal"
            username = "postgres"
            password = "postgres"
            driverClassName = "org.postgresql.Driver"
        }

        try {
            val transactionManager =
                configuration.serviceEtlTransactionManager(serviceDataSource) as DataSourceTransactionManager
            assertSame(serviceDataSource, transactionManager.dataSource)
            assertSame(
                transactionManager,
                configuration.serviceEtlTransactionTemplate(transactionManager).transactionManager,
            )
        } finally {
            serviceDataSource.close()
        }
    }
}
