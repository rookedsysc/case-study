package com.roky.casestudy.coupon

import com.roky.casestudy.coupon.exception.CouponCacheLoadLockTimeoutException
import com.roky.casestudy.coupon.exception.CouponIssueInProgressException
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

@Component
class CouponRedisCoordinator(
    private val redissonClient: RedissonClient,
) {
    private val cacheLoadLockWaitTimeout = Duration.ofMillis(300)
    private val cacheLoadLockTtl = Duration.ofSeconds(2)
    private val userLockWaitTimeout = Duration.ofSeconds(2)
    private val userLockTtl = Duration.ofSeconds(5)

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
        val acquired = lock.tryLock(userLockWaitTimeout.toMillis(), userLockTtl.toMillis(), TimeUnit.MILLISECONDS)
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
            runCatching { lock.unlock() }
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
        val acquired = lock.tryLock(cacheLoadLockWaitTimeout.toMillis(), cacheLoadLockTtl.toMillis(), TimeUnit.MILLISECONDS)
        if (!acquired) {
            return readCached() ?: throw CouponCacheLoadLockTimeoutException(lockKey)
        }

        return try {
            readCached() ?: loadAndCache()
        } finally {
            lock.unlock()
        }
    }

    /** RAtomicLong CAS 루프로 재고를 원자적으로 감소시킵니다. 재고가 0이면 false를 반환합니다. */
    fun decreaseRemainingStock(
        storeId: UUID,
        eventTotalCount: Long,
        issuedCountLoader: () -> Long,
    ): Boolean {
        initializeRemainingStockIfAbsent(storeId, eventTotalCount, issuedCountLoader)
        val stock = redissonClient.getAtomicLong(stockKey(storeId))
        var current = stock.get()
        while (current > 0) {
            if (stock.compareAndSet(current, current - 1)) return true
            current = stock.get()
        }
        return false
    }

    fun rollbackStock(storeId: UUID) {
        redissonClient.getAtomicLong(stockKey(storeId)).incrementAndGet()
    }

    fun storeSnapshotLoadLockKey(storeId: UUID): String = "coupon:lock:store-snapshot:$storeId"

    fun stockInitializationLockKey(storeId: UUID): String = "coupon:lock:stock-init:$storeId"

    private fun userLockKey(userId: UUID): String = "coupon:lock:user:$userId"

    private fun stockKey(storeId: UUID): String = "coupon:stock:remaining:$storeId"

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
}
