package com.roky.casestudy.coupon

import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class CouponRedisCoordinator(
    private val redisTemplate: StringRedisTemplate,
) {
    private val userLockRetryInterval = Duration.ofMillis(50)
    private val userLockWaitTimeout = Duration.ofSeconds(2)
    private val userLockTtl = Duration.ofSeconds(5)

    private val lockReleaseScript =
        DefaultRedisScript<Long>().apply {
            setLocation(ClassPathResource("lua/coupon-lock-release.lua"))
            resultType = Long::class.java
        }

    private val stockDecreaseScript =
        DefaultRedisScript<Long>().apply {
            setLocation(ClassPathResource("lua/coupon-stock-decrease.lua"))
            resultType = Long::class.java
        }

    fun tryAcquireUserLock(userId: UUID): String? {
        val deadlineNanos = System.nanoTime() + userLockWaitTimeout.toNanos()
        while (System.nanoTime() <= deadlineNanos) {
            val lockValue = UUID.randomUUID().toString()
            val acquired = redisTemplate.opsForValue().setIfAbsent(userLockKey(userId), lockValue, userLockTtl) == true
            if (acquired) {
                return lockValue
            }
            Thread.sleep(userLockRetryInterval.toMillis())
        }
        return null
    }

    fun releaseUserLock(
        userId: UUID,
        lockValue: String,
    ): Boolean {
        val result = redisTemplate.execute(lockReleaseScript, listOf(userLockKey(userId)), lockValue)
        return result == 1L
    }

    fun decreaseRemainingStock(
        storeId: UUID,
        eventTotalCount: Long,
        issuedCountLoader: () -> Long,
    ): Boolean {
        initializeRemainingStockIfAbsent(storeId, eventTotalCount, issuedCountLoader)
        val result =
            redisTemplate.execute(
                stockDecreaseScript,
                listOf(stockKey(storeId)),
            )
        return result == 1L
    }

    fun rollbackStock(storeId: UUID) {
        redisTemplate.opsForValue().increment(stockKey(storeId))
    }

    private fun userLockKey(userId: UUID): String = "coupon:lock:user:$userId"

    private fun stockKey(storeId: UUID): String = "coupon:stock:remaining:$storeId"

    private fun initializeRemainingStockIfAbsent(
        storeId: UUID,
        eventTotalCount: Long,
        issuedCountLoader: () -> Long,
    ) {
        if (redisTemplate.hasKey(stockKey(storeId)) == true) {
            return
        }

        val issuedCount = issuedCountLoader()
        val initialRemaining = (eventTotalCount - issuedCount).coerceAtLeast(0L)
        redisTemplate.opsForValue().setIfAbsent(stockKey(storeId), initialRemaining.toString())
    }
}
