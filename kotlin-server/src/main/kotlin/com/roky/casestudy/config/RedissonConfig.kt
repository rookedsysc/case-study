package com.roky.casestudy.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** application.yml 의 Redis 접속 정보를 읽어 Redisson 클라이언트를 구성합니다. */
@Configuration
class RedissonConfig(
    @Value("\${spring.data.redis.host}") private val host: String,
    @Value("\${spring.data.redis.port}") private val port: Int,
    @Value("\${spring.data.redis.password}") private val password: String,
) {
    @Bean(destroyMethod = "shutdown")
    fun redissonClient(): RedissonClient {
        // 1 core / 1 GB 환경 기준 적정 설정 (Redisson 기본값: threads=16, nettyThreads=32, pool=64, idle=24)
        val config =
            Config().apply {
                threads = 4
                nettyThreads = 4
                useSingleServer()
                    .setAddress("redis://$host:$port")
                    .setPassword(password)
                    .setConnectionPoolSize(16)
                    .setConnectionMinimumIdleSize(4)
            }
        return Redisson.create(config)
    }
}
