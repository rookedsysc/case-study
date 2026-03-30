package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.exception.CouponCacheLoadLockTimeoutException
import com.roky.casestudy.coupon.exception.CouponIssueInProgressException
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class CouponRedisCoordinator(
    private val redissonClient: RedissonClient,
    private val stringRedisTemplate: StringRedisTemplate,
    private val couponSoldOutLocalCache: CouponSoldOutLocalCache,
) {
    private val decreaseStockScript: String =
        ClassPathResource("scripts/decrease-stock.lua").inputStream.bufferedReader().readText()
    private val cacheLoadLockWaitTimeout = Duration.ofSeconds(2)
    private val userLockWaitTimeout = Duration.ofSeconds(2)

    /**
     * 유저 단위 락을 획득한 뒤 액션을 실행하고 반드시 락을 해제합니다.
     *
     * [unlock() 실패 시 예외]
     * Redisson의 RLock.unlock()은 현재 스레드가 락을 보유하지 않으면 IllegalMonitorStateException을 던집니다.
     * 가장 흔한 원인은 userLockTtl 초과로 Redis 키가 만료된 경우입니다.
     * Ref: https://github.com/redisson/redisson/wiki/8.-distributed-locks-and-synchronizers
     *
     * [finally에서 unlock() 예외가 발생할 때 action() 예외가 사라지는 문제]
     * JVM은 finally 블록에서 새로운 예외가 던져지면 try/catch에서 전파 중이던 예외를 덮어씁니다.
     * catch에서 throw e로 재던져도 finally가 먼저 실행되므로 동일한 문제가 발생합니다.
     * 이를 방지하기 위해 runCatching으로 unlock() 예외가 finally 밖으로 탈출하지 못하게 막고,
     * action() 예외 객체에 addSuppressed로 첨부하여 두 원인을 모두 보존합니다.
     * action()이 성공한 경우(actionException == null)에는 unlock() 예외를 직접 던집니다.
     * Ref: https://docs.oracle.com/javase/8/docs/api/java/lang/Throwable.html#addSuppressed-java.lang.Throwable-
     */
    fun <T> withUserLock(
        userId: UUID,
        action: () -> T,
    ): T {
        val lock = redissonClient.getLock(userLockKey(userId))
        val acquired = lock.tryLock(userLockWaitTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!acquired) {
            throw CouponIssueInProgressException(userId)
        }
        var actionException: Throwable? = null
        return try {
            action()
        } catch (e: Throwable) {
            actionException = e
            throw e
        } finally {
            runCatching {
                if (lock.isHeldByCurrentThread) {
                    lock.unlock()
                }
            }
                .onFailure { unlockEx ->
                    actionException?.addSuppressed(unlockEx) ?: throw unlockEx
                }
        }
    }

    /**
     * 캐시 미스 시 짧은 락으로 단일 스레드만 로더를 실행하도록 보호합니다.
     * 락 획득 타임아웃이 발생하면 캐시를 한 번 더 읽고, 여전히 없으면 예외를 발생시킵니다.
     */
    fun <T> loadWithShortLock(
        lockKey: String,
        readCached: () -> T?,
        loadAndCache: () -> T,
    ): T {
        readCached()?.let { return it }

        val lock = redissonClient.getLock(lockKey)
        val acquired = lock.tryLock(cacheLoadLockWaitTimeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!acquired) {
            return readCached() ?: throw CouponCacheLoadLockTimeoutException(lockKey)
        }

        return try {
            readCached() ?: loadAndCache()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    /** Lua 스크립트로 재고를 원자적으로 감소시킵니다. 재고가 0이면 false를 반환합니다. */
    fun decreaseRemainingCoupon(
        storeId: UUID,
        eventTotalCount: Long,
        issuedCountLoader: () -> Long,
    ): Boolean {
        if (couponSoldOutLocalCache.isSoldOut(storeId)) {
            return false
        }

        val result = decreaseStock(storeId)
        if (result != STOCK_KEY_MISSING_RESULT) {
            return applyStockDecreaseResultToLocalCache(storeId, result)
        }

        initializeRemainingStockIfAbsent(storeId, eventTotalCount, issuedCountLoader)
        return applyStockDecreaseResultToLocalCache(storeId, decreaseStock(storeId))
    }

    private fun applyStockDecreaseResultToLocalCache(
        storeId: UUID,
        result: Long,
    ): Boolean {
        if (result == STOCK_DECREASED_RESULT) {
            couponSoldOutLocalCache.evict(storeId)
            return true
        }

        couponSoldOutLocalCache.markSoldOut(storeId)
        return false
    }

    private fun decreaseStock(storeId: UUID): Long =
        redissonClient.script.eval<Long>(
            RScript.Mode.READ_WRITE,
            decreaseStockScript,
            RScript.ReturnType.INTEGER,
            listOf(stockKey(storeId)),
        )

    // 이건 readOnly 걸면 Replica Lag 발생할 수 있음
    private fun initializeRemainingStockIfAbsent(
        storeId: UUID,
        eventTotalCount: Long,
        issuedCountLoader: () -> Long,
    ) {
        val stock = redissonClient.getAtomicLong(stockKey(storeId))
        loadWithShortLock(
            lockKey = stockInitializationLockKey(storeId),
            readCached = { if (stock.isExists) true else null },
            loadAndCache = {
                val issuedCount = issuedCountLoader()
                stock.set((eventTotalCount - issuedCount).coerceAtLeast(0L))
                true
            },
        )
    }

    /** dedup 키가 존재하는지 읽기 전용으로 확인합니다. 발급 요청이 진행 중이면 true를 반환합니다. */
    fun isDedupKeyPresent(
        storeId: UUID,
        userId: UUID,
    ): Boolean =
        stringRedisTemplate.hasKey(dedupKey(storeId, userId))

    /** SETNX 기반 따닥 방지. TTL 내 동일 요청이면 false를 반환합니다. */
    fun tryAcquireDedup(
        storeId: UUID,
        userId: UUID,
    ): Boolean =
        stringRedisTemplate.opsForValue()
            .setIfAbsent(dedupKey(storeId, userId), "1", DEDUP_TTL) == true

    companion object {
        private val DEDUP_TTL: Duration = Duration.ofSeconds(2)
        private const val STOCK_KEY_MISSING_RESULT = -1L
        private const val STOCK_DECREASED_RESULT = 1L
    }

    fun rollbackStock(storeId: UUID) {
        couponSoldOutLocalCache.evict(storeId)
        redissonClient.getAtomicLong(stockKey(storeId)).incrementAndGet()
    }

    fun storeSnapshotLoadLockKey(storeId: UUID): String = "coupon:lock:store-snapshot:$storeId"

    fun stockInitializationLockKey(storeId: UUID): String = "coupon:lock:stock-init:$storeId"

    private fun userLockKey(userId: UUID): String = "coupon:lock:user:$userId"

    private fun stockKey(storeId: UUID): String = "coupon:stock:remaining:$storeId"

    private fun dedupKey(
        storeId: UUID,
        userId: UUID,
    ): String = "coupon:v5:dedup:$storeId:$userId"
}
