package com.projectgc.config

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * 앱 전역 DataSource 토폴로지를 한 곳에서 정의합니다.
 *
 * - 기본(@Primary): JPA(스키마 validate)·ingest 적재 등 기본 경로
 * - service: service 스키마 — calendar API(읽기)와 batch ETL(쓰기)이 공유하는 두 모듈의 유일한 인터페이스
 * - ingestRead: batch ETL의 ingest 스키마 읽기 전용
 *
 * 세 풀은 기본적으로 같은 DB를 가리키며, `projectgc.datasource.*` 오버라이드로
 * 풀별 물리 분리(읽기 복제본 등) 여지를 남겨둔다.
 */
@Configuration
@EnableConfigurationProperties(DataSourceOverrideProperties::class)
class DataSourceConfiguration {

    @Bean
    @Primary
    fun dataSource(dataSourceProperties: DataSourceProperties): DataSource =
        buildDataSource(
            url = dataSourceProperties.determineUrl(),
            username = dataSourceProperties.determineUsername(),
            password = dataSourceProperties.determinePassword(),
            driverClassName = dataSourceProperties.determineDriverClassName(),
        )

    @Bean("ingestReadDataSource")
    fun ingestReadDataSource(
        dataSourceProperties: DataSourceProperties,
        overrideProperties: DataSourceOverrideProperties,
    ): DataSource {
        val override = overrideProperties.ingestRead
        return buildDataSource(
            url = override.url ?: dataSourceProperties.determineUrl(),
            username = override.username ?: dataSourceProperties.determineUsername(),
            password = override.password ?: dataSourceProperties.determinePassword(),
            driverClassName = override.driverClassName ?: dataSourceProperties.determineDriverClassName(),
        ).apply {
            // ETL 전용(주 2회 실행, 단일 스레드 순차 쿼리) — 평시 유휴 커넥션을 점유하지 않도록 제한
            maximumPoolSize = 2
            minimumIdle = 0
        }
    }

    @Bean("serviceDataSource")
    fun serviceDataSource(
        dataSourceProperties: DataSourceProperties,
        overrideProperties: DataSourceOverrideProperties,
    ): DataSource {
        val override = overrideProperties.service
        return buildDataSource(
            url = override.url ?: dataSourceProperties.determineUrl(),
            username = override.username ?: dataSourceProperties.determineUsername(),
            password = override.password ?: dataSourceProperties.determinePassword(),
            driverClassName = override.driverClassName ?: dataSourceProperties.determineDriverClassName(),
        )
    }

    @Bean("serviceJdbcTemplate")
    fun serviceJdbcTemplate(
        @Suppress("SpringJavaInjectionPointsAutowiringInspection")
        @Qualifier("serviceDataSource")
        dataSource: DataSource,
    ) =
        JdbcTemplate(dataSource)

    @Bean("ingestReadJdbcTemplate")
    fun ingestReadJdbcTemplate(
        @Suppress("SpringJavaInjectionPointsAutowiringInspection")
        @Qualifier("ingestReadDataSource")
        dataSource: DataSource,
    ) =
        JdbcTemplate(dataSource)

    @Bean
    @Primary
    fun jdbcTemplate(dataSource: DataSource) = JdbcTemplate(dataSource)

    @Bean("transactionManager")
    @Primary
    fun transactionManager(entityManagerFactory: EntityManagerFactory): PlatformTransactionManager =
        JpaTransactionManager(entityManagerFactory)

    private fun buildDataSource(
        url: String,
        username: String?,
        password: String?,
        driverClassName: String?,
    ): HikariDataSource = HikariDataSource().apply {
        jdbcUrl = url
        this.username = username
        this.password = password
        if (!driverClassName.isNullOrBlank()) {
            this.driverClassName = driverClassName
        }
    }
}

@ConfigurationProperties("projectgc.datasource")
data class DataSourceOverrideProperties(
    var ingestRead: DataSourceOverride = DataSourceOverride(),
    var service: DataSourceOverride = DataSourceOverride(),
)

data class DataSourceOverride(
    var url: String? = null,
    var username: String? = null,
    var password: String? = null,
    var driverClassName: String? = null,
)
