package com.projectgc.batch.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

/**
 * service ETL 쓰기 트랜잭션 빈.
 * service 스키마 쓰기는 ETL이 유일하므로 전역 DataSource 토폴로지(com.projectgc.config)와 분리해 batch가 소유합니다.
 */
@Configuration
class ServiceEtlJdbcConfiguration {

    @Bean("serviceEtlTransactionManager")
    fun serviceEtlTransactionManager(
        @Suppress("SpringJavaInjectionPointsAutowiringInspection")
        @Qualifier("serviceDataSource")
        dataSource: DataSource,
    ): PlatformTransactionManager = DataSourceTransactionManager(dataSource)

    @Bean("serviceEtlTransactionTemplate")
    fun serviceEtlTransactionTemplate(
        @Suppress("SpringJavaInjectionPointsAutowiringInspection")
        @Qualifier("serviceEtlTransactionManager")
        transactionManager: PlatformTransactionManager,
    ) = TransactionTemplate(transactionManager)
}
