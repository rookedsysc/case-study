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
        // 0으로 설정하면 CPU 코어 수 기반 자동 설정 (기본값: threads=16, nettyThreads=32)
        // ref: https://github.com/redisson/redisson/wiki/2.-Configuration
        // ref: https://github.com/redisson/redisson/issues/5317
        val config =
            Config().apply {
                threads = 0
                nettyThreads = 0
                useSingleServer()
                    .setAddress("redis://$host:$port")
                    .setPassword(password)
                    .setConnectionPoolSize(16)
                    .setConnectionMinimumIdleSize(4)
            }
        return Redisson.create(config)
    }
}
