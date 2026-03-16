package com.roky.casestudy.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource

/**
 * Read/Write 분리를 위한 DataSource 라우팅 설정.
 * spring.datasource.source-only=true 설정 시 모든 쿼리가 write(source) DataSource로 라우팅된다.
 */
@Configuration
class DataSourceConfig {

    @Value("\${spring.datasource.source-only:false}")
    private var sourceOnly: Boolean = false

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.write")
    fun writeDataSource(): HikariDataSource =
        DataSourceBuilder.create().type(HikariDataSource::class.java).build().apply {
            poolName = "write-pool"
        }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.read")
    fun readDataSource(): HikariDataSource =
        DataSourceBuilder.create().type(HikariDataSource::class.java).build().apply {
            poolName = "read-pool"
            isReadOnly = true
        }

    @Bean
    fun routingDataSource(
        writeDataSource: HikariDataSource,
        readDataSource: HikariDataSource,
    ): DataSource {
        val routing = ReadWriteRoutingDataSource(sourceOnly)
        routing.setTargetDataSources(
            mapOf<Any, Any>(
                DataSourceType.WRITE to writeDataSource,
                DataSourceType.READ to readDataSource,
            ),
        )
        routing.setDefaultTargetDataSource(writeDataSource)
        return routing
    }

    /** 실제 커넥션 획득을 트랜잭션 시작 시점까지 지연시켜 정확한 readOnly 판별을 보장한다. */
    @Bean
    @Primary
    fun dataSource(routingDataSource: DataSource): DataSource =
        LazyConnectionDataSourceProxy(routingDataSource)
}

enum class DataSourceType {
    WRITE,
    READ,
}

/**
 * 현재 트랜잭션의 readOnly 여부에 따라 write/read DataSource를 결정한다.
 * @param sourceOnly true이면 readOnly 트랜잭션도 write(source)로 라우팅한다.
 */
class ReadWriteRoutingDataSource(
    private val sourceOnly: Boolean,
) : AbstractRoutingDataSource() {
    override fun determineCurrentLookupKey(): DataSourceType =
        if (sourceOnly || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            DataSourceType.WRITE
        } else {
            DataSourceType.READ
        }
}
