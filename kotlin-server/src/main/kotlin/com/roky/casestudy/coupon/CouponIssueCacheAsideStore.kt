package com.roky.casestudy.coupon

import com.roky.casestudy.store.StoreEntity
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@Component
class CouponIssueCacheAsideStore(
    private val redisTemplate: StringRedisTemplate,
    private val couponRedisCoordinator: CouponRedisCoordinator,
    private val couponStoreSnapshotLocalCache: CouponStoreSnapshotLocalCache,
    private val transactionManager: PlatformTransactionManager,
) {
    private val readOnlyTransactionTemplate =
        TransactionTemplate(transactionManager).apply {
            isReadOnly = true
        }

    fun getStoreSnapshotWithShortLock(
        storeId: UUID,
        loader: () -> StoreEntity,
    ): CachedStoreSnapshot =
        readLocalStoreSnapshot(storeId)
            ?: couponRedisCoordinator.loadWithShortLock(
                lockKey = couponRedisCoordinator.storeSnapshotLoadLockKey(storeId),
                readCached = { readStoreSnapshot(storeId)?.also(couponStoreSnapshotLocalCache::put) },
                loadAndCache = {
                    val store = loadStoreSnapshot(loader)
                    val snapshot = CachedStoreSnapshot(storeId = store.id, eventTotalCount = store.eventTotalCount)
                    redisTemplate
                        .opsForValue()
                        .set(storeCacheKey(storeId), snapshot.eventTotalCount.toString(), cacheTtlWithJitter())
                    couponStoreSnapshotLocalCache.put(snapshot)
                    snapshot
                },
            )

    /** 발급 유저 Set에 예약 마킹을 시도합니다. 이미 발급된 경우 false를 반환합니다. */
    fun reserveCouponIssue(
        storeId: UUID,
        userId: UUID,
    ): Boolean =
        redisTemplate.opsForSet().add(couponIssuedUsersKey(storeId), userId.toString()) == 1L

    fun markCouponIssued(
        storeId: UUID,
        userId: UUID,
    ) {
        redisTemplate.opsForSet().add(couponIssuedUsersKey(storeId), userId.toString())
    }

    fun unmarkCouponIssued(
        storeId: UUID,
        userId: UUID,
    ) {
        redisTemplate.opsForSet().remove(couponIssuedUsersKey(storeId), userId.toString())
    }

    private fun cacheTtlWithJitter(): Duration = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(600, 661))

    private fun loadStoreSnapshot(loader: () -> StoreEntity): StoreEntity =
        checkNotNull(readOnlyTransactionTemplate.execute { loader() })

    private fun readStoreSnapshot(storeId: UUID): CachedStoreSnapshot? {
        val cachedEventTotalCount = redisTemplate.opsForValue().get(storeCacheKey(storeId)) ?: return null
        return CachedStoreSnapshot(storeId = storeId, eventTotalCount = cachedEventTotalCount.toLong())
    }

    private fun readLocalStoreSnapshot(storeId: UUID): CachedStoreSnapshot? = couponStoreSnapshotLocalCache.get(storeId)

    private fun storeCacheKey(storeId: UUID): String = "coupon:v2:store:event-total-count:$storeId"

    private fun couponIssuedUsersKey(storeId: UUID): String = "coupon:v3:issued-users:$storeId"
}

data class CachedStoreSnapshot(
    val storeId: UUID,
    val eventTotalCount: Long,
)
